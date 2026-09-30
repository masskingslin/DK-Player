package com.dk.tvplayer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.Format
import java.util.Locale

/**
 * Technical details of what the renderers are consuming right now. Any field the
 * stream doesn't report (Media3 uses -1 for "unknown") is simply left out rather than
 * shown as a meaningless number.
 */
@Composable
fun VideoInfoDialog(
    videoFormat: Format?,
    audioFormat: Format?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video Information") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Video", style = MaterialTheme.typography.titleSmall)
                if (videoFormat != null) {
                    InfoLine("Codec", videoFormat.codecs ?: videoFormat.sampleMimeType ?: "Unknown")
                    if (videoFormat.width > 0 && videoFormat.height > 0) {
                        InfoLine("Resolution", "${videoFormat.width} × ${videoFormat.height}")
                    }
                    if (videoFormat.frameRate > 0f) {
                        InfoLine("Frame rate", String.format(Locale.US, "%.2f fps", videoFormat.frameRate))
                    }
                    if (videoFormat.bitrate > 0) {
                        InfoLine("Bitrate", "${videoFormat.bitrate / 1000} kbps")
                    }
                } else {
                    Text("No video track", style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Audio", style = MaterialTheme.typography.titleSmall)
                if (audioFormat != null) {
                    InfoLine("Codec", audioFormat.codecs ?: audioFormat.sampleMimeType ?: "Unknown")
                    if (audioFormat.channelCount > 0) {
                        InfoLine("Channels", audioFormat.channelCount.toString())
                    }
                    if (audioFormat.sampleRate > 0) {
                        InfoLine("Sample rate", "${audioFormat.sampleRate} Hz")
                    }
                    if (audioFormat.bitrate > 0) {
                        InfoLine("Bitrate", "${audioFormat.bitrate / 1000} kbps")
                    }
                } else {
                    Text("No audio track", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Text("$label: $value", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
}

/** Plain-language cheat sheet for the player's gestures — kept in sync with the actual
 *  gesture handling in PhonePlayerScreen (tap, double-tap zones, drag zones, Lock). */
@Composable
fun VideoPlayerTipsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video Player Tips") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                TipLine("Tap the video once to show or hide the controls.")
                TipLine("Double-tap the left side to rewind 10 seconds, the right side to skip forward 10 seconds, or the middle to play/pause.")
                TipLine("Drag up or down on the left side to change brightness.")
                TipLine("Drag up or down on the right side to change volume.")
                TipLine("Drag left or right across the middle to scrub through the video.")
                TipLine("Choose Lock from the ⋮ menu to ignore all touches — handy in a pocket. Tap the unlock button to get controls back.")
                TipLine("Any of these gestures can be switched off under Control settings.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        }
    )
}

@Composable
private fun TipLine(text: String) {
    Text("• $text", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
}
