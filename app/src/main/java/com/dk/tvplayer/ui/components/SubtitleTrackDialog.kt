package com.dk.tvplayer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.dk.tvplayer.player.SubtitleTrackInfo

@Composable
fun SubtitleTrackDialog(
    tracks: List<SubtitleTrackInfo>,
    onDismiss: () -> Unit,
    onTrackSelected: (groupIndex: Int, trackIndex: Int) -> Unit,
    onSubtitlesOff: () -> Unit,
    onLoadExternalSubtitle: (url: String) -> Unit
) {
    var externalUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subtitles") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(selected = tracks.none { it.isSelected }, onClick = onSubtitlesOff)
                    Text("Off", modifier = Modifier.padding(start = 8.dp))
                }
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text("Load external subtitle (.srt/.vtt URL)", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.padding(top = 4.dp))
                OutlinedTextField(
                    value = externalUrl,
                    onValueChange = { externalUrl = it },
                    placeholder = { Text("https://.../subtitles.srt") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (externalUrl.isNotBlank()) onLoadExternalSubtitle(externalUrl.trim()) },
                enabled = externalUrl.isNotBlank()
            ) { Text("Load") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
