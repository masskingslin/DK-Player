package com.dk.tvplayer.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NewStreamDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit,
    onLoadAsPlaylist: ((url: String) -> Unit)? = null
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    // A URL ending in .m3u (not .m3u8, which is a legitimate single HLS stream) is
    // almost always a channel *list* rather than a single playable stream — feeding
    // it straight to the player fails with a "format not supported" error, no matter
    // how many times you retry, because it genuinely isn't a video/audio file.
    val looksLikePlaylist = remember(url) {
        val trimmed = url.trim().substringBefore('?')
        trimmed.endsWith(".m3u", ignoreCase = true) && !trimmed.endsWith(".m3u8", ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Add Network Stream") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Stream / Channel Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        isError = false
                    },
                    label = { Text("Stream URL (HLS / M3U8 / MP4)") },
                    isError = isError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isError) {
                    Text(
                        text = "Please provide a valid stream link.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (looksLikePlaylist) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.height(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (onLoadAsPlaylist != null) {
                                "This looks like an IPTV playlist (a list of channels), not a single " +
                                    "stream — it won't play directly. Use \"Load as Playlist\" below instead."
                            } else {
                                "This looks like an IPTV playlist (a list of channels), not a single " +
                                    "stream — it won't play directly here. Load it from Videos → IPTV " +
                                    "Channels → + instead."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (url.isNotBlank()) {
                        onConfirm(if (name.isBlank()) "Network Stream" else name, url.trim())
                    } else {
                        isError = true
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            Row {
                if (looksLikePlaylist && onLoadAsPlaylist != null) {
                    TextButton(onClick = { onLoadAsPlaylist(url.trim()) }) {
                        Text("Load as Playlist")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
