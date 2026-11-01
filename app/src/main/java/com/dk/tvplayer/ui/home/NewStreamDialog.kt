package com.dk.tvplayer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Adds either a single network stream (HLS / MP4 / ...) or a whole M3U channel playlist.
 *
 * A playlist URL is NOT a playable stream: handing it straight to the player fails with
 * "format not supported". The M3U tab loads it as a channel list instead. Typical IPTV
 * provider links have no .m3u extension at all (get.php?...&type=m3u_plus), so those are
 * detected too and switch the dialog to the M3U tab automatically.
 */
@Composable
fun NewStreamDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit,
    onLoadAsPlaylist: ((url: String) -> Unit)? = null
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var playlistMode by remember { mutableStateOf(false) }
    val canLoadPlaylist = onLoadAsPlaylist != null

    val looksLikePlaylist = remember(url) {
        val trimmed = url.trim()
        val path = trimmed.substringBefore('?')
        val query = trimmed.substringAfter('?', "").lowercase()
        (path.endsWith(".m3u", ignoreCase = true) && !path.endsWith(".m3u8", ignoreCase = true)) ||
            query.contains("type=m3u") || query.contains("output=m3u") || query.contains("format=m3u")
    }

    // Pasting a playlist link flips to the M3U tab by itself.
    LaunchedEffect(looksLikePlaylist) {
        if (looksLikePlaylist && canLoadPlaylist) playlistMode = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (playlistMode) "Add M3U Playlist" else "Add Network Stream") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (canLoadPlaylist) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !playlistMode,
                            onClick = { playlistMode = false; isError = false },
                            label = { Text("Single stream") }
                        )
                        FilterChip(
                            selected = playlistMode,
                            onClick = { playlistMode = true; isError = false },
                            label = { Text("M3U playlist") }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (!playlistMode) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Stream / Channel Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        isError = false
                    },
                    label = { Text(if (playlistMode) "M3U playlist URL" else "Stream URL (HLS / M3U8 / MP4)") },
                    placeholder = if (playlistMode) {
                        { Text("https://example.com/playlist.m3u") }
                    } else {
                        null
                    },
                    isError = isError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isError) {
                    Text(
                        text = "Please provide a valid link.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (playlistMode) {
                    Text(
                        text = "Loads every channel in the playlist. Find them under Videos → IPTV Channels. " +
                            "This replaces the current IPTV list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (looksLikePlaylist) {
                    // Only reachable when this dialog has no playlist loader to offer.
                    Text(
                        text = "This looks like an IPTV playlist (a list of channels), not a single " +
                            "stream — it won't play directly. Load it from Videos → IPTV Channels → + instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = url.trim()
                    if (trimmed.isBlank()) {
                        isError = true
                    } else if (playlistMode && onLoadAsPlaylist != null) {
                        onLoadAsPlaylist(trimmed)
                    } else {
                        onConfirm(if (name.isBlank()) "Network Stream" else name, trimmed)
                    }
                }
            ) {
                Text(if (playlistMode) "Load playlist" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
