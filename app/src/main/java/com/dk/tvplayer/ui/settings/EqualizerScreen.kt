package com.dk.tvplayer.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dk.tvplayer.player.EqPresets
import com.dk.tvplayer.player.EqualizerStore
import com.dk.tvplayer.ui.TvPlayerViewModel
import com.dk.tvplayer.ui.components.CurveThumbnail
import com.dk.tvplayer.ui.components.EqualizerDialog

/**
 * Full-screen Equalizer (Settings → Equalizer): every preset with a curve thumbnail, tap to use,
 * the eye icon hides/shows a preset in the quick-pick chips, the sliders icon opens the mixer,
 * and presets can be exported to / imported from a file. Works without anything playing — the
 * choice is applied as soon as audio starts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(viewModel: TvPlayerViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by EqualizerStore.state.collectAsState()
    val custom by EqualizerStore.customPresets.collectAsState()
    val hidden by EqualizerStore.hiddenPresets.collectAsState()
    var showMixer by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<String?>(null) }

    val presets = EqPresets.builtIn + custom

    fun apply() = viewModel.playerManager.applyEqualizerSettings()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val json = pendingExport
        pendingExport = null
        if (uri != null && json != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } != null
            }.getOrDefault(false)
            Toast.makeText(context, if (ok) "Presets exported" else "Couldn't write the file", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
            }.getOrNull()
            val count = text?.let { EqualizerStore.importJson(it) } ?: 0
            Toast.makeText(
                context,
                if (count > 0) "Imported $count preset(s)" else "No presets found in that file",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Equalizer") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "Close") }
                },
                actions = {
                    IconButton(onClick = { showMixer = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Sliders")
                    }
                    IconButton(onClick = {
                        // Export the preset currently in use.
                        val current = presets.firstOrNull { it.id == settings.presetId }
                            ?: com.dk.tvplayer.player.EqPreset("custom:Custom", "Custom", settings.preampDb, settings.bandsDb, false)
                        pendingExport = EqualizerStore.exportJson(listOf(current))
                        exportLauncher.launch("${current.name}.json")
                    }) { Icon(Icons.Default.FileUpload, contentDescription = "Export preset") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Show all") }, onClick = {
                                menuOpen = false
                                EqualizerStore.showAll()
                            })
                            DropdownMenuItem(text = { Text("Hide all") }, onClick = {
                                menuOpen = false
                                EqualizerStore.hideAll()
                            })
                            DropdownMenuItem(text = { Text("Export all") }, onClick = {
                                menuOpen = false
                                pendingExport = EqualizerStore.exportJson(presets)
                                exportLauncher.launch("dk_player_equalizer_presets.json")
                            })
                            DropdownMenuItem(text = { Text("Import all") }, onClick = {
                                menuOpen = false
                                importLauncher.launch(arrayOf("application/json", "*/*"))
                            })
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(presets, key = { it.id }) { preset ->
                val selected = preset.id == settings.presetId
                val isHidden = preset.id in hidden
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                        .clickable {
                            EqualizerStore.selectPreset(preset)
                            apply()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CurveThumbnail(preset.bandsDb, modifier = Modifier.size(width = 56.dp, height = 40.dp))
                        if (selected) {
                            Icon(Icons.Default.Check, contentDescription = "In use", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        preset.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).alpha(if (isHidden) 0.5f else 1f)
                    )
                    if (!selected) {
                        IconButton(onClick = { EqualizerStore.setHidden(preset.id, !isHidden) }) {
                            Icon(
                                if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isHidden) "Show preset" else "Hide preset"
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMixer) {
        EqualizerDialog(
            available = true,
            onDismiss = { showMixer = false },
            onChanged = { apply() }
        )
    }
}
