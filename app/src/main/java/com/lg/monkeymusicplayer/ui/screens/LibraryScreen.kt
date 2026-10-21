package com.lg.monkeymusicplayer.ui.screens

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavController
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import com.lg.monkeymusicplayer.R
import com.lg.monkeymusicplayer.data.database.PlaylistEntity
import com.lg.monkeymusicplayer.data.model.Song
import com.lg.monkeymusicplayer.ui.SortOrder
import com.lg.monkeymusicplayer.ui.PlayerState
import com.lg.monkeymusicplayer.ui.LibraryUiState
import com.lg.monkeymusicplayer.ui.MusicViewModel
import com.lg.monkeymusicplayer.core.result.Result
import com.lg.monkeymusicplayer.ui.components.core.MonkeyPlayerBottomBar
import com.lg.monkeymusicplayer.ui.components.MediaProgressSlider
import com.lg.monkeymusicplayer.ui.theme.PrimaryOrange
import kotlinx.coroutines.launch
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.lg.monkeymusicplayer.ui.components.LyricsView
import com.lg.monkeymusicplayer.data.model.SmartPlaylist
import com.lg.monkeymusicplayer.data.model.SmartPlaylistType
import android.net.Uri
import com.lg.monkeymusicplayer.ui.components.core.MonkeySearchBar
import androidx.compose.animation.core.animateFloatAsState
import com.lg.monkeymusicplayer.ui.components.player.FullPlayerScreen
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import com.lg.monkeymusicplayer.ui.components.core.PlaybackWaveform
import com.lg.monkeymusicplayer.ui.components.dialogs.EditTagsDialog
import com.lg.monkeymusicplayer.ui.components.dialogs.PlaylistPickerDialog
import com.lg.monkeymusicplayer.ui.components.dialogs.SongMenuSheet
import com.lg.monkeymusicplayer.ui.components.home.HomeContent
import com.lg.monkeymusicplayer.ui.components.library.AlbumGrid
import com.lg.monkeymusicplayer.ui.components.library.ArtistList
import com.lg.monkeymusicplayer.ui.components.library.FolderList
import com.lg.monkeymusicplayer.ui.components.library.GenreList
import com.lg.monkeymusicplayer.ui.components.library.PlaylistGrid
import com.lg.monkeymusicplayer.ui.components.library.SongList
import com.lg.monkeymusicplayer.ui.screens.library.ExcludedFoldersScreen
import com.lg.monkeymusicplayer.ui.screens.library.SongListDetailScreen
import com.lg.monkeymusicplayer.ui.theme.dynamic.LocalDynamicPlayerColors

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    windowSizeClass: androidx.compose.material3.windowsizeclass.WindowSizeClass
 ) {
    val uiState by viewModel.uiState.collectAsState()
    val navController = rememberNavController()
    val context = LocalContext.current
    var songForMenu by remember { mutableStateOf<Song?>(null) }
    var showSongMenu by remember { mutableStateOf(false) }
    var songToEdit by remember { mutableStateOf<Song?>(null) }
    var songForPlaylist by remember { mutableStateOf<Song?>(null) }

    // Escuchar eventos de edición solicitados desde el ViewModel
    LaunchedEffect(Unit) {
        viewModel.requestEditSongEvent.collect { song ->
            songToEdit = song
        }
    }

    // Mostrar feedback de actualización de tags
    LaunchedEffect(Unit) {
        viewModel.tagUpdateResult.collect { result ->
            when (result) {
                is Result.Success -> Toast.makeText(context, context.getString(R.string.tags_saved_ok), Toast.LENGTH_SHORT).show()
                is Result.Error -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                else -> {}
            }
        }
    }

    if (showSongMenu && songForMenu != null) {
        SongMenuSheet(
            song = songForMenu!!,
            onDismiss = { showSongMenu = false },
            onPlayNext = { 
                viewModel.addToQueue(it)
                Toast.makeText(context, context.getString(R.string.add_to_queue), Toast.LENGTH_SHORT).show()
                showSongMenu = false
            },
            onAddToPlaylist = { 
                songForPlaylist = it
                showSongMenu = false
            },
            onEditTags = { 
                songToEdit = it
                showSongMenu = false
            },
            onToggleFavorite = {
                viewModel.toggleFavorite(it)
                showSongMenu = false
            }
        )
    }

    if (songToEdit != null) {
        EditTagsDialog(
            song = songToEdit!!,
            isSaving = false,
            onDismiss = { songToEdit = null },
            onSave = { title, artist, album, genre ->
                viewModel.updateSongTags(songToEdit!!, title, artist, album, genre)
                songToEdit = null
            }
        )
    }

    if (songForPlaylist != null) {
        PlaylistPickerDialog(
            playlists = uiState.playlists,
            onDismiss = { songForPlaylist = null },
            onPlaylistSelected = { playlist ->
                viewModel.addSongToPlaylist(playlist.id.toString(), songForPlaylist!!)
                Toast.makeText(context, "${context.getString(R.string.add_to_playlist)}: ${playlist.name}", Toast.LENGTH_SHORT).show()
                songForPlaylist = null
            },
            onCreatePlaylist = { name -> viewModel.createPlaylist(name) }
        )
    }

    NavHost(navController = navController, startDestination = "library") {
        composable("library") {
            LibraryMainContent(
                uiState = uiState,
                viewModel = viewModel,
                navController = navController,
                onSearchQueryChanged = viewModel::onSearchQueryChanged,
                onSortOrderChanged = viewModel::setSortOrder,
                onPlayPause = viewModel::togglePlayPause,
                onPlay = { song, playlist -> viewModel.playSong(song, playlist) },
                onAddToQueue = viewModel::addToQueue,
                onScanMusic = { viewModel.scanMusic() },
                onSkipNext = viewModel::skipNext,
                onSkipPrevious = viewModel::skipPrevious,
                onSeekTo = viewModel::seekTo,
                onSeekForward = viewModel::seekForward,
                onSeekBack = viewModel::seekBack,
                onToggleShuffle = viewModel::toggleShuffle,
                onCycleRepeatMode = viewModel::cycleRepeatMode,
                onToggleFavorite = viewModel::toggleFavorite,
                onCreatePlaylist = viewModel::createPlaylist,
                onDeletePlaylist = viewModel::deletePlaylist,
                onAddSongToPlaylist = viewModel::addSongToPlaylist,
                onAddSongsToPlaylist = viewModel::addSongsToPlaylist,
                onRemoveSongFromPlaylist = { playlistId, songId -> viewModel.removeSongFromPlaylist(playlistId, songId)},
                onLoadPlaylistSongs = viewModel::loadPlaylistSongs,
                onUpdateSongTags = { song, t, a, al, g -> viewModel.updateSongTags(song, t, a, al, g) },
                onOpenEqualizer = { navController.navigate("equalizer") },
                onSetSleepTimer = viewModel::setSleepTimer,
                onChangeLanguage = { lang ->
                    val appLocale: LocaleListCompat = if (lang.isEmpty()) {
                        LocaleListCompat.getEmptyLocaleList()
                    } else {
                        LocaleListCompat.forLanguageTags(lang)
                    }
                    AppCompatDelegate.setApplicationLocales(appLocale)
                },
                onPlayerClick = { navController.navigate("player") },
                onMenuClick = { navController.navigate("settings") },
                onGenreClick = { genre -> navController.navigate("detail/genre/${Uri.encode(genre)}") },
                onArtistClick = { artist -> navController.navigate("detail/artist/${Uri.encode(artist)}") },
                onAlbumClick = { album -> navController.navigate("detail/album/${Uri.encode(album)}") },
                onFolderClick = { folder -> navController.navigate("detail/folder/${Uri.encode(folder)}") },
                onPlaylistClick = { playlist -> 
                    viewModel.loadPlaylistSongs(playlist.id.toString())
                    navController.navigate("playlist_detail/${playlist.id}/${Uri.encode(playlist.name)}")
                },
                onSmartPlaylistClick = { smart ->
                    navController.navigate("smart_playlist_detail/${smart.type.name}")
                },
                onSongMoreClick = { song ->
                    songForMenu = song
                    showSongMenu = true
                }
            )
        }
        composable("detail/{type}/{key}") { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: ""
            val key = backStackEntry.arguments?.getString("key") ?: ""
            val songs = when (type) {
                "genre" -> uiState.genres[key]
                "artist" -> uiState.artists[key]
                "album" -> uiState.albums[key]
                "folder" -> uiState.folders[key]
                else -> emptyList()
            } ?: emptyList()
            
            SongListDetailScreen(
                title = if (type == "folder") key.substringAfterLast("/") else key,
                songs = songs,
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onPlaySong = { song, list -> viewModel.playSong(song, list) },
                onMoreClick = { song ->
                    songForMenu = song
                    showSongMenu = true
                }
            )
        }
        composable("playlist_detail/{id}/{name}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: ""
            val name = backStackEntry.arguments?.getString("name") ?: ""
            
            SongListDetailScreen(
                title = name,
                songs = uiState.currentPlaylistSongs,
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onPlaySong = { song, list -> viewModel.playSong(song, list) },
                onMoreClick = { song ->
                    songForMenu = song
                    showSongMenu = true
                }
            )
        }
        composable("smart_playlist_detail/{type}") { backStackEntry ->
            val typeStr = backStackEntry.arguments?.getString("type") ?: ""
            val type = try { SmartPlaylistType.valueOf(typeStr) } catch(e: Exception) { SmartPlaylistType.DAILY_MIX }
            val smart = uiState.smartPlaylists.find { it.type == type }
            val title = when (type) {
                SmartPlaylistType.DAILY_MIX -> stringResource(R.string.smart_daily_mix_title)
                SmartPlaylistType.REDISCOVER -> stringResource(R.string.smart_rediscover_title)
                SmartPlaylistType.TOP_SONGS -> stringResource(R.string.smart_top_songs_title)
            }
            
            SongListDetailScreen(
                title = title,
                songs = smart?.songs ?: emptyList(),
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onPlaySong = { song, list -> viewModel.playSong(song, list) },
                onMoreClick = { song ->
                    songForMenu = song
                    showSongMenu = true
                }
            )
        }
        composable("player") {
            val songToShow = uiState.playerState.currentSong ?: uiState.playerState.lastPlayedSong
            if (songToShow != null) {
                FullPlayerScreen(
                    song = songToShow,
                    queue = uiState.playerState.currentQueue,
                    lyrics = uiState.playerState.lyrics,
                    isLoadingLyrics = viewModel.isLoadingLyrics.collectAsState().value,
                    playerState = uiState.playerState,
                    onClose = { navController.popBackStack() },
                    onPlayPause = viewModel::togglePlayPause,
                    onSkipNext = viewModel::skipNext,
                    onSkipPrevious = viewModel::skipPrevious,
                    onSeekTo = viewModel::seekTo,
                    onSeekForward = viewModel::seekForward,
                    onSeekBack = viewModel::seekBack,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onCycleRepeatMode = viewModel::cycleRepeatMode,
                    onToggleFavorite = { songToShow?.let { viewModel.toggleFavorite(it) } },
                    onAddToPlaylist = { songForPlaylist = it },
                    onEditSong = { viewModel.requestEditSong(it) },
                    onPlayFromQueue = { viewModel.playSong(it, uiState.playerState.currentQueue) }
                )
            }
        }
        composable("settings") {
            SettingsScreen(
                uiState = uiState,
                viewModel = viewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                onScanMusic = { viewModel.scanMusic() },
                onOpenEqualizer = { navController.navigate("equalizer") },
                onSetSleepTimer = viewModel::setSleepTimer,
                onChangeLanguage = { lang ->
                    val appLocale: LocaleListCompat = if (lang.isEmpty()) {
                        LocaleListCompat.getEmptyLocaleList()
                    } else {
                        LocaleListCompat.forLanguageTags(lang)
                    }
                    AppCompatDelegate.setApplicationLocales(appLocale)
                }
            )
        }
        composable("excluded_folders") {
            val excludedFolders by viewModel.excludedFolders.collectAsState()
            ExcludedFoldersScreen(
                excludedFolders = excludedFolders,
                onBack = { navController.popBackStack() },
                onScanMusic = { viewModel.scanMusic() },
                onAddExcludedFolder = viewModel::addExcludedFolder,
                onRemoveExcludedFolder = viewModel::removeExcludedFolder
            )
        }
        composable("equalizer") {
            EqualizerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("stats") {
            StatsScreen(
                statsRepository = viewModel.statsRepository,
                onBack = { navController.popBackStack() }
            )
        }
        composable("paywall") {
            PaywallScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun LibraryMainContent(
    uiState: LibraryUiState,
    viewModel: MusicViewModel,
    navController: NavController,
    onSearchQueryChanged: (String) -> Unit,
    onSortOrderChanged: (SortOrder) -> Unit,
    onPlayPause: () -> Unit,
    onPlay: (Song, List<Song>) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onScanMusic: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSearchOpen: () -> Unit = {},
    onSeekForward: () -> Unit,
    onSeekBack: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onReorderPlaylistSongs: (String, List<Song>) -> Unit = { _, _ -> },
    onDeletePlaylist: (PlaylistEntity) -> Unit,
    onAddSongToPlaylist: (String, Song) -> Unit,
    onAddSongsToPlaylist: (String, List<Song>) -> Unit,
    onRemoveSongFromPlaylist: (String, Song) -> Unit,
    onRemovePlaylist: (PlaylistEntity) -> Unit = {},
    onLoadPlaylistSongs: (String) -> Unit,
    onUpdateSongTags: (Song, String, String, String, String) -> Unit,
    onOpenEqualizer: () -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onChangeLanguage: (String) -> Unit,
    onPlayerClick: () -> Unit,
    onMenuClick: () -> Unit,
    onGenreClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onFolderClick: (String) -> Unit,
    onPlaylistClick: (PlaylistEntity) -> Unit,
    onSmartPlaylistClick: (SmartPlaylist) -> Unit,
    onSongMoreClick: (Song) -> Unit
) {
    val favoriteSongs = remember(uiState.songs) {
        uiState.songs.filter { it.isFavorite }
    }

    val recentSongs = remember(uiState.history, uiState.songs) {
        val songMap = uiState.songs.associateBy { it.id }
        uiState.history.mapNotNull { songMap[it.songId] }
            .distinctBy { it.id }
            .take(20)
    }

    val tabs = listOf(
        stringResource(R.string.tab_main),
        stringResource(R.string.tab_songs),
        stringResource(R.string.tab_playlists),
        stringResource(R.string.tab_genres),
        stringResource(R.string.tab_artists),
        stringResource(R.string.tab_albums),
        stringResource(R.string.tab_folders)
    )

    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val tabsListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        tabsListState.animateScrollToItem(pagerState.currentPage)
    }

    Scaffold(
        topBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = "Monkey Music",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Reactive audio jungle",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(16.dp))
                    }

                    LibraryTopBar(
                        searchQuery = uiState.searchQuery,
                        onSearchQueryChanged = onSearchQueryChanged,
                        onMenuClick = onMenuClick
                    )

                    LazyRow(
                        state = tabsListState,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(tabs) { index, title ->
                            val selected = pagerState.currentPage == index

                            Surface(
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                shape = RoundedCornerShape(22.dp),
                                color = if (selected)
                                    PrimaryOrange
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = if (selected) 8.dp else 2.dp,
                                shadowElevation = if (selected) 12.dp else 0.dp
                            ) {
                                Text(
                                    text = title,
                                    modifier = Modifier.padding(
                                        horizontal = 18.dp,
                                        vertical = 10.dp
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected)
                                        FontWeight.Bold
                                    else
                                        FontWeight.Medium,
                                    color = if (selected)
                                        Color.White
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },

        bottomBar = {
            MonkeyPlayerBottomBar(
                playerState = uiState.playerState,
                onPlayPause = onPlayPause,
                onSkipNext = onSkipNext,
                onClick = onPlayerClick
            )
        }
    ) { padding ->

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            beyondViewportPageCount = 1
        ) { page ->

            when (page) {
                0 -> HomeContent(
                    favoriteSongs = favoriteSongs,
                    recentSongs = recentSongs,
                    smartPlaylists = uiState.smartPlaylists,
                    onSongClick = { onPlay(it, uiState.songs) },
                    onSongMoreClick = onSongMoreClick,
                    onSmartPlaylistClick = onSmartPlaylistClick
                )

                1 -> SongList(
                    songs = uiState.songs,
                    currentSong = uiState.playerState.currentSong,
                    isPlaying = uiState.playerState.isPlaying,
                    onSongClick = { onPlay(it, uiState.songs) },
                    onMoreClick = onSongMoreClick
                )

                2 -> PlaylistGrid(
                    playlists = uiState.playlists,
                    smartPlaylists = uiState.smartPlaylists,
                    onCreatePlaylist = onCreatePlaylist,
                    onPlaylistClick = onPlaylistClick,
                    onSmartPlaylistClick = onSmartPlaylistClick
                )

                3 -> GenreList(uiState.genres, onGenreClick)
                4 -> ArtistList(uiState.artists, onArtistClick)
                5 -> AlbumGrid(uiState.albums, onAlbumClick)
                6 -> FolderList(uiState.folders, onFolderClick)
            }
        }
    }
}

@Composable
fun LibraryTopBar(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onMenuClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MonkeySearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChanged,
            placeholder = stringResource(R.string.search_placeholder),
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = stringResource(R.string.settings),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

