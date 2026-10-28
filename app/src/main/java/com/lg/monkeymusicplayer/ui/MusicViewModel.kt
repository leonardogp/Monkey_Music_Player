package com.lg.monkeymusicplayer.ui

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.lg.monkeymusicplayer.core.billing.BillingManager
import com.lg.monkeymusicplayer.core.cast.CastManager
import com.lg.monkeymusicplayer.core.feature.Feature
import com.lg.monkeymusicplayer.core.feature.FeatureGate
import com.lg.monkeymusicplayer.core.player.MusicPlayerManager
import com.lg.monkeymusicplayer.core.queue.QueueManager
import com.lg.monkeymusicplayer.core.result.Result
import com.lg.monkeymusicplayer.data.database.EqPresetEntity
import com.lg.monkeymusicplayer.data.database.HistoryEntity
import com.lg.monkeymusicplayer.data.database.PlaylistEntity
import com.lg.monkeymusicplayer.data.database.PlaylistWithCount
import com.lg.monkeymusicplayer.data.model.LyricLine
import com.lg.monkeymusicplayer.data.model.SmartPlaylist
import com.lg.monkeymusicplayer.data.model.Song
import com.lg.monkeymusicplayer.data.repository.BackupRepository
import com.lg.monkeymusicplayer.data.repository.CloudSyncRepository
import com.lg.monkeymusicplayer.data.repository.ExcludedFoldersRepository
import com.lg.monkeymusicplayer.data.repository.MusicRepository
import com.lg.monkeymusicplayer.data.repository.SmartRepository
import com.lg.monkeymusicplayer.data.repository.StatsRepository
import com.lg.monkeymusicplayer.domain.usecase.*
import com.lg.monkeymusicplayer.ui.theme.PrimaryOrange
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import androidx.media3.common.util.UnstableApi
import androidx.palette.graphics.Palette
import com.lg.monkeymusicplayer.core.lyrics.LrcLibService
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(UnstableApi::class)
@HiltViewModel
class MusicViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val repository: MusicRepository,
    private val playerManager: MusicPlayerManager,
    private val excludedFoldersRepository: ExcludedFoldersRepository,
    private val smartRepository: SmartRepository,
    val statsRepository: StatsRepository,
    val backupRepository: BackupRepository,
    private val cloudSyncRepository: CloudSyncRepository,
    private val getSongsUseCase: GetSongsUseCase,
    private val getSmartPlaylistsUseCase: GetSmartPlaylistsUseCase,
    private val getUserStatsUseCase: GetUserStatsUseCase,
    private val refreshMusicLibraryUseCase: RefreshMusicLibraryUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val updateSongTagsUseCase: UpdateSongTagsUseCase,
    private val playSongUseCase: PlaySongUseCase,
    val featureGate: FeatureGate,
    val billingManager: BillingManager,
    private val castManager: CastManager,
    val queueManager: QueueManager
) : ViewModel() {

    val context: Context get() = applicationContext

    private val _loadState = MutableStateFlow<LibraryLoadState>(LibraryLoadState.Idle)
    private val _isInitialLoad = MutableStateFlow(true)
    private val _searchQuery = MutableStateFlow("")
    private val _sortOrder = MutableStateFlow(SortOrder.NAME)
    private val _currentPlaylistSongs = MutableStateFlow<List<Song>>(emptyList())
    
    private val _accentColor = MutableStateFlow(PrimaryOrange)
    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()
    private val _sleepTimerMinutes = MutableStateFlow(0)
    private val _sleepTimerRemaining = MutableStateFlow(0L)
    private var sleepTimerJob: Job? = null

    val eqPresets: StateFlow<List<EqPresetEntity>> = repository.eqPresets
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _hasManageStoragePermission = MutableStateFlow(false)
    val hasManageStoragePermission: StateFlow<Boolean> = _hasManageStoragePermission.asStateFlow()

    private val _requestManageStorageEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requestManageStorageEvent: SharedFlow<Unit> = _requestManageStorageEvent.asSharedFlow()

    private val _requestEditSongEvent = MutableSharedFlow<Song>(extraBufferCapacity = 1)
    val requestEditSongEvent: SharedFlow<Song> = _requestEditSongEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            delay(500)
            refreshLibrary()
        }
        // Disparar loadLyrics automáticamente cada vez que cambia la canción
        viewModelScope.launch {
            playerManager.currentSong
                .distinctUntilChanged { old, new -> old?.id == new?.id }
                .collect { song ->
                    if (song != null) loadLyrics(song)
                    else _lyrics.value = emptyList()
                }
        }
    }

    fun requestEditSong(song: Song) {
        viewModelScope.launch { _requestEditSongEvent.emit(song) }
    }

    fun onManageStoragePermissionResult(granted: Boolean) {
        val wasGranted = _hasManageStoragePermission.value
        _hasManageStoragePermission.value = granted
        if (!wasGranted && granted) {
            refreshLibrary()
        }
    }

    fun requestManageStoragePermission() {
        viewModelScope.launch { _requestManageStorageEvent.emit(Unit) }
    }

    private val _tagUpdateResult = MutableSharedFlow<Result<Unit>>(extraBufferCapacity = 1)
    val tagUpdateResult: SharedFlow<Result<Unit>> = _tagUpdateResult.asSharedFlow()

    val currentSong: StateFlow<Song?> = playerManager.currentSong
    val searchQuery = _searchQuery.asStateFlow()
    val equalizerData = playerManager.equalizerData

    val excludedFolders: StateFlow<List<String>> = excludedFoldersRepository.excludedFolders

    private data class PlaybackCore(
        val currentSong: Song?,
        val isPlaying: Boolean,
        val isShuffleMode: Boolean,
        val repeatMode: Int,
        val currentPosition: Long,
        val duration: Long,
        val audioSessionId: Int
    )

    private data class PlaybackUiExtras(
        val accentColor: Color,
        val lyrics: List<LyricLine>,
        val sleepTimerMinutes: Int,
        val sleepTimerRemainingMillis: Long
    )

    private val playbackCoreFlow: Flow<PlaybackCore> = combine(
        playerManager.currentSong,
        playerManager.isPlaying,
        playerManager.isShuffleMode,
        playerManager.repeatMode,
        playerManager.currentPosition
    ) { song, playing, shuffle, repeat, position ->
        PlaybackCore(song, playing, shuffle, repeat, position, 0L, -1)
    }.combine(playerManager.duration) { core, dur ->
        core.copy(duration = dur)
    }.combine(playerManager.audioSessionId) { core, sessionId ->
        core.copy(audioSessionId = sessionId)
    }.flowOn(Dispatchers.Default)

    private val playbackUiExtrasFlow: Flow<PlaybackUiExtras> = combine(
        _accentColor,
        _lyrics,
        _sleepTimerMinutes,
        _sleepTimerRemaining
    ) { color, lyrics, timerMin, timerRem ->
        PlaybackUiExtras(color, lyrics, timerMin, timerRem)
    }.flowOn(Dispatchers.Default)

    private val mappedQueueFlow: Flow<List<Song>> = combine(
        playerManager.currentQueue,
        repository.favorites
    ) { queue, favorites ->
        queue.map { it.copy(isFavorite = favorites.contains(it.id)) }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val lastPlayedSongFlow: Flow<Song?> = combine(
        repository.history,
        repository.allSongsFlow
    ) { history, songs ->
        if (history.isEmpty()) return@combine null
        val songIndex = songs.associateBy { it.id }
        songIndex[history.first().songId]
    }.flowOn(Dispatchers.Default)

    private val playerStateFlow: Flow<PlayerState> = combine(
        playbackCoreFlow,
        playbackUiExtrasFlow,
        mappedQueueFlow,
        repository.favorites,
        lastPlayedSongFlow
    ) { core, extras, queue, favorites, lastPlayed ->
        val isFavorite = core.currentSong?.let { favorites.contains(it.id) } ?: false
        PlayerState(
            currentSong              = core.currentSong?.copy(isFavorite = isFavorite),
            lastPlayedSong           = lastPlayed,
            isPlaying                = core.isPlaying,
            isShuffleMode            = core.isShuffleMode,
            repeatMode               = core.repeatMode,
            currentPosition          = core.currentPosition,
            duration                 = core.duration,
            currentQueue             = queue,
            audioSessionId           = core.audioSessionId,
            accentColor              = extras.accentColor,
            lyrics                   = extras.lyrics,
            sleepTimerMinutes        = extras.sleepTimerMinutes,
            sleepTimerRemainingMillis = extras.sleepTimerRemainingMillis
        )
    }

    val playerState: StateFlow<PlayerState> = playerStateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayerState())

    val smartPlaylistsFlow: Flow<List<SmartPlaylist>> = smartRepository.smartPlaylists

    val allSongs: StateFlow<List<Song>> = combine(
        repository.allSongsFlow,
        _sortOrder,
        repository.favorites
    ) { songs, order, favorites ->
        val mappedSongs = songs.map { it.copy(isFavorite = favorites.contains(it.id)) }
        when (order) {
            SortOrder.NAME -> mappedSongs.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST -> mappedSongs.sortedBy { it.artist.lowercase() }
            SortOrder.ALBUM -> mappedSongs.sortedBy { it.album.lowercase() }
            SortOrder.DATE_ADDED -> mappedSongs
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val filteredSongs: StateFlow<List<Song>> = combine(
        allSongs,
        _searchQuery
    ) { songs, query ->
        if (query.isBlank()) songs
        else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ── Grouped UI States ───────────────────────────────────────────────────
    
    private data class LibraryContentState(
        val songs: List<Song>,
        val allSongs: List<Song>,
        val history: List<HistoryEntity>,
        val playlists: List<PlaylistWithCount>,
        val currentPlaylistSongs: List<Song>,
        val smartPlaylists: List<SmartPlaylist>
    )

    private val libraryContentFlow: Flow<LibraryContentState> = combine(
        filteredSongs,
        allSongs,
        repository.history,
        repository.playlistsWithCount,
        _currentPlaylistSongs
    ) { filtered, all, history, playlists, currentPlaylist ->
        LibraryContentState(
            songs = filtered,
            allSongs = all,
            history = history,
            playlists = playlists,
            currentPlaylistSongs = currentPlaylist,
            smartPlaylists = emptyList()
        )
    }.combine(smartPlaylistsFlow) { state, smart ->
        state.copy(smartPlaylists = smart)
    }.flowOn(Dispatchers.Default)

    private data class LibraryConfigState(
        val loadState: LibraryLoadState,
        val isInitialLoad: Boolean,
        val searchQuery: String,
        val sortOrder: SortOrder
    )

    private val libraryConfigFlow: Flow<LibraryConfigState> = combine(
        _loadState,
        _isInitialLoad,
        _searchQuery,
        _sortOrder
    ) { load, initial, query, order ->
        LibraryConfigState(load, initial, query, order)
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<LibraryUiState> = combine(
        libraryContentFlow,
        libraryConfigFlow,
        playerState
    ) { content, config, player ->
        LibraryUiState(
            loadState = config.loadState,
            isInitialLoad = config.isInitialLoad,
            songs = content.songs,
            genres = content.allSongs.groupBy { it.genre },
            artists = content.allSongs.groupBy { it.artist },
            albums = content.allSongs.groupBy { it.album },
            folders = content.allSongs.groupBy { it.folder },
            playlists = content.playlists,
            history = content.history,
            currentPlaylistSongs = content.currentPlaylistSongs,
            searchQuery = config.searchQuery,
            sortOrder = config.sortOrder,
            playerState = player,
            smartPlaylists = content.smartPlaylists
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState())

    // ── Library Actions ─────────────────────────────────────────────────────

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun scanMusic() = refreshLibrary()

    fun refreshLibrary() {
        viewModelScope.launch {
            _loadState.value = LibraryLoadState.Scanning(0, 0)
            refreshMusicLibraryUseCase()
            _loadState.value = LibraryLoadState.Idle
            _isInitialLoad.value = false
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            toggleFavoriteUseCase(song)
        }
    }

    // ── Excluded Folders ────────────────────────────────────────────────────

    fun addExcludedFolder(path: String) {
        excludedFoldersRepository.addFolder(path)
    }

    fun removeExcludedFolder(path: String) {
        excludedFoldersRepository.removeFolder(path)
    }

    // ── Playlist Actions ────────────────────────────────────────────────────

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
        }
    }

    fun addSongToPlaylist(playlistId: String, song: Song) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId.toLong(), song.id)
        }
    }

    fun addSongsToPlaylist(playlistId: String, songs: List<Song>) {
        viewModelScope.launch {
            repository.addSongsToPlaylist(playlistId.toLong(), songs)
        }
    }

    fun removeSongFromPlaylist(playlistId: String, song: Song) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId.toLong(), song.id)
        }
    }

    fun loadPlaylistSongs(playlistId: String) {
        viewModelScope.launch {
            val songs = repository.getSongsInPlaylist(playlistId.toLong())
            _currentPlaylistSongs.value = songs
        }
    }

    // ── Player Actions ──────────────────────────────────────────────────────

    fun playSong(song: Song, playlist: List<Song>) {
        viewModelScope.launch {
            playSongUseCase(song, playlist)
        }
    }

    fun addToQueue(song: Song) {
        playerManager.addToQueue(song)
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun playPause() {
        playerManager.togglePlayPause()
    }

    fun next() {
        playerManager.skipNext()
    }

    fun skipNext() = next()

    fun previous() {
        playerManager.skipPrevious()
    }

    fun skipPrevious() = previous()

    fun seekTo(position: Long) {
        playerManager.seekTo(position)
    }

    fun seekForward() {
        playerManager.seekForward()
    }

    fun seekBack() {
        playerManager.seekBack()
    }

    fun toggleShuffle() {
        playerManager.toggleShuffle()
    }

    fun cycleRepeatMode() {
        playerManager.cycleRepeatMode()
    }

    fun setShuffleMode(enabled: Boolean) {
        if (playerManager.isShuffleMode.value != enabled) {
            playerManager.toggleShuffle()
        }
    }

    fun setRepeatMode(mode: Int) {
        playerManager.setRepeatMode(mode)
    }

    fun setSleepTimer(minutes: Int) {
        _sleepTimerMinutes.value = minutes
        sleepTimerJob?.cancel()
        if (minutes > 0) {
            _sleepTimerRemaining.value = minutes * 60 * 1000L
            sleepTimerJob = viewModelScope.launch {
                while (_sleepTimerRemaining.value > 0) {
                    delay(1000)
                    _sleepTimerRemaining.value -= 1000
                }
                playerManager.pause()
                _sleepTimerMinutes.value = 0
            }
        } else {
            _sleepTimerRemaining.value = 0
        }
    }

    fun updateAccentColorFromArtwork(artworkPath: String?) {
        if (artworkPath == null) {
            _accentColor.value = PrimaryOrange
            return
        }

        viewModelScope.launch {
            val loader = ImageLoader(applicationContext)
            val request = ImageRequest.Builder(applicationContext)
                .data(File(artworkPath))
                .allowHardware(false)
                .build()

            val result = (loader.execute(request) as? SuccessResult)?.drawable
            if (result is BitmapDrawable) {
                val palette = withContext(Dispatchers.Default) {
                    Palette.from(result.bitmap).generate()
                }
                _accentColor.value = Color(palette.getVibrantColor(PrimaryOrange.hashCode()))
            }
        }
    }

    fun openEqualizer(context: Context) {
        val intent = android.content.Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, playerManager.audioSessionId.value)
            putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        }
    }

    // ── Equalizer Actions ───────────────────────────────────────────────────

    fun setEqualizerBand(band: Short, level: Short) {
        playerManager.setEqualizerBand(band, level)
    }

    fun saveEqPreset(name: String, levels: List<Float>) {
        viewModelScope.launch {
            repository.saveEqPreset(name, levels)
        }
    }

    fun deleteEqPreset(preset: EqPresetEntity) {
        viewModelScope.launch {
            repository.deleteEqPreset(preset)
        }
    }

    // ── Lyric Support ───────────────────────────────────────────────────────

    fun loadLyrics(song: Song) {
        viewModelScope.launch {
            _isLoadingLyrics.value = true
            delay(500)
            _lyrics.value = emptyList()
            try {
                val lrcContent = withContext(Dispatchers.IO) {
                    repository.fetchLyrics(song)
                }
                _lyrics.value = if (lrcContent != null) parseLrc(lrcContent) else emptyList()
            } catch (e: Exception) {
                timber.log.Timber.w(e, "loadLyrics failed for \u0027${song.title}\u0027")
                _lyrics.value = emptyList()
            } finally {
                _isLoadingLyrics.value = false
            }
        }
    }

    /**
     * Parsea contenido LRC a lista de [LyricLine] con timestamps en ms.
     * Formato: [mm:ss.xx] texto
     * Soporta múltiples timestamps por línea y líneas sin timestamp (plainLyrics).
     */
    private fun parseLrc(lrc: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val timeRegex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})](.*)"""  )
        var sharedTimeMs = 0L

        for (raw in lrc.lines()) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue

            // Ignorar tags de metadatos [ar:], [ti:], [al:], [by:], [offset:]
            if (Regex("""^\[[a-z]+:.*]$""", RegexOption.IGNORE_CASE).matches(trimmed)) continue

            val matches = timeRegex.findAll(trimmed)
            if (!matches.any()) {
                // Línea plana sin timestamp — usar tiempo compartido con la anterior
                lines.add(LyricLine(timeMs = sharedTimeMs, text = trimmed))
                continue
            }

            var lastText = ""
            for (match in matches) {
                val mm  = match.groupValues[1].toLongOrNull() ?: 0L
                val ss  = match.groupValues[2].toLongOrNull() ?: 0L
                val cs  = match.groupValues[3].let {
                    // Centésimas (2 dígitos) o milésimas (3 dígitos)
                    val v = it.toLongOrNull() ?: 0L
                    if (it.length == 2) v * 10L else v
                }
                val timeMs = mm * 60_000L + ss * 1_000L + cs
                val text = match.groupValues[4].trim()
                if (text.isNotEmpty()) {
                    lines.add(LyricLine(timeMs = timeMs, text = text))
                    sharedTimeMs = timeMs
                    lastText = text
                }
            }
        }

        return lines.sortedBy { it.timeMs }
    }

    // ── Tag Editing ──────────────────────────────────────────────────────────

    fun updateSongTags(song: Song, newTitle: String, newArtist: String, newAlbum: String, newGenre: String) {
        viewModelScope.launch {
            updateSongTagsUseCase(song, newTitle, newArtist, newAlbum, newGenre)
        }
    }

    // ── Queue Actions ────────────────────────────────────────────────────────

    fun createAndSwitchQueue(name: String) {
        if (queueManager.createQueue(name)) {
            queueManager.switchToQueue(name)
        }
    }

    fun deleteQueue(name: String) {
        queueManager.deleteQueue(name)
    }

    fun switchToQueue(name: String) {
        queueManager.switchToQueue(name)
        val songs = queueManager.getQueue(name)?.songs ?: emptyList()
        if (songs.isNotEmpty()) {
            playerManager.setPlaylist(songs)
        }
    }

    // ── Billing Actions ──────────────────────────────────────────────────────

    fun launchProUpgrade(activity: android.app.Activity) {
        billingManager.launchBillingFlow(activity)
    }

    // ── Backup / Cloud Sync ──────────────────────────────────────────────────

    suspend fun cloudSyncUpload(): Result<Uri> = cloudSyncRepository.upload()

    suspend fun cloudSyncDownload(): Result<Unit> = cloudSyncRepository.download()

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            // backupRepository.createBackup(uri)
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            // backupRepository.restoreBackup(uri)
        }
    }

    fun isFeatureUnlocked(feature: Feature): Boolean {
        return featureGate.isUnlocked(feature)
    }
}
