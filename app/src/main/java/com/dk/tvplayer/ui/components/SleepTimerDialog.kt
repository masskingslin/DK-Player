package com.dk.tvplayer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

private val PRESET_MINUTES = listOf(15, 30, 45, 60)

@Composable
fun SleepTimerDialog(
    activeRemainingSec: Long?,
    onDismiss: () -> Unit,
    onStart: (minutes: Int) -> Unit,
    onCancel: () -> Unit
) {
    var customMinutes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep Timer") },
        text = {
            Column {
                if (activeRemainingSec != null) {
                    val mins = activeRemainingSec / 60
                    val secs = activeRemainingSec % 60
                    Text(
                        "Playback will pause in ${mins}m ${secs}s",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    PRESET_MINUTES.forEach { minutes ->
                        TextButton(onClick = { onStart(minutes) }) { Text("${minutes}m") }
                    }
                }
                OutlinedTextField(
                    value = customMinutes,
                    onValueChange = { customMinutes = it.filter { c -> c.isDigit() } },
                    label = { Text("Custom minutes") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { customMinutes.toIntOrNull()?.let { if (it > 0) onStart(it) } },
                enabled = customMinutes.toIntOrNull()?.let { it > 0 } == true
            ) { Text("Start") }
        },
        dismissButton = {
            Row {
                if (activeRemainingSec != null) {
                    TextButton(onClick = onCancel) { Text("Cancel Timer", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}
