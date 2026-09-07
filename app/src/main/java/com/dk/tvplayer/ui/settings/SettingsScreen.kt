@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dk.tvplayer.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dk.tvplayer.data.local.AppLanguage
import com.dk.tvplayer.data.local.AppThemeMode
import com.dk.tvplayer.data.local.SubtitleColorPreset
import com.dk.tvplayer.data.local.SubtitleTextSize
import com.dk.tvplayer.data.local.VideoResolutionCap
import com.dk.tvplayer.ui.TvPlayerViewModel
import com.dk.tvplayer.ui.theme.ThemeSeedPresets
import com.dk.tvplayer.util.ShareFileUtils
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: TvPlayerViewModel,
    onOpenDownloads: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val settings = state.appSettings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importStatus by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                val json = stream.bufferedReader().readText()
                viewModel.importSettingsBackup(json) { success ->
                    importStatus = if (success) "Settings imported successfully" else "Import failed — invalid backup file"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(text = "Player Configuration", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingToggleItem(
                    title = "Hardware Acceleration",
                    subtitle = "Use MediaCodec hardware decoders when available (applies on next app start)",
                    checked = settings.hwAcceleration,
                    onCheckedChange = { viewModel.setHwAcceleration(it) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                SettingToggleItem(
                    title = "Background Audio Playback",
                    subtitle = "Continue playing audio when app is minimized",
                    checked = settings.backgroundAudioPlayback,
                    onCheckedChange = { viewModel.setBackgroundAudioPlayback(it) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                SettingToggleItem(
                    title = "Auto Resume Playback",
                    subtitle = "Remember and restore playback positions across launches",
                    checked = settings.autoResumePlayback,
                    onCheckedChange = { viewModel.setAutoResumePlayback(it) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.padding(start = 12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Default Playback Speed", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${String.format("%.2f", settings.defaultPlaybackSpeed)}x",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Slider(
                    value = settings.defaultPlaybackSpeed,
                    onValueChange = { viewModel.setDefaultPlaybackSpeed(it) },
                    valueRange = 0.5f..2.0f,
                    steps = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                SettingToggleItem(
                    title = "Fast Seek",
                    subtitle = "Seek is faster but may be less precise (snaps to the nearest keyframe)",
                    checked = settings.fastSeekEnabled,
                    onCheckedChange = { viewModel.setFastSeekEnabled(it) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                SettingToggleItem(
                    title = "Match Display Frame Rate",
                    subtitle = "Switch the display's refresh rate to match the video's frame rate when possible",
                    checked = settings.matchDisplayFrameRate,
                    onCheckedChange = { viewModel.setMatchDisplayFrameRate(it) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                SettingToggleItem(
                    title = "Video Thumbnails",
                    subtitle = "Show a frame preview for local videos in lists",
                    checked = settings.videoThumbnailsEnabled,
                    onCheckedChange = { viewModel.setVideoThumbnailsEnabled(it) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.HighQuality, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.padding(start = 12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Preferred Video Resolution", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Maximum video quality for streams, when applicable, will be: ${settings.maxVideoResolution.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    VideoResolutionCap.entries.forEachIndexed { index, cap ->
                        SegmentedButton(
                            selected = settings.maxVideoResolution == cap,
                            onClick = { viewModel.setMaxVideoResolution(cap) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = VideoResolutionCap.entries.size)
                        ) {
                            Text(cap.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                SettingToggleItem(
                    title = "Show List Headers",
                    subtitle = "Split lists by A-Z headers when sorted by name",
                    checked = settings.showListHeaders,
                    onCheckedChange = { viewModel.setShowListHeaders(it) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                SettingToggleItem(
                    title = "Incognito Mode",
                    subtitle = "Don't save watch history or resume positions for this session",
                    checked = settings.incognitoMode,
                    onCheckedChange = { viewModel.setIncognitoMode(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Subtitles", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Text Size", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SubtitleTextSize.entries.forEachIndexed { index, size ->
                        SegmentedButton(
                            selected = settings.subtitleTextSize == size,
                            onClick = { viewModel.setSubtitleTextSize(size) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = SubtitleTextSize.entries.size)
                        ) {
                            Text(size.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Text Color", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SubtitleColorPreset.entries.forEach { preset ->
                        val isSelected = settings.subtitleColor == preset
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                color = Color(preset.colorArgb),
                                isSelected = isSelected,
                                onClick = { viewModel.setSubtitleColor(preset) }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(preset.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Theme Customization", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    AppThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = AppThemeMode.entries.size)
                        ) {
                            Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Accent Color", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ThemeSeedPresets.forEach { (name, colorLong) ->
                        val isSelected = settings.themeSeedColor == colorLong
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                color = Color(colorLong),
                                isSelected = isSelected,
                                onClick = { viewModel.setThemeSeedColor(colorLong) }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(name, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Language", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Changes date/number formatting and any translated text available. Most " +
                        "of this app's labels are currently English-only regardless of language.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                var showLanguageMenu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { showLanguageMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(settings.appLanguage.label)
                    }
                    DropdownMenu(expanded = showLanguageMenu, onDismissRequest = { showLanguageMenu = false }) {
                        AppLanguage.entries.forEach { language ->
                            DropdownMenuItem(
                                text = { Text(language.label) },
                                leadingIcon = {
                                    if (language == settings.appLanguage) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    viewModel.setAppLanguage(language)
                                    showLanguageMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Library", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenDownloads),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(text = "Downloads", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Manage offline downloads from your playlists",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Export / Import Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Back up player settings, theme, custom streams and playlists to a JSON file, or restore from a previous backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            val json = viewModel.exportSettingsBackup()
                            ShareFileUtils.shareTextFile(context, "dk_player_backup.json", "application/json", json)
                        }
                    }) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.padding(start = 4.dp))
                        Text("Export")
                    }
                    OutlinedButton(onClick = {
                        importStatus = null
                        importLauncher.launch(arrayOf("application/json", "*/*"))
                    }) {
                        Icon(Icons.Default.FileUpload, contentDescription = null)
                        Spacer(modifier = Modifier.padding(start = 4.dp))
                        Text("Import")
                    }
                }
                importStatus?.let { status ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun Box(color: Color, isSelected: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
        }
    }
}

@Composable
fun SettingToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
