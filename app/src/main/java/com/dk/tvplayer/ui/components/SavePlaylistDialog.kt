package com.dk.tvplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dk.tvplayer.data.local.PlaylistEntity

/**
 * Saves whatever's currently playing into a playlist — either one that already exists
 * (tap its name) or a brand new one (type a name and confirm).
 */
@Composable
fun SavePlaylistDialog(
    playlists: List<PlaylistEntity>,
    onDismiss: () -> Unit,
    onAddToExisting: (PlaylistEntity) -> Unit,
    onCreateNew: (name: String) -> Unit
) {
    var newPlaylistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save to Playlist") },
        text = {
            Column {
                if (playlists.isNotEmpty()) {
                    Text("Add to an existing playlist:", style = MaterialTheme.typography.bodyMedium)
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(playlists, key = { it.id }) { playlist ->
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAddToExisting(playlist) }
                                    .padding(vertical = 12.dp)
                            )
                        }
                    }
                }
                Text(
                    "Or create a new one:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("New playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreateNew(newPlaylistName) },
                enabled = newPlaylistName.isNotBlank()
            ) { Text("Create & Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
