package com.dk.tvplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Turns the player's touch gestures on/off individually. Values persist via Settings. */
@Composable
fun ControlSettingsDialog(
    gestureSeekEnabled: Boolean,
    gestureBrightnessVolumeEnabled: Boolean,
    doubleTapSeekEnabled: Boolean,
    onDismiss: () -> Unit,
    onGestureSeekChange: (Boolean) -> Unit,
    onGestureBrightnessVolumeChange: (Boolean) -> Unit,
    onDoubleTapSeekChange: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Control Settings") },
        text = {
            Column {
                ControlToggleRow("Swipe to seek", gestureSeekEnabled, onGestureSeekChange)
                ControlToggleRow(
                    "Swipe for brightness / volume",
                    gestureBrightnessVolumeEnabled,
                    onGestureBrightnessVolumeChange
                )
                ControlToggleRow("Double-tap to seek", doubleTapSeekEnabled, onDoubleTapSeekChange)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun ControlToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
