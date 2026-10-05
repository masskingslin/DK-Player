package com.dk.tvplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.dk.tvplayer.player.EQ_MAX_DB
import com.dk.tvplayer.player.EQ_MIN_DB
import com.dk.tvplayer.player.EqBandFrequencies
import com.dk.tvplayer.player.EqPreset
import com.dk.tvplayer.player.EqualizerStore
import kotlin.math.roundToInt

/**
 * VLC-style equalizer: a bottom sheet with 10 vertical band sliders, a preamp, preset chips,
 * custom presets ("+" saves the current curve) and a full preset list with curve thumbnails.
 * All state lives in [EqualizerStore]; [onChanged] tells the player to re-apply it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerDialog(
    available: Boolean,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val settings by EqualizerStore.state.collectAsState()
    val custom by EqualizerStore.customPresets.collectAsState()
    val hidden by EqualizerStore.hiddenPresets.collectAsState()
    var showList by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    val presets = com.dk.tvplayer.player.EqPresets.builtIn + custom
    val currentPreset = presets.firstOrNull { it.id == settings.presetId }

    fun apply(change: (com.dk.tvplayer.player.EqualizerSettings) -> com.dk.tvplayer.player.EqualizerSettings) {
        EqualizerStore.update(change)
        EqualizerStore.writeThroughIfCustom()
        onChanged()
    }

    fun pick(preset: EqPreset) {
        EqualizerStore.selectPreset(preset)
        onChanged()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Equalizer", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { showList = !showList }) {
                    Icon(
                        if (showList) Icons.Default.Tune else Icons.Default.ViewList,
                        contentDescription = if (showList) "Show sliders" else "Show preset list"
                    )
                }
                IconButton(onClick = { showSaveDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Save as preset")
                }
            }

            if (!available) {
                Text(
                    "The equalizer becomes available once playback has started " +
                        "(and isn't supported on every device).",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(checked = settings.enabled, onCheckedChange = { on -> apply { it.copy(enabled = on) } })
                }

                if (showList) {
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(460.dp)) {
                        items(presets, key = { it.id }) { preset ->
                            PresetListRow(
                                preset = preset,
                                selected = preset.id == settings.presetId,
                                onClick = { pick(preset) }
                            )
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        items(presets.filter { it.id !in hidden || it.id == settings.presetId }, key = { it.id }) { preset ->
                            FilterChip(
                                selected = preset.id == settings.presetId,
                                onClick = { pick(preset) },
                                label = { Text(preset.name, maxLines = 1) },
                                leadingIcon = if (preset.id == settings.presetId) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    currentPreset?.name ?: "Custom",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (currentPreset != null && !currentPreset.builtIn) {
                                    IconButton(onClick = { showRenameDialog = true }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Rename preset")
                                    }
                                    IconButton(onClick = { EqualizerStore.deleteCustom(currentPreset.id); onChanged() }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete preset")
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Preamp", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
                                Slider(
                                    value = settings.preampDb,
                                    onValueChange = { v -> apply { it.copy(preampDb = v, presetId = keepIfCustom(it)) } },
                                    valueRange = EQ_MIN_DB..EQ_MAX_DB,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${settings.preampDb.roundToInt()}dB",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(44.dp),
                                    textAlign = TextAlign.End
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth()) {
                                EqBandFrequencies.forEachIndexed { index, hz ->
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "${settings.bandsDb[index].roundToInt()}dB",
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1
                                        )
                                        VerticalSlider(
                                            value = settings.bandsDb[index],
                                            onValueChange = { v ->
                                                apply { s -> moveBand(s, index, v) }
                                            },
                                            range = EQ_MIN_DB..EQ_MAX_DB,
                                            modifier = Modifier.width(34.dp).height(180.dp)
                                        )
                                        Text(
                                            if (hz >= 1000) "${hz / 1000}kHz" else "${hz}Hz",
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text("Snap bands", style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.width(12.dp))
                                Switch(
                                    checked = settings.snapBands,
                                    onCheckedChange = { on -> apply { it.copy(snapBands = on) } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        NameDialog(
            title = "Save preset",
            initial = "",
            confirmLabel = "Save",
            onDismiss = { showSaveDialog = false },
            onConfirm = { name ->
                EqualizerStore.saveCurrentAs(name)
                showSaveDialog = false
            }
        )
    }
    if (showRenameDialog && currentPreset != null) {
        NameDialog(
            title = "Rename preset",
            initial = currentPreset.name,
            confirmLabel = "Rename",
            onDismiss = { showRenameDialog = false },
            onConfirm = { name ->
                EqualizerStore.renameCustom(currentPreset.id, name)
                showRenameDialog = false
            }
        )
    }
}

/** A hand-tuned curve no longer matches a built-in preset, so it becomes "Custom". */
private fun keepIfCustom(s: com.dk.tvplayer.player.EqualizerSettings): String =
    if (s.presetId.startsWith("custom:")) s.presetId else ""

/** Applies a band change; with snap on, the neighbouring bands follow along (half / quarter). */
private fun moveBand(
    s: com.dk.tvplayer.player.EqualizerSettings,
    index: Int,
    newValue: Float
): com.dk.tvplayer.player.EqualizerSettings {
    val delta = newValue - s.bandsDb[index]
    val updated = s.bandsDb.toMutableList()
    updated[index] = newValue.coerceIn(EQ_MIN_DB, EQ_MAX_DB)
    if (s.snapBands) {
        for ((offset, factor) in listOf(1 to 0.5f, 2 to 0.25f)) {
            for (neighbour in listOf(index - offset, index + offset)) {
                if (neighbour in updated.indices) {
                    updated[neighbour] = (updated[neighbour] + delta * factor).coerceIn(EQ_MIN_DB, EQ_MAX_DB)
                }
            }
        }
    }
    return s.copy(bandsDb = updated, presetId = keepIfCustom(s))
}

@Composable
private fun PresetListRow(preset: EqPreset, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            CurveThumbnail(preset.bandsDb, modifier = Modifier.size(width = 56.dp, height = 40.dp))
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(preset.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
    }
}

/** Small filled step chart of a preset's 10 bands, like the thumbnails in VLC's list. */
@Composable
internal fun CurveThumbnail(bandsDb: List<Float>, modifier: Modifier = Modifier) {
    val fill = MaterialTheme.colorScheme.onSurfaceVariant
    val back = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier.clip(RoundedCornerShape(6.dp))) {
        drawRect(back)
        val barWidth = size.width / bandsDb.size
        bandsDb.forEachIndexed { i, db ->
            val fraction = ((db - EQ_MIN_DB) / (EQ_MAX_DB - EQ_MIN_DB)).coerceIn(0f, 1f)
            val h = size.height * fraction
            drawRect(
                color = fill.copy(alpha = 0.7f),
                topLeft = Offset(i * barWidth, size.height - h),
                size = Size(barWidth + 1f, h)
            )
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                singleLine = true,
                label = { Text("Preset name") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** A Slider turned on its side (bottom = min, top = max). */
@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        modifier = modifier
            .graphicsLayer {
                rotationZ = 270f
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints(
                        minWidth = constraints.minHeight,
                        maxWidth = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth
                    )
                )
                layout(placeable.height, placeable.width) {
                    placeable.place(-placeable.width, 0)
                }
            }
    )
}
