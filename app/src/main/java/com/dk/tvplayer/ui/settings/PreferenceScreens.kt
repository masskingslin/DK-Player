package com.dk.tvplayer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dk.tvplayer.data.local.AppLanguage
import com.dk.tvplayer.data.local.AppThemeMode
import com.dk.tvplayer.data.local.VideoResolutionCap
import com.dk.tvplayer.ui.TvPlayerViewModel
import com.dk.tvplayer.ui.components.SleepTimerDialog
import com.dk.tvplayer.ui.components.StyledSubtitleText
import com.dk.tvplayer.util.LockscreenCover
import com.dk.tvplayer.util.OutlineSize
import com.dk.tvplayer.util.SubtitleEncoding
import com.dk.tvplayer.util.SubtitleLanguages
import com.dk.tvplayer.util.SubtitlePrefs
import com.dk.tvplayer.util.SubtitleSize
import com.dk.tvplayer.util.SubtitleStyle
import com.dk.tvplayer.util.TitleEllipsize
import com.dk.tvplayer.util.UiPrefs
import com.dk.tvplayer.util.showSubtitlePreview
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------
// Shared building blocks (VLC-style rows)
// ---------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PrefScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun PrefSection(label: String) {
    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
internal fun PrefRow(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

@Composable
internal fun PrefCheck(title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    PrefRow(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        onClick = { onChange(!checked) },
        trailing = { Checkbox(checked = checked, onCheckedChange = { onChange(it) }, enabled = enabled) }
    )
}

@Composable
internal fun PrefColor(title: String, color: Int, onClick: () -> Unit) {
    PrefRow(
        title = title,
        onClick = onClick,
        trailing = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        }
    )
}

@Composable
internal fun PrefSlider(title: String, value: Float, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${(value * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
        }
        Slider(value = value, onValueChange = onChange, valueRange = 0f..1f)
    }
}

