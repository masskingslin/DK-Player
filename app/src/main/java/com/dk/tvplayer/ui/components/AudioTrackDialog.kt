package com.dk.tvplayer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dk.tvplayer.player.AudioTrackInfo

@Composable
fun AudioTrackDialog(
    tracks: List<AudioTrackInfo>,
    onDismiss: () -> Unit,
    onTrackSelected: (groupIndex: Int, trackIndex: Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio Track") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (tracks.isEmpty()) {
                    Text("Only one audio track is available for this media.")
                } else {
                    tracks.forEach { track ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = track.isSelected,
                                onClick = { onTrackSelected(track.groupIndex, track.trackIndex) }
                            )
                            Text(track.label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
