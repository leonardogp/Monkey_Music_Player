package com.lg.monkeymusicplayer.ui

import com.lg.monkeymusicplayer.data.database.HistoryEntity
import com.lg.monkeymusicplayer.data.database.PlaylistWithCount
import com.lg.monkeymusicplayer.data.model.SmartPlaylist
import com.lg.monkeymusicplayer.data.model.Song

// ── CORRECCIÓN: LayoutMode eliminado ──
// El enum LayoutMode { LIST, GRID } y el campo layoutMode en LibraryUiState
// nunca fueron leídos por ningún composable. Dead code eliminado.
// Si se implementa vista de grilla en el futuro, añadir aquí con su lógica completa.

data class LibraryUiState(
    /** Estado de carga unificado — reemplaza isLoading/isScanning/scanProgress/scanTotal. */
    val loadState: LibraryLoadState = LibraryLoadState.Idle,
    /** True mientras el splash inicial está activo (primera carga desde DB). */
    val isInitialLoad: Boolean = true,
    val songs: List<Song> = emptyList(),
    val genres: Map<String, List<Song>> = emptyMap(),
    val artists: Map<String, List<Song>> = emptyMap(),
    val albums: Map<String, List<Song>> = emptyMap(),
    val folders: Map<String, List<Song>> = emptyMap(),
    val playlists: List<PlaylistWithCount> = emptyList(),
    val history: List<HistoryEntity> = emptyList(),
    val currentPlaylistSongs: List<Song> = emptyList(),
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.NAME,
    val playerState: PlayerState = PlayerState(),
    val smartPlaylists: List<SmartPlaylist> = emptyList()
)
