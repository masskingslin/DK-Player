package com.dk.tvplayer.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
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

/**
 * "Jump to Time" — type an hours / minutes / seconds timestamp and seek straight to it.
 * Fields are left blank-means-zero, so "5" in the minutes box alone jumps to 5:00.
 */
@Composable
fun JumpToTimeDialog(
    durationMs: Long,
    onDismiss: () -> Unit,
    onJump: (positionMs: Long) -> Unit
) {
    var hours by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var seconds by remember { mutableStateOf("") }

    val totalMs = ((hours.toLongOrNull() ?: 0L) * 3_600_000L) +
        ((minutes.toLongOrNull() ?: 0L) * 60_000L) +
        ((seconds.toLongOrNull() ?: 0L) * 1_000L)
    // A duration of 0 means it hasn't loaded (or it's a live stream) — in that case
    // there's nothing to validate against, so any non-negative time is accepted.
    val isValid = (hours.isNotEmpty() || minutes.isNotEmpty() || seconds.isNotEmpty()) &&
        (durationMs <= 0L || totalMs <= durationMs)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jump to Time") },
        text = {
            Row {
                OutlinedTextField(
                    value = hours,
                    onValueChange = { hours = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("HH") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("MM") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = seconds,
                    onValueChange = { seconds = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("SS") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onJump(totalMs) }, enabled = isValid) { Text("Jump") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
