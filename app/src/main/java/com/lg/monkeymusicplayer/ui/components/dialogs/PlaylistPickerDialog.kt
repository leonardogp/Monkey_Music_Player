package com.lg.monkeymusicplayer.ui.components.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lg.monkeymusicplayer.R
import com.lg.monkeymusicplayer.data.database.PlaylistEntity
import com.lg.monkeymusicplayer.data.database.PlaylistWithCount
import com.lg.monkeymusicplayer.ui.theme.PrimaryOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPickerDialog(
    playlists: List<PlaylistWithCount>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (PlaylistEntity) -> Unit,
    onCreatePlaylist: (String) -> Unit
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_to_playlist)) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                item {
                    ListItem(
                        modifier = Modifier.clickable { showCreateDialog = true },
                        headlineContent = { Text(stringResource(R.string.new_playlist), color = PrimaryOrange) },
                        leadingContent = { Icon(Icons.Default.Add, contentDescription = null, tint = PrimaryOrange) }
                    )
                }
                items(playlists) { playlist ->
                    val subtitle = if (playlist.songCount == 1) {
                        stringResource(R.string.song_singular)
                    } else {
                        stringResource(R.string.stats_artist_songs, playlist.songCount)
                    }
                    ListItem(
                        modifier = Modifier.clickable { onPlaylistSelected(PlaylistEntity(id = playlist.id, name = playlist.name)) },
                        headlineContent = { Text(playlist.name) },
                        supportingContent = { Text(subtitle) },
                        leadingContent = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
