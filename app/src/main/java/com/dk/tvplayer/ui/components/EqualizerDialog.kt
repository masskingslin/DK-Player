package com.dk.tvplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import com.dk.tvplayer.player.EqualizerState

/**
 * Equalizer for the local player's audio session. Each band gets its own horizontal
 * slider labelled by center frequency. Touching any slider turns the equalizer on
 * (see TvExoPlayerManager.setEqualizerBandLevel), so tweaking a band always has an
 * audible effect without a separate "enable" step.
 */
@Composable
fun EqualizerDialog(
    state: EqualizerState,
    onDismiss: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onBandLevelChange: (bandIndex: Int, levelMillibel: Int) -> Unit,
    onPresetSelected: (Int) -> Unit
) {
    var showPresets by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Equalizer") },
        text = {
            if (!state.available) {
                Text(
                    "The equalizer becomes available once playback has started " +
                        "(and isn't supported on every device)."
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enabled", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = state.enabled, onCheckedChange = onEnabledChange)
                    }

                    if (state.presets.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box {
                            TextButton(onClick = { showPresets = true }) {
                                Text(
                                    if (state.currentPreset in state.presets.indices) {
                                        "Preset: ${state.presets[state.currentPreset]}"
                                    } else {
                                        "Preset: Custom"
                                    }
                                )
                            }
                            DropdownMenu(expanded = showPresets, onDismissRequest = { showPresets = false }) {
                                state.presets.forEachIndexed { index, name ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            onPresetSelected(index)
                                            showPresets = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    state.bands.forEach { band ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (band.centerFreqHz >= 1000) {
                                    "${band.centerFreqHz / 1000}kHz"
                                } else {
                                    "${band.centerFreqHz}Hz"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(60.dp)
                            )
                            Slider(
                                value = band.currentLevelMillibel.toFloat(),
                                onValueChange = { onBandLevelChange(band.index, it.toInt()) },
                                valueRange = band.minLevelMillibel.toFloat()..band.maxLevelMillibel.toFloat(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