@Composable
internal fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    label: (T) -> String,
    selected: T,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onPick(option) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == selected, onClick = { onPick(option) })
                        Spacer(Modifier.width(8.dp))
                        Text(label(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private val ColorChoices = listOf(
    0xFFFFFFFF, 0xFFFFEB3B, 0xFFFFC107, 0xFFFF9800, 0xFFFF5252, 0xFFFF4081,
    0xFFE040FB, 0xFF7C4DFF, 0xFF448AFF, 0xFF18FFFF, 0xFF69F0AE, 0xFFB2FF59,
    0xFFBDBDBD, 0xFF757575, 0xFF303030, 0xFF000000
).map { it.toInt() }

@Composable
internal fun ColorPickerDialog(title: String, selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val opaque = selected or 0xFF000000.toInt()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ColorChoices.chunked(4).forEach { rowColors ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        rowColors.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .border(
                                        width = if (c == opaque) 3.dp else 1.dp,
                                        color = if (c == opaque) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .clickable { onPick(c) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------------------------------------------------------------------------------------
// Interface
// ---------------------------------------------------------------------------------------

private enum class InterfaceDialog { Theme, Language, Ellipsize, Sleep, CoverLock }

private fun themeLabel(mode: AppThemeMode) = when (mode) {
    AppThemeMode.SYSTEM -> "System default"
    AppThemeMode.LIGHT -> "Light theme"
    AppThemeMode.DARK -> "Dark theme"
    AppThemeMode.AMOLED -> "Black theme"
}

@Composable
fun InterfaceSettingsScreen(viewModel: TvPlayerViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val settings = state.appSettings
    val ellipsize by UiPrefs.titleEllipsize.collectAsState()
    val forceTv by UiPrefs.forceTvInterface.collectAsState()
    val persistentIncognito by UiPrefs.persistentIncognito.collectAsState()
    val seenMarker by UiPrefs.showSeenMarker.collectAsState()
    val notifSeek by UiPrefs.seekButtonsInNotification.collectAsState()
    val showMissing by UiPrefs.showMissingMedia.collectAsState()
    val lastTip by UiPrefs.showLastPlaylistTip.collectAsState()
    val coverLock by UiPrefs.mediaCoverOnLockscreen.collectAsState()
    val sleepRemaining by viewModel.playerManager.sleepTimerRemainingSecFlow.collectAsState()
    var dialog by remember { mutableStateOf<InterfaceDialog?>(null) }

    PrefScaffold("Interface", onBack) {
        PrefRow("DayNight mode", themeLabel(settings.themeMode)) { dialog = InterfaceDialog.Theme }
        PrefCheck(
            "Android TV interface", "Change UI to TV adapted theme (restart the app to apply)",
            forceTv
        ) { UiPrefs.setForceTvInterface(it) }
        PrefRow("Set locale", settings.appLanguage.label) { dialog = InterfaceDialog.Language }
        PrefRow("Single line list title ellipsize", ellipsize.label) { dialog = InterfaceDialog.Ellipsize }
        PrefCheck("Show headers", "Split lists by headers depending on the sort type", settings.showListHeaders) {
            viewModel.setShowListHeaders(it)
        }
        PrefCheck(
            "Show missing media", "Show history items even if their file is no longer on the device",
            showMissing
        ) { UiPrefs.setShowMissingMedia(it) }
        PrefRow(
            "Sleep timer",
            sleepRemaining?.let { "Pausing in ${it / 60}m ${it % 60}s" } ?: "Disabled"
        ) { dialog = InterfaceDialog.Sleep }
        PrefCheck("Incognito mode", null, settings.incognitoMode) { viewModel.setIncognitoMode(it) }
        PrefCheck(
            "Persistent incognito mode", "Keep the incognito mode enabled even if the app is restarted",
            persistentIncognito, enabled = settings.incognitoMode
        ) { UiPrefs.setPersistentIncognito(it) }

        PrefSection("Video")
        PrefCheck("Show seen video marker", "Mark a video as seen when you play it until the end", seenMarker) {
            UiPrefs.setShowSeenMarker(it)
        }
        PrefCheck("Video thumbnails", "Show video thumbnails in lists", settings.videoThumbnailsEnabled) {
            viewModel.setVideoThumbnailsEnabled(it)
        }

        PrefSection("Audio")
        PrefCheck(
            "Show last playlist tip", "Shows a tip helping you to resume playback on app start",
            lastTip
        ) { UiPrefs.setShowLastPlaylistTip(it) }
        PrefCheck(
            "Media cover on Lockscreen",
            "While media plays, set its cover (audio art or a video frame) as your lock screen wallpaper; " +
                "restored when playback stops",
            coverLock
        ) { enable ->
            if (enable) {
                dialog = InterfaceDialog.CoverLock
            } else {
                UiPrefs.setMediaCoverOnLockscreen(false)
                LockscreenCover.restoreNow()
            }
        }
        PrefCheck(
            "Seek buttons in notification panel", "Show rewind and fast forward buttons in compact media controls",
            notifSeek
        ) {
            UiPrefs.setSeekButtonsInNotification(it)
            viewModel.playerManager.setAndroidAutoSeekButtonsEnabled(it || settings.androidAutoSeekButtonsEnabled)
        }
    }

    when (dialog) {
        InterfaceDialog.Theme -> ChoiceDialog(
            "DayNight mode", AppThemeMode.entries, ::themeLabel, settings.themeMode,
            onPick = { viewModel.setThemeMode(it); dialog = null }, onDismiss = { dialog = null }
        )
        InterfaceDialog.Language -> ChoiceDialog(
            "Set locale", AppLanguage.entries, { it.label }, settings.appLanguage,
            onPick = { viewModel.setAppLanguage(it); dialog = null }, onDismiss = { dialog = null }
        )
        InterfaceDialog.Ellipsize -> ChoiceDialog(
            "Single line list title ellipsize", TitleEllipsize.entries, { it.label }, ellipsize,
            onPick = { UiPrefs.setTitleEllipsize(it); dialog = null }, onDismiss = { dialog = null }
        )
        InterfaceDialog.CoverLock -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Cover on the lock screen?") },
            text = {
                Text(
                    "While audio or video plays, DK Player will replace your lock screen wallpaper with that " +
                        "media's cover (a video's cover is a frame from it). When playback stops, your original " +
                        "wallpaper is put back.\n\nIf Android doesn't let DK Player read your current lock screen " +
                        "wallpaper, the lock screen falls back to your home screen wallpaper instead — so a " +
                        "separate custom lock wallpaper or a live wallpaper may need to be set again."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    UiPrefs.setMediaCoverOnLockscreen(true)
                    LockscreenCover.update(viewModel.playerManager.currentMediaUrl)
                    dialog = null
                }) { Text("Turn on") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
        )
        InterfaceDialog.Sleep -> SleepTimerDialog(
            activeRemainingSec = sleepRemaining,
            onDismiss = { dialog = null },
            onStart = { minutes -> viewModel.playerManager.startSleepTimer(minutes); dialog = null },
            onCancel = { viewModel.playerManager.cancelSleepTimer(); dialog = null }
        )
        null -> Unit
    }
}

// ---------------------------------------------------------------------------------------
// Video
// ---------------------------------------------------------------------------------------

@Composable
fun VideoSettingsScreen(viewModel: TvPlayerViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val settings = state.appSettings
    val customPip by UiPrefs.useCustomPipPopup.collectAsState()
    val restore by UiPrefs.restoreVideoFromBackground.collectAsState()
    val preferClone by UiPrefs.preferClone.collectAsState()
    var showResolution by remember { mutableStateOf(false) }

    PrefScaffold("Video", onBack) {
        PrefCheck("Always use fast seek", "Seek is faster but may be less precise", settings.fastSeekEnabled) {
            viewModel.setFastSeekEnabled(it)
        }
        PrefCheck(
            "Use custom Picture-in-Picture popup",
            "Use custom Picture-in-Picture resizable popup (off: Android's own Picture-in-Picture)",
            customPip
        ) { UiPrefs.setUseCustomPipPopup(it) }
        PrefCheck(
            "Restore video from background", "Restore video from background when reopening DK Player",
            restore
        ) { UiPrefs.setRestoreVideoFromBackground(it) }
        PrefCheck(
            "Match Display Frame Rate",
            "Match display refresh rate to media frame rate. For example, a 24p film should play at 24p",
            settings.matchDisplayFrameRate
        ) { viewModel.setMatchDisplayFrameRate(it) }
        PrefRow(
            "Preferred video resolution",
            "Maximum video quality for streams, when applicable, will be: ${settings.maxVideoResolution.label}"
        ) { showResolution = true }

        PrefSection("Secondary display")
        PrefRow("Settings when secondary displays are connected (HDMI/Chromecast)")
        PrefCheck(
            "Prefer clone",
            "Clone the device screen without remote control. Off: the video plays on the other screen and this phone becomes the remote control",
            preferClone
        ) { UiPrefs.setPreferClone(it) }
    }

    if (showResolution) {
        ChoiceDialog(
            "Preferred video resolution", VideoResolutionCap.entries, { it.label }, settings.maxVideoResolution,
            onPick = { viewModel.setMaxVideoResolution(it); showResolution = false },
            onDismiss = { showResolution = false }
        )
    }
}

// ---------------------------------------------------------------------------------------
// Subtitles
// ---------------------------------------------------------------------------------------

private enum class SubtitleDialog { Presets, Encoding, Language, Size, TextColor, BgColor, ShadowColor, OutlineColor, OutlineSize }

@Composable
fun SubtitlesSettingsScreen(onBack: () -> Unit) {
    val style by SubtitlePrefs.style.collectAsState()
    val autoLoad by SubtitlePrefs.autoLoad.collectAsState()
    val encoding by SubtitlePrefs.encoding.collectAsState()
    val language by SubtitlePrefs.language.collectAsState()
    var dialog by remember { mutableStateOf<SubtitleDialog?>(null) }

    PrefScaffold("Subtitles", onBack) {
        // Live preview, drawn by the same renderer the player uses.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(Color(0xFF26323A))
        ) {
            StyledSubtitleText(
                text = "This is how subtitles will look",
                style = style,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 12.dp)
            )
        }

        PrefRow("Subtitles presets") { dialog = SubtitleDialog.Presets }
        PrefCheck(
            "Auto load subtitles", "Use subtitle files stored next to a local video (same name, .srt/.vtt/.ass)",
            autoLoad
        ) { SubtitlePrefs.setAutoLoad(it) }
        PrefRow("Subtitle text encoding", "For loaded subtitle files: ${encoding.label}") { dialog = SubtitleDialog.Encoding }
        PrefRow(
            "Preferred subtitle language",
            SubtitleLanguages.firstOrNull { it.second == language }?.first ?: language
        ) { dialog = SubtitleDialog.Language }

        PrefSection("Subtitles font style")
        PrefRow("Subtitles Size", style.size.label) { dialog = SubtitleDialog.Size }
        PrefCheck("Bold subtitles", null, style.bold) { v -> SubtitlePrefs.update { it.copy(bold = v) } }
        PrefColor("Colour", style.color) { dialog = SubtitleDialog.TextColor }
        PrefSlider("Opacity", style.opacity) { v -> SubtitlePrefs.update { it.copy(opacity = v) } }

        PrefSection("Subtitles Background")
        PrefCheck("Subtitles Background", null, style.backgroundEnabled) { v ->
            SubtitlePrefs.update { it.copy(backgroundEnabled = v) }
        }
        if (style.backgroundEnabled) {
            PrefColor("Colour", style.backgroundColor) { dialog = SubtitleDialog.BgColor }
            PrefSlider("Opacity", style.backgroundOpacity) { v -> SubtitlePrefs.update { it.copy(backgroundOpacity = v) } }
        }

        PrefSection("Subtitles shadow")
        PrefCheck("Subtitles shadow", null, style.shadowEnabled) { v -> SubtitlePrefs.update { it.copy(shadowEnabled = v) } }
        if (style.shadowEnabled) {
            PrefColor("Colour", style.shadowColor) { dialog = SubtitleDialog.ShadowColor }
            PrefSlider("Opacity", style.shadowOpacity) { v -> SubtitlePrefs.update { it.copy(shadowOpacity = v) } }
        }

        PrefSection("Subtitles outline")
        PrefCheck("Subtitles outline", null, style.outlineEnabled) { v -> SubtitlePrefs.update { it.copy(outlineEnabled = v) } }
        if (style.outlineEnabled) {
            PrefRow("Size", style.outlineSize.label) { dialog = SubtitleDialog.OutlineSize }
            PrefColor("Colour", style.outlineColor) { dialog = SubtitleDialog.OutlineColor }
            PrefSlider("Opacity", style.outlineOpacity) { v -> SubtitlePrefs.update { it.copy(outlineOpacity = v) } }
        }
    }

    when (dialog) {
        SubtitleDialog.Presets -> ChoiceDialog(
            "Subtitles presets", SubtitlePrefs.presets, { it.name },
            selected = SubtitlePrefs.presets.firstOrNull { it.style == style } ?: SubtitlePrefs.presets.first(),
            onPick = { preset -> SubtitlePrefs.update { preset.style }; dialog = null },
            onDismiss = { dialog = null }
        )
        SubtitleDialog.Encoding -> ChoiceDialog(
            "Subtitle text encoding", SubtitleEncoding.entries, { it.label }, encoding,
            onPick = { SubtitlePrefs.setEncoding(it); dialog = null }, onDismiss = { dialog = null }
        )
        SubtitleDialog.Language -> ChoiceDialog(
            "Preferred subtitle language", SubtitleLanguages, { it.first },
            SubtitleLanguages.firstOrNull { it.second == language } ?: SubtitleLanguages.first(),
            onPick = { SubtitlePrefs.setLanguage(it.second); dialog = null }, onDismiss = { dialog = null }
        )
        SubtitleDialog.Size -> ChoiceDialog(
            "Subtitles Size", SubtitleSize.entries, { it.label }, style.size,
            onPick = { size -> SubtitlePrefs.update { it.copy(size = size) }; dialog = null }, onDismiss = { dialog = null }
        )
        SubtitleDialog.TextColor -> ColorPickerDialog("Colour", style.color,
            onPick = { c -> SubtitlePrefs.update { it.copy(color = c) }; dialog = null }, onDismiss = { dialog = null })
        SubtitleDialog.BgColor -> ColorPickerDialog("Background colour", style.backgroundColor,
            onPick = { c -> SubtitlePrefs.update { it.copy(backgroundColor = c) }; dialog = null }, onDismiss = { dialog = null })
        SubtitleDialog.ShadowColor -> ColorPickerDialog("Shadow colour", style.shadowColor,
            onPick = { c -> SubtitlePrefs.update { it.copy(shadowColor = c) }; dialog = null }, onDismiss = { dialog = null })
        SubtitleDialog.OutlineSize -> ChoiceDialog(
            "Outline size", OutlineSize.entries, { it.label }, style.outlineSize,
            onPick = { size -> SubtitlePrefs.update { it.copy(outlineSize = size) }; dialog = null }, onDismiss = { dialog = null }
        )
        SubtitleDialog.OutlineColor -> ColorPickerDialog("Outline colour", style.outlineColor,
            onPick = { c -> SubtitlePrefs.update { it.copy(outlineColor = c) }; dialog = null }, onDismiss = { dialog = null })
        null -> Unit
    }
}
