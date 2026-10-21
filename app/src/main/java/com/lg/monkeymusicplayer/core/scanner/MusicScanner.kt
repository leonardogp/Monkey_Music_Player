package com.lg.monkeymusicplayer.core.scanner

import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.lg.monkeymusicplayer.data.model.Song
import com.lg.monkeymusicplayer.util.PermissionHelper
import timber.log.Timber
import java.io.File

class MusicScanner(private val context: Context) {

    fun invalidateGenreCache() {
        genreCache = null
        genreCacheTimeMs = 0L
    }

    private var genreCache: Map<Long, String>? = null
    private var genreCacheTimeMs: Long = 0L
    private val GENRE_CACHE_TTL_MS = 5L * 60L * 1000L

    private val audioExtensions = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus")

    internal fun isPathExcluded(fullPath: String, excludedPaths: List<String>): Boolean {
        val set = excludedPaths.toSet()
        return set.isNotEmpty() && fullPath.isNotEmpty() && set.any { ex -> fullPath.startsWith(ex) }
    }

    suspend fun scan(
        excludedPaths: List<String> = emptyList(),
        existingIds: Set<Long> = emptySet(),
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onSongsFound: suspend (List<Song>) -> Unit = {}
    ): List<Song> {
        val songs = mutableListOf<Song>()
        val scannedPaths = mutableSetOf<String>()

        val genreMap = loadAllGenres()

        if (!PermissionHelper.hasReadPermissions(context)) {
            Timber.w("MusicScanner: scan abortado — permisos no concedidos")
            return songs
        }

        // 1. Trigger MediaScanner on storage roots so Android indexes new files
        val storageDirs = getStorageDirectories(context)
        storageDirs.forEach { dir ->
            try {
                MediaScannerConnection.scanFile(context, arrayOf(dir.absolutePath), null, null)
            } catch (e: Exception) {
                Timber.e(e, "Error triggering MediaScannerConnection for ${dir.absolutePath}")
            }
        }

        // 2. Query MediaStore
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DATA
        )

        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null, null
        )

        val excludedSet = excludedPaths.toSet()
        val artworkBase = Uri.parse("content://media/external/audio/albumart")
        val mediaBase   = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        cursor?.use { c ->
            val total = c.count
            Timber.d("MusicScanner: $total canciones en MediaStore")

            val colId      = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val colAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val colTitle   = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val colArtist  = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val colAlbum   = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val colData    = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            var current = 0
            val BATCH_SIZE = 150
            val batch = mutableListOf<Song>()

            while (c.moveToNext()) {
                current++
                val fullPath = c.getString(colData) ?: ""

                if (excludedSet.isNotEmpty() && fullPath.isNotEmpty() &&
                    excludedSet.any { ex -> fullPath.startsWith(ex) }) continue

                if (fullPath.isNotEmpty()) scannedPaths.add(fullPath)

                val id      = c.getLong(colId)
                val albumId = c.getLong(colAlbumId)

                batch.add(Song(
                    id          = id,
                    albumId     = albumId,
                    title       = c.getString(colTitle)  ?: "Desconocido",
                    artist      = c.getString(colArtist) ?: "Artista Desconocido",
                    album       = c.getString(colAlbum)  ?: "Álbum Desconocido",
                    genre       = genreMap[id] ?: "Sin género",
                    folder      = if (fullPath.isNotEmpty())
                                      File(fullPath).parentFile?.name ?: "Raíz"
                                  else "Desconocida",
                    path        = ContentUris.withAppendedId(mediaBase, id).toString(),
                    albumArtUri = ContentUris.withAppendedId(artworkBase, albumId).toString(),
                    replayGain  = null
                ))

                if (batch.size >= BATCH_SIZE) {
                    songs.addAll(batch)
                    onSongsFound(batch.toList())
                    batch.clear()
                }
            }

            if (batch.isNotEmpty()) {
                songs.addAll(batch)
                onSongsFound(batch.toList())
            }
        }

        // 3. Direct File System Supplement (Internal Storage + SD Card fallback for unindexed files)
        val directSongs = mutableListOf<Song>()
        storageDirs.forEach { dir ->
            scanDirectoryDirectly(dir, excludedSet, scannedPaths) { song ->
                directSongs.add(song)
                scannedPaths.add(song.path)
            }
        }

        if (directSongs.isNotEmpty()) {
            Timber.d("MusicScanner: ${directSongs.size} canciones adicionales encontradas por escaneo directo de archivos")
            songs.addAll(directSongs)
            onSongsFound(directSongs.toList())
        }

        onProgress(songs.size, songs.size)
        return songs
    }

    private fun getStorageDirectories(context: Context): List<File> {
        val dirs = mutableListOf<File>()
        try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.let { if (it.exists()) dirs.add(it) }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { if (it.exists()) dirs.add(it) }
            Environment.getExternalStorageDirectory()?.let { if (it.exists()) dirs.add(it) }

            ContextCompat.getExternalFilesDirs(context, null).forEach { file ->
                file?.let {
                    var root: File? = it
                    while (root != null && root.absolutePath != "/storage" && root.absolutePath != "/") {
                        val parent = root.parentFile
                        if (parent != null && parent.absolutePath == "/storage") {
                            dirs.add(root)
                            break
                        }
                        root = parent
                    }
                }
            }

            val storageDir = File("/storage")
            if (storageDir.exists() && storageDir.isDirectory) {
                storageDir.listFiles()?.forEach { volume ->
                    if (volume.isDirectory && volume.name != "emulated" && volume.name != "self") {
                        dirs.add(volume)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error detecting storage directories")
        }
        return dirs.distinctBy { it.absolutePath }
    }

    private fun scanDirectoryDirectly(
        dir: File,
        excludedSet: Set<String>,
        existingPaths: Set<String>,
        onSongFound: (Song) -> Unit
    ) {
        if (!dir.exists() || !dir.isDirectory) return
        val fullPath = dir.absolutePath
        if (excludedSet.isNotEmpty() && excludedSet.any { fullPath.startsWith(it) }) return

        try {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    scanDirectoryDirectly(file, excludedSet, existingPaths, onSongFound)
                } else if (file.isFile) {
                    val ext = file.extension.lowercase()
                    if (ext in audioExtensions) {
                        val filePath = file.absolutePath
                        if (excludedSet.isNotEmpty() && excludedSet.any { filePath.startsWith(it) }) return@forEach
                        if (filePath !in existingPaths) {
                            val title = file.nameWithoutExtension
                            val parentFolder = file.parentFile?.name ?: "Raíz"
                            val id = Math.abs(filePath.hashCode().toLong()) + 1000000L

                            onSongFound(Song(
                                id = id,
                                albumId = 0L,
                                title = title,
                                artist = "Desconocido",
                                album = parentFolder,
                                genre = "Sin género",
                                folder = parentFolder,
                                path = filePath,
                                albumArtUri = "",
                                duration = 0L,
                                dateAdded = file.lastModified()
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error scanning directory: ${dir.absolutePath}")
        }
    }

    private fun loadAllGenres(): Map<Long, String> {
        val now = System.currentTimeMillis()
        genreCache?.let {
            if (now - genreCacheTimeMs < GENRE_CACHE_TTL_MS) return it
        }
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            loadGenresApi29()
        else
            loadGenresLegacy()
        genreCache = result
        genreCacheTimeMs = now
        return result
    }

    private fun loadGenresApi29(): Map<Long, String> {
        val result = mutableMapOf<Long, String>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.GENRE),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null, null
        )?.use { c ->
            val colId    = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val colGenre = c.getColumnIndex(MediaStore.Audio.Media.GENRE)
            if (colGenre == -1) return loadGenresLegacy()
            while (c.moveToNext()) {
                val genre = c.getString(colGenre)
                if (!genre.isNullOrBlank()) result[c.getLong(colId)] = genre
            }
        }
        return result
    }

    private fun loadGenresLegacy(): Map<Long, String> {
        val result = mutableMapOf<Long, String>()
        context.contentResolver.query(
            MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
            null, null, null
        )?.use { gc ->
            val colId   = gc.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
            val colName = gc.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
            while (gc.moveToNext()) {
                val genreId   = gc.getLong(colId)
                val genreName = gc.getString(colName) ?: continue
                context.contentResolver.query(
                    MediaStore.Audio.Genres.Members.getContentUri("external", genreId),
                    arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                    null, null, null
                )?.use { sc ->
                    val colAudio = sc.getColumnIndexOrThrow(MediaStore.Audio.Genres.Members.AUDIO_ID)
                    while (sc.moveToNext()) {
                        val sid = sc.getLong(colAudio)
                        if (!result.containsKey(sid)) result[sid] = genreName
                    }
                }
            }
        }
        return result
    }
}
