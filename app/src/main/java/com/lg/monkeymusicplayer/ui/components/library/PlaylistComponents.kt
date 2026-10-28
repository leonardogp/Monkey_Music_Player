package com.lg.monkeymusicplayer.ui.components.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lg.monkeymusicplayer.R
import com.lg.monkeymusicplayer.data.database.PlaylistEntity
import com.lg.monkeymusicplayer.data.database.PlaylistWithCount
import com.lg.monkeymusicplayer.data.model.SmartPlaylist
import com.lg.monkeymusicplayer.data.model.SmartPlaylistType
import com.lg.monkeymusicplayer.ui.components.dialogs.CreatePlaylistDialog

@Composable
fun PlaylistGrid(
    playlists: List<PlaylistWithCount>,
    smartPlaylists: List<SmartPlaylist>,
    onCreatePlaylist: (String) -> Unit,
    onPlaylistClick: (PlaylistEntity) -> Unit,
    onSmartPlaylistClick: (SmartPlaylist) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                onCreatePlaylist(name)
                showCreateDialog = false
            }
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clickable { showCreateDialog = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(48.dp))
                        Text(
                            text = stringResource(R.string.new_playlist),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        items(smartPlaylists) { smart ->
            SmartPlaylistCard(smart, onClick = { onSmartPlaylistClick(smart) })
        }

        items(playlists) { playlist ->
            val subtitle = if (playlist.songCount == 1) {
                stringResource(R.string.song_singular)
            } else {
                stringResource(R.string.stats_artist_songs, playlist.songCount)
            }
            PlaylistCard(
                name = playlist.name,
                subtitle = subtitle,
                onPlaylistClick = { onPlaylistClick(PlaylistEntity(id = playlist.id, name = playlist.name)) }
            )
        }
    }
}

@Composable
fun PlaylistCard(name: String, subtitle: String, onPlaylistClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onPlaylistClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Icon(
                Icons.Default.QueueMusic,
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).size(64.dp).alpha(0.12f)
            )
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SmartPlaylistCard(smart: SmartPlaylist, onClick: () -> Unit) {
    val title = when (smart.type) {
        SmartPlaylistType.DAILY_MIX -> stringResource(R.string.smart_daily_mix_title)
        SmartPlaylistType.REDISCOVER -> stringResource(R.string.smart_rediscover_title)
        SmartPlaylistType.TOP_SONGS -> stringResource(R.string.smart_top_songs_title)
    }
    val icon = when (smart.type) {
        SmartPlaylistType.DAILY_MIX -> Icons.Default.AutoAwesome
        SmartPlaylistType.REDISCOVER -> Icons.Default.History
        SmartPlaylistType.TOP_SONGS -> Icons.Default.Star
    }

    val gradients = listOf(
        listOf(Color(0xFFFF8C00), Color(0xFFFF5500)),  // DAILY_MIX
        listOf(Color(0xFF7B2FBE), Color(0xFFFF8C00)),  // REDISCOVER
        listOf(Color(0xFF1DB954), Color(0xFF0D7A38))   // TOP_SONGS
    )
    val gradientColors = when (smart.type) {
        SmartPlaylistType.DAILY_MIX -> gradients[0]
        SmartPlaylistType.REDISCOVER -> gradients[1]
        SmartPlaylistType.TOP_SONGS -> gradients[2]
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(gradientColors))
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).size(56.dp).alpha(0.2f),
                tint = Color.White
            )
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.stats_artist_songs, smart.songs.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

