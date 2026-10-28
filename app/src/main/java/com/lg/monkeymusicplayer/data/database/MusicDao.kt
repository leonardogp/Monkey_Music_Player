package com.lg.monkeymusicplayer.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Query("SELECT * FROM songs")
    fun getAllSongsFlow(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs")
    suspend fun getAllSongs(): List<SongEntity>

    @Query("SELECT id FROM songs")
    suspend fun getAllIds(): List<Long>

    // INSERT OR IGNORE: si la cancion ya existe en Room, no sobreescribir.
    // Protege el campo `genre` editado manualmente de ser pisado por el scanner.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongs(songs: List<SongEntity>)

    // Actualiza metadatos de canciones existentes SIN tocar genre.
    // Llamado por el scanner para canciones que ya estaban en Room.
    @Query("UPDATE songs SET title = :title, artist = :artist, album = :album, albumId = :albumId, folder = :folder, path = :path, albumArtUri = :albumArtUri WHERE id = :id")
    suspend fun updateSongMetadata(id: Long, title: String, artist: String, album: String, albumId: Long, folder: String, path: String, albumArtUri: String?)

    @Query("UPDATE songs SET title = :title, artist = :artist, album = :album, genre = :genre WHERE id = :id")
    suspend fun updateSongTags(id: Long, title: String, artist: String, album: String, genre: String)

    @Query("UPDATE songs SET replayGain = :gain WHERE id = :id")
    suspend fun updateReplayGain(id: Long, gain: Float)

    /**
     * OPT-3: Actualiza metadatos de múltiples canciones en una sola transacción.
     * Drásticamente más rápido que N llamadas individuales a updateSongMetadata:
     * cada llamada individual abre/cierra su propia transacción SQLite.
     * Con @Transaction todo el batch se ejecuta en una sola transacción.
     * NO toca el campo genre (preserva ediciones manuales del usuario).
     */
    @Transaction
    suspend fun updateSongMetadataBatch(songs: List<SongEntity>) {
        for (song in songs) {
            updateSongMetadata(
                id          = song.id,
                title       = song.title,
                artist      = song.artist,
                album       = song.album,
                albumId     = song.albumId,
                folder      = song.folder,
                path        = song.path,
                albumArtUri = song.albumArtUri
            )
        }
    }

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteSongsByIds(ids: List<Long>)

    // ── FIX: removeDeletedSongs ya no usa NOT IN (:currentIds) ──
    // SQLite tiene un límite de ~999 variables bind. Con bibliotecas de >999 canciones,
    // "NOT IN (:currentIds)" lanza SQLiteException: too many SQL variables.
    // La estrategia correcta es: obtener IDs actuales en Room → diff en Kotlin → borrar solo los
    // huérfanos con deleteSongsByIds(ids), que también usa IN pero con una lista pequeña
    // (solo los borrados, que normalmente son 0 o pocos).
    // Este método se mantiene como fallback para migraciones/tests, pero ya no se llama
    // desde MusicRepository en el flujo normal de escaneo.
    @Query("DELETE FROM songs WHERE id NOT IN (:currentIds)")
    suspend fun removeDeletedSongsUnsafe(currentIds: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Delete
    suspend fun deleteFavorite(favorite: FavoriteEntity)

    @Query("SELECT songId FROM favorites")
    fun getFavorites(): Flow<List<Long>>

    @Insert
    suspend fun createPlaylist(playlist: PlaylistEntity): Long

    @Query("SELECT * FROM playlists")
    fun getPlaylists(): Flow<List<PlaylistEntity>>

    @Query("""
        SELECT p.id AS id,
               p.name AS name,
               COUNT(ps.songId) AS songCount
        FROM playlists p
        LEFT JOIN playlist_songs ps
               ON p.id = ps.playlistId
        GROUP BY p.id, p.name
    """)
    fun getPlaylistsWithCount(): Flow<List<PlaylistWithCount>>

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun deleteSongsFromPlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongsToPlaylist(crossRefs: List<PlaylistSongCrossRef>)

    @Delete
    suspend fun removeSongFromPlaylist(crossRef: PlaylistSongCrossRef)

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getSongsInPlaylist(playlistId: Long): List<Long>

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getSongsByIds(ids: List<Long>): List<SongEntity>

    @Insert
    suspend fun addToHistory(history: HistoryEntity)

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY timestamp DESC LIMIT 50)")
    suspend fun trimHistory()

    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT 50")
    fun getHistory(): Flow<List<HistoryEntity>>

    // ── Backup / Restore ──────────────────────────────────────────────────────

    @Query("SELECT songId FROM favorites")
    suspend fun getAllFavoriteIds(): List<Long>

    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_songs")
    suspend fun getAllPlaylistSongs(): List<PlaylistSongCrossRef>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistSongs(crossRefs: List<PlaylistSongCrossRef>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavorites(favorites: List<FavoriteEntity>)

    // ── EQ Presets ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEqPreset(preset: EqPresetEntity): Long

    @Delete
    suspend fun deleteEqPreset(preset: EqPresetEntity)

    @Query("SELECT * FROM eq_presets ORDER BY name ASC")
    fun getEqPresets(): Flow<List<EqPresetEntity>>

    @Query("SELECT * FROM eq_presets ORDER BY name ASC")
    suspend fun getEqPresetsSnapshot(): List<EqPresetEntity>
}
