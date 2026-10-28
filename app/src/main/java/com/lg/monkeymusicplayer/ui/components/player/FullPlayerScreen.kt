package com.lg.monkeymusicplayer.ui.components.player

import android.Manifest
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.lg.monkeymusicplayer.R
import com.lg.monkeymusicplayer.data.model.LyricLine
import com.lg.monkeymusicplayer.data.model.Song
import com.lg.monkeymusicplayer.ui.PlayerState
import com.lg.monkeymusicplayer.ui.components.AudioVisualizer
import com.lg.monkeymusicplayer.ui.components.LyricsView
import com.lg.monkeymusicplayer.ui.components.MediaProgressSlider
import com.lg.monkeymusicplayer.ui.components.core.PlaybackWaveform
import com.lg.monkeymusicplayer.ui.components.dialogs.EditTagsDialog
import com.lg.monkeymusicplayer.ui.theme.PrimaryOrange
import com.lg.monkeymusicplayer.ui.theme.dynamic.BitmapColorEngine
import com.lg.monkeymusicplayer.ui.theme.dynamic.LocalDynamicPlayerColors
import com.lg.monkeymusicplayer.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableFloatStateOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    song: Song,
    queue: List<Song>,
    lyrics: List<LyricLine>,
    isLoadingLyrics: Boolean,
    playerState: PlayerState,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekForward: () -> Unit,
    onSeekBack: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onEditSong: (Song) -> Unit,
    onPlayFromQueue: (Song) -> Unit
) {
    val tabLabels = listOf(
        stringResource(R.string.tab_playlist),
        stringResource(R.string.lyrics_tab),
        stringResource(R.string.queue_tab)
    )
    val pagerState = rememberPagerState(pageCount = { tabLabels.size })
    val scope = rememberCoroutineScope()
    var playerVisible by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val dismissThresholdPx = remember(density) { with(density) { 28.dp.toPx() } }
    var dismissDragY by remember { mutableFloatStateOf(0f) }
    var dismissDragX by remember { mutableFloatStateOf(0f) }
    var hasDismissTriggered by remember { mutableStateOf(false) }

    val dismissGestureModifier = Modifier.pointerInput(onClose) {
        detectDragGestures(
            onDragStart = {
                dismissDragY = 0f
                dismissDragX = 0f
                hasDismissTriggered = false
            },
            onDragEnd = {
                dismissDragY = 0f
                dismissDragX = 0f
                hasDismissTriggered = false
            },
            onDragCancel = {
                dismissDragY = 0f
                dismissDragX = 0f
                hasDismissTriggered = false
            },
            onDrag = { change, dragAmount ->
                dismissDragY += dragAmount.y
                dismissDragX += dragAmount.x
                if (kotlin.math.abs(dismissDragY) > kotlin.math.abs(dismissDragX)) {
                    if (dismissDragY >= dismissThresholdPx && !hasDismissTriggered) {
                        hasDismissTriggered = true
                        change.consume()
                        onClose()
                    }
                }
            }
        )
    }

    val context = LocalContext.current
    val defaultColors = LocalDynamicPlayerColors.current
    var extractedColors by remember(song.id, song.albumArtUri) { mutableStateOf(defaultColors) }

    var isVisualizerEnabled by remember { mutableStateOf(false) }
    val recordAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isVisualizerEnabled = true
        }
    }

    LaunchedEffect(song.albumArtUri) {
        val uri = song.albumArtUri
        if (!uri.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val loader = ImageLoader(context)
                    val request = ImageRequest.Builder(context)
                        .data(uri)
                        .allowHardware(false)
                        .build()
                    val result = (loader.execute(request) as? SuccessResult)?.drawable
                    if (result is BitmapDrawable) {
                        val engine = BitmapColorEngine()
                        val colors = engine.extract(result.bitmap)
                        withContext(Dispatchers.Main) {
                            extractedColors = colors
                        }
                    }
                } catch (e: Exception) {
                    // Fallback to default colors on error
                }
            }
        } else {
            extractedColors = defaultColors
        }
    }

    CompositionLocalProvider(LocalDynamicPlayerColors provides extractedColors) {
        val dynamicColors = LocalDynamicPlayerColors.current

        val animatedBgDark by animateColorAsState(
            targetValue = dynamicColors.backgroundDark,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            label = "bg_dark"
        )
        val animatedDark by animateColorAsState(
            targetValue = dynamicColors.dark,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            label = "dark"
        )
        val animatedBg by animateColorAsState(
            targetValue = dynamicColors.background,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            label = "bg"
        )
        val animatedAccent by animateColorAsState(
            targetValue = dynamicColors.accent,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            label = "accent"
        )
        val animatedTextPrimary by animateColorAsState(
            targetValue = dynamicColors.textPrimary,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            label = "text_primary"
        )
        val animatedGlow by animateColorAsState(
            targetValue = dynamicColors.vibrant.copy(alpha = 0.22f),
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            label = "glow"
        )

        LaunchedEffect(Unit) {
            playerVisible = true
        }

        var showEditDialog by remember { mutableStateOf(false) }
        if (showEditDialog) {
            EditTagsDialog(
                song = song,
                isSaving = false,
                onDismiss = { showEditDialog = false },
                onSave = { title, artist, album, genre ->
                    onEditSong(song.copy(title = title, artist = artist, album = album, genre = genre))
                    showEditDialog = false
                }
            )
        }

        AnimatedVisibility(
            visible = playerVisible,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 200))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                animatedBgDark,
                                animatedDark,
                                animatedBg
                            )
                        )
                    )
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(animatedGlow, Color.Transparent),
                                center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.32f),
                                radius = size.width * 0.85f
                            )
                        )
                    }
                    .systemBarsPadding()
            ) {
                // ── TopBar: flecha abajo | PLAYLIST / nombre | (vacío) ────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(dismissGestureModifier)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = dynamicColors.controls.copy(alpha = 0.85f),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.tab_playlist).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = dynamicColors.controls.copy(alpha = 0.6f),
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = song.album.ifBlank { song.artist },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = animatedTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // ── Tabs: Playlist / Letras / Cola ─────────────────────────────────
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = animatedBgDark,
                    contentColor = animatedTextPrimary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            color = animatedAccent,
                            height = 2.dp
                        )
                    },
                    divider = {}
                ) {
                    tabLabels.forEachIndexed { index, label ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = {
                                Text(
                                    label,
                                    color = if (pagerState.currentPage == index) animatedTextPrimary
                                            else dynamicColors.controls.copy(alpha = 0.45f),
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        )
                    }
                }

                // ── Pager ──────────────────────────────────────────────────────────
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    when (page) {

                        // ── Página 0: Portada + info + controles ───────────────────
                        0 -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(dismissGestureModifier)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            animatedBgDark,
                                            animatedDark,
                                            animatedBg
                                        )
                                    )
                                )
                                .padding(horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(Modifier.height(12.dp))

                            val artworkScale by animateFloatAsState(
                                targetValue = if (playerVisible) 1f else 0.85f,
                                animationSpec = tween(500),
                                label = ""
                            )

                            // Artwork grande
                            AsyncImage(
                                model = song.albumArtUri,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .graphicsLayer {
                                        scaleX = artworkScale
                                        scaleY = artworkScale
                                    }
                                    .clip(RoundedCornerShape(22.dp)),
                                contentScale = ContentScale.Crop,
                                error = painterResource(R.drawable.ic_monkey_head)
                            )

                            Spacer(Modifier.height(20.dp))

                            // Título + íconos edit/fav en la misma fila
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        song.title,
                                        style = MaterialTheme.typography.displaySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = animatedTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        song.artist,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = animatedAccent,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { showEditDialog = true }) {
                                    Icon(Icons.Default.Edit, null,
                                        tint = dynamicColors.controls.copy(alpha = 0.7f))
                                }
                                IconButton(onClick = onToggleFavorite) {
                                    Icon(
                                        if (song.isFavorite) Icons.Default.Favorite
                                        else Icons.Default.FavoriteBorder,
                                        null,
                                        tint = if (song.isFavorite) animatedAccent
                                               else dynamicColors.controls.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // Slider de progreso
                            MediaProgressSlider(
                                playerState = playerState,
                                onSeekTo = onSeekTo
                            )

                            Spacer(Modifier.height(18.dp))

                            val showRealVisualizer = isVisualizerEnabled && PermissionHelper.hasRecordAudioPermission(context) && playerState.audioSessionId > 0

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clickable {
                                        val hasPerm = PermissionHelper.hasRecordAudioPermission(context)
                                        if (!isVisualizerEnabled) {
                                            if (hasPerm) {
                                                isVisualizerEnabled = true
                                            } else {
                                                recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        } else {
                                            isVisualizerEnabled = false
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (showRealVisualizer) {
                                    AudioVisualizer(
                                        audioSessionId = playerState.audioSessionId,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    PlaybackWaveform(
                                        isPlaying = playerState.isPlaying,
                                        accent = PrimaryOrange
                                    )
                                }
                            }

                            Spacer(Modifier.height(18.dp))

                            // Fila principal: prev | play | next
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onSkipPrevious, modifier = Modifier.size(52.dp)) {
                                    Icon(Icons.Default.SkipPrevious, null,
                                        tint = dynamicColors.controls, modifier = Modifier.size(36.dp))
                                }
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(animatedAccent)
                                        .clickable { onPlayPause() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (playerState.isPlaying) Icons.Default.Pause
                                        else Icons.Default.PlayArrow,
                                        null,
                                        tint = dynamicColors.controls,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                IconButton(onClick = onSkipNext, modifier = Modifier.size(52.dp)) {
                                    Icon(Icons.Default.SkipNext, null,
                                        tint = dynamicColors.controls, modifier = Modifier.size(36.dp))
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Fila inferior: shuffle | seek-back | seek-forward | add-to-queue | repeat
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onToggleShuffle) {
                                    Icon(
                                        Icons.Default.Shuffle, null,
                                        tint = if (playerState.isShuffleMode) animatedAccent
                                               else dynamicColors.controls.copy(alpha = 0.6f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                IconButton(onClick = onSeekBack) {
                                    Icon(Icons.Default.Replay10, null,
                                        tint = dynamicColors.controls.copy(alpha = 0.7f),
                                        modifier = Modifier.size(28.dp))
                                }
                                IconButton(onClick = { onAddToPlaylist(song) }) {
                                    Icon(Icons.Default.PlaylistAdd, null,
                                        tint = dynamicColors.controls.copy(alpha = 0.7f),
                                        modifier = Modifier.size(28.dp))
                                }
                                IconButton(onClick = onSeekForward) {
                                    Icon(Icons.Default.Forward10, null,
                                        tint = dynamicColors.controls.copy(alpha = 0.7f),
                                        modifier = Modifier.size(28.dp))
                                }
                                IconButton(onClick = onCycleRepeatMode) {
                                    Icon(
                                        when (playerState.repeatMode) {
                                            androidx.media3.common.Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                            else -> Icons.Default.Repeat
                                        },
                                        null,
                                        tint = if (playerState.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF)
                                                   animatedAccent
                                               else dynamicColors.controls.copy(alpha = 0.6f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        // ── Página 1: Letras ───────────────────────────────────────
                        1 -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            animatedBgDark,
                                            animatedDark,
                                            animatedBg
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                isLoadingLyrics -> CircularProgressIndicator(color = animatedAccent)
                                lyrics.isEmpty() -> Text(
                                    stringResource(R.string.lyrics_not_found),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = dynamicColors.controls.copy(alpha = 0.5f)
                                )
                                else -> LyricsView(
                                    lyrics = lyrics,
                                    currentPosition = playerState.currentPosition,
                                    accentColor = animatedAccent,
                                    onLyricClick = onSeekTo
                                )
                            }
                        }

                        // ── Página 2: Cola ─────────────────────────────────────────
                        2 -> LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            animatedBgDark,
                                            animatedDark,
                                            animatedBg
                                        )
                                    )
                                )
                        ) {
                            items(queue, key = { it.id }) { queueSong ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            queueSong.title,
                                            fontWeight = if (queueSong.id == song.id) FontWeight.Bold else FontWeight.Normal,
                                            color = if (queueSong.id == song.id) animatedAccent else animatedTextPrimary
                                        )
                                    },
                                    supportingContent = {
                                        Text(queueSong.artist, color = dynamicColors.textSecondary)
                                    },
                                    leadingContent = {
                                        if (queueSong.id == song.id) {
                                            Icon(Icons.Default.VolumeUp, null, tint = animatedAccent)
                                        } else {
                                            AsyncImage(
                                                model = queueSong.albumArtUri,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(6.dp)),
                                                contentScale = ContentScale.Crop,
                                                error = painterResource(R.drawable.ic_monkey_head)
                                            )
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = dynamicColors.backgroundDark),
                                    modifier = Modifier.clickable { onPlayFromQueue(queueSong) }
                                )
                                HorizontalDivider(color = dynamicColors.controls.copy(alpha = 0.20f))
                            }
                        }
                    }
                }
            }
        }
    }
}
