package com.lg.monkeymusicplayer.ui.components.core

import com.lg.monkeymusicplayer.ui.theme.PrimaryOrange
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import coil.compose.AsyncImage
import com.lg.monkeymusicplayer.data.model.Song
import com.lg.monkeymusicplayer.ui.PlayerState
import com.lg.monkeymusicplayer.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun MonkeyPlayerBottomBar(
    playerState: PlayerState,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onClick: () -> Unit
) {
    val song: Song? = playerState.currentSong ?: playerState.lastPlayedSong
    if (song == null) return

    val density = LocalDensity.current
    val thresholdPx = remember(density) { with(density) { 24.dp.toPx() } }
    var totalDragY by remember { mutableFloatStateOf(0f) }
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var hasTriggered by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(78.dp)
            .pointerInput(onClick) {
                detectDragGestures(
                    onDragStart = {
                        totalDragY = 0f
                        totalDragX = 0f
                        hasTriggered = false
                    },
                    onDragEnd = {
                        totalDragY = 0f
                        totalDragX = 0f
                        hasTriggered = false
                    },
                    onDragCancel = {
                        totalDragY = 0f
                        totalDragX = 0f
                        hasTriggered = false
                    },
                    onDrag = { change, dragAmount ->
                        totalDragY += dragAmount.y
                        totalDragX += dragAmount.x
                        if (kotlin.math.abs(totalDragY) > kotlin.math.abs(totalDragX)) {
                            if (totalDragY <= -thresholdPx && !hasTriggered) {
                                hasTriggered = true
                                change.consume()
                                onClick()
                            }
                        }
                    }
                )
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 14.dp,
        shadowElevation = 18.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        )
    ){
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
        ){
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = null,
                modifier = Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop,
                error = painterResource(R.drawable.ic_monkey_head)
            )
            // Text area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )

                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )

                Spacer(Modifier.height(4.dp))

                PlaybackWaveform(
                    isPlaying = playerState.isPlaying,
                    accent = PrimaryOrange,
                    modifier = Modifier.height(14.dp)
                )
            }
            Surface(
                onClick = onPlayPause,
                shape = RoundedCornerShape(18.dp),
                color = PrimaryOrange,
                tonalElevation = 8.dp,
                modifier = Modifier.size(46.dp)
            ) {
                androidx.compose.foundation.layout.Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (playerState.isPlaying)
                            Icons.Filled.Pause
                        else
                            Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            IconButton(
                onClick = onSkipNext,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
