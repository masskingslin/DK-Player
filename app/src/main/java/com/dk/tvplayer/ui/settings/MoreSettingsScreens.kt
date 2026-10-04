package com.dk.tvplayer.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dk.tvplayer.ui.TvPlayerViewModel
import com.dk.tvplayer.util.BackgroundMode
import com.dk.tvplayer.util.HardwareAcceleration
import com.dk.tvplayer.util.MeteredAction
import com.dk.tvplayer.util.PlaybackPrefs
import com.dk.tvplayer.util.ReplayGainMode
import com.dk.tvplayer.util.ResumeAudio
import com.dk.tvplayer.util.SubtitleLanguages
import com.dk.tvplayer.util.VideoOrientation

@Composable
private fun NumberDialog(
    title: String,
    label: String,
    initial: Int,
    allowNegative: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember { mutableStateOf(initial.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v ->
                    text = v.filterIndexed { i, c -> c.isDigit() || (allowNegative && i == 0 && c == '-') }.take(4)
                },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.toIntOrNull() ?: initial) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------------------------------------------------------------------------------------
// Audio
// ---------------------------------------------------------------------------------------

private enum class AudioDialog { Language, ResumeAudio, RgMode, RgPreamp, RgDefault }

@Composable
fun AudioSettingsScreen(viewModel: TvPlayerViewModel, onBack: () -> Unit) {
    val resumeAfterCall by PlaybackPrefs.resumeAfterCall.flow.collectAsState()
    val stopOnSwipe by PlaybackPrefs.stopOnSwipe.flow.collectAsState()
    val passthrough by PlaybackPrefs.digitalPassthrough.flow.collectAsState()
    val language by PlaybackPrefs.preferredAudioLanguage.flow.collectAsState()
    val resumeAudio by PlaybackPrefs.resumeAudio.flow.collectAsState()
    val detectHeadset by PlaybackPrefs.detectHeadset.flow.collectAsState()
    val resumeOnHeadset by PlaybackPrefs.resumeOnHeadset.flow.collectAsState()
    val ignoreButtons by PlaybackPrefs.ignoreHeadsetButtons.flow.collectAsState()
    val rgEnabled by PlaybackPrefs.replayGainEnabled.flow.collectAsState()
    val rgMode by PlaybackPrefs.replayGainMode.flow.collectAsState()
    val rgPreamp by PlaybackPrefs.replayPreampDb.flow.collectAsState()
    val rgDefault by PlaybackPrefs.defaultReplayGainDb.flow.collectAsState()
    val peak by PlaybackPrefs.peakProtection.flow.collectAsState()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<AudioDialog?>(null) }

    PrefScaffold("Audio", onBack) {
        PrefCheck("Resume playback after a call", "Stay in pause otherwise", resumeAfterCall) {
            PlaybackPrefs.resumeAfterCall.set(it)
        }
        PrefCheck("Stop on application swipe", "Stop playback when application is dismissed", stopOnSwipe) {
            PlaybackPrefs.stopOnSwipe.set(it)
        }
        PrefCheck(
            "Digital audio output (passthrough)",
            (if (passthrough) "Audio Digital Output enabled" else "Audio Digital Output disabled") +
                " — restart the app to apply",
            passthrough
        ) {
            PlaybackPrefs.digitalPassthrough.set(it)
            Toast.makeText(context, "Restart the app to apply", Toast.LENGTH_SHORT).show()
        }
        PrefRow(
            "Preferred audio language",
            SubtitleLanguages.firstOrNull { it.second == language }?.first ?: language
        ) { dialog = AudioDialog.Language }
        PrefRow("Resume played audio", resumeAudio.label) { dialog = AudioDialog.ResumeAudio }

        PrefSection("Headset")
        PrefCheck("Detect headset", "Detect headset insertion and removal", detectHeadset) {
            PlaybackPrefs.detectHeadset.set(it)
        }
        PrefCheck("Resume on headset insertion", "Pause otherwise", resumeOnHeadset, enabled = detectHeadset) {
            PlaybackPrefs.resumeOnHeadset.set(it)
        }
        PrefCheck(
            "Ignore headset media button presses",
            "Useful, for instance, if you are using a headset with broken physical buttons",
            ignoreButtons
        ) { PlaybackPrefs.ignoreHeadsetButtons.set(it) }

        PrefSection("Replay Gain")
        PrefCheck(
            "Enable replay gain", "Streams without replay gain information may be quieter due to the default gain",
            rgEnabled
        ) {
            PlaybackPrefs.replayGainEnabled.set(it)
            viewModel.playerManager.refreshReplayGain()
        }
        PrefRow(
            "Replay gain mode",
            "Track mode plays streams with replay gain information at the same loudness. " +
                "Album mode preserves relative stream loudness on the same album.\n${rgMode.label}",
            enabled = rgEnabled
        ) { dialog = AudioDialog.RgMode }
        PrefRow(
            "Replay preamp",
            "Changes the default target level (89 dB) for streams with replay gain information\nCurrent: $rgPreamp dB",
            enabled = rgEnabled
        ) { dialog = AudioDialog.RgPreamp }
        PrefRow(
            "Default replay gain",
            "Applies to streams without replay gain information. Set to 0 to disable\nCurrent: $rgDefault dB",
            enabled = rgEnabled
        ) { dialog = AudioDialog.RgDefault }
        PrefCheck("Peak protection", "Protect against sound clipping", peak, enabled = rgEnabled) {
            PlaybackPrefs.peakProtection.set(it)
            viewModel.playerManager.refreshReplayGain()
        }

        PrefSection("Advanced")
        PrefRow(
            "MIDI SoundFont",
            "Not available: DK Player's audio engine can't play MIDI files or load SoundFont files",
            enabled = false
        )
    }

    when (dialog) {
        AudioDialog.Language -> ChoiceDialog(
            "Preferred audio language", SubtitleLanguages, { it.first },
            SubtitleLanguages.firstOrNull { it.second == language } ?: SubtitleLanguages.first(),
            onPick = { PlaybackPrefs.preferredAudioLanguage.set(it.second); dialog = null }, onDismiss = { dialog = null }
        )
        AudioDialog.ResumeAudio -> ChoiceDialog(
            "Resume played audio", ResumeAudio.entries, { it.label }, resumeAudio,
            onPick = { PlaybackPrefs.resumeAudio.set(it); dialog = null }, onDismiss = { dialog = null }
        )
        AudioDialog.RgMode -> ChoiceDialog(
            "Replay gain mode", ReplayGainMode.entries, { it.label }, rgMode,
            onPick = {
                PlaybackPrefs.replayGainMode.set(it)
                viewModel.playerManager.refreshReplayGain()
                dialog = null
            }, onDismiss = { dialog = null }
        )
        AudioDialog.RgPreamp -> NumberDialog(
            "Replay preamp", "Target level in dB (default 89)", rgPreamp, allowNegative = false,
            onDismiss = { dialog = null },
            onConfirm = {
                PlaybackPrefs.replayPreampDb.set(it.coerceIn(60, 120))
                viewModel.playerManager.refreshReplayGain()
                dialog = null
            }
        )
        AudioDialog.RgDefault -> NumberDialog(
            "Default replay gain", "Gain in dB (0 = off)", rgDefault, allowNegative = true,
            onDismiss = { dialog = null },
            onConfirm = {
                PlaybackPrefs.defaultReplayGainDb.set(it.coerceIn(-30, 15))
                viewModel.playerManager.refreshReplayGain()
                dialog = null
            }
        )
        null -> Unit
    }
}

// ---------------------------------------------------------------------------------------
// General (media library, video, network, permissions, history)
// ---------------------------------------------------------------------------------------

private enum class GeneralDialog { Background, Hardware, Orientation, Metered }

@Composable
fun GeneralSettingsScreen(
    viewModel: TvPlayerViewModel,
    onBack: () -> Unit,
    onOpenFolders: () -> Unit,
    onOpenPermissions: () -> Unit
) {
    val context = LocalContext.current
    val excluded by PlaybackPrefs.excludedFolders.flow.collectAsState()
    val autoRescan by PlaybackPrefs.autoRescan.flow.collectAsState()
    val background by PlaybackPrefs.backgroundMode.flow.collectAsState()
    val hardware by PlaybackPrefs.hardwareAcceleration.flow.collectAsState()
    val orientation by PlaybackPrefs.videoOrientation.flow.collectAsState()
    val metered by PlaybackPrefs.meteredAction.flow.collectAsState()
    val saveHistory by PlaybackPrefs.savePlaybackHistory.flow.collectAsState()
    val videoQueue by PlaybackPrefs.videoQueueHistory.flow.collectAsState()
    val audioQueue by PlaybackPrefs.audioQueueHistory.flow.collectAsState()
    var dialog by remember { mutableStateOf<GeneralDialog?>(null) }

    PrefScaffold("Settings", onBack) {
        PrefSection("Media library")
        PrefRow(
            "Media library folders",
            if (excluded.isEmpty()) "Select directories to include in the media library"
            else "${excluded.size} folder(s) left out of the media library",
            onClick = onOpenFolders
        )
        PrefCheck(
            "Auto rescan", "Automatically scan device for new or deleted media at application startup",
            autoRescan
        ) { PlaybackPrefs.autoRescan.set(it) }
        PrefRow("Rescan now", "Look for new or deleted videos and audio right away") {
            viewModel.rescanMediaLibrary()
            Toast.makeText(context, "Scanning…", Toast.LENGTH_SHORT).show()
        }

        PrefSection("Video")
        PrefRow(
            "Background/PiP mode",
            "Select DK Player behaviour when you switch to another application from video playback\n${background.label}"
        ) { dialog = GeneralDialog.Background }
        PrefRow(
            "Hardware Acceleration",
            "Disabled: better stability\nDecoding: may improve performance\nFull: may improve performance further\n" +
                "Current: ${hardware.label} (restart the app to apply)"
        ) { dialog = GeneralDialog.Hardware }
        PrefRow("Video screen orientation", orientation.label) { dialog = GeneralDialog.Orientation }

        PrefSection("Network")
        PrefRow("Action for streams when the connection is metered", metered.label) { dialog = GeneralDialog.Metered }

        PrefSection("Permissions")
        PrefRow("Permissions", "List of all the permissions", onClick = onOpenPermissions)

        PrefSection("History")
        PrefCheck("Playback history", "Save all media played in History section", saveHistory) {
            PlaybackPrefs.savePlaybackHistory.set(it)
        }
        PrefCheck(
            "Video play queue history", "Allow saving the video play queue to resume later",
            videoQueue, enabled = saveHistory
        ) { PlaybackPrefs.videoQueueHistory.set(it) }
        PrefCheck(
            "Audio play queue history", "Allow saving the audio play queue to resume later",
            audioQueue, enabled = saveHistory
        ) { PlaybackPrefs.audioQueueHistory.set(it) }
    }

    when (dialog) {
        GeneralDialog.Background -> ChoiceDialog(
            "Background/PiP mode", BackgroundMode.entries, { "${it.label}\n${it.description}" }, background,
            onPick = {
                PlaybackPrefs.backgroundMode.set(it)
                viewModel.refreshBackgroundPlayback()
                dialog = null
            }, onDismiss = { dialog = null }
        )
        GeneralDialog.Hardware -> ChoiceDialog(
            "Hardware Acceleration", HardwareAcceleration.entries, { "${it.label}\n${it.description}" }, hardware,
            onPick = {
                PlaybackPrefs.hardwareAcceleration.set(it)
                viewModel.setHwAcceleration(it != HardwareAcceleration.DISABLED)
                Toast.makeText(context, "Restart the app to apply", Toast.LENGTH_SHORT).show()
                dialog = null
            }, onDismiss = { dialog = null }
        )
        GeneralDialog.Orientation -> ChoiceDialog(
            "Video screen orientation", VideoOrientation.entries, { it.label }, orientation,
            onPick = { PlaybackPrefs.videoOrientation.set(it); dialog = null }, onDismiss = { dialog = null }
        )
        GeneralDialog.Metered -> ChoiceDialog(
            "Action for streams when the connection is metered", MeteredAction.entries, { it.label }, metered,
            onPick = { PlaybackPrefs.meteredAction.set(it); dialog = null }, onDismiss = { dialog = null }
        )
        null -> Unit
    }
}

// ---------------------------------------------------------------------------------------
// Media library folders
// ---------------------------------------------------------------------------------------

@Composable
fun MediaFoldersScreen(viewModel: TvPlayerViewModel, onBack: () -> Unit) {
    val folders by viewModel.mediaFolders.collectAsState()
    val excluded by PlaybackPrefs.excludedFolders.flow.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (viewModel.mediaFolders.value.isEmpty()) viewModel.rescanMediaLibrary()
    }

    PrefScaffold("Media library folders", onBack) {
        Text(
            "Untick a folder to leave its videos and audio out of the media library.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        if (folders.isEmpty()) {
            Text(
                "No media folders found yet.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        folders.forEach { (path, count) ->
            val included = path !in excluded
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        PlaybackPrefs.excludedFolders.set(if (included) excluded + path else excluded - path)
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(path.substringAfterLast('/').ifBlank { path }, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "$path · $count item(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Checkbox(
                    checked = included,
                    onCheckedChange = {
                        PlaybackPrefs.excludedFolders.set(if (included) excluded + path else excluded - path)
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Permissions
// ---------------------------------------------------------------------------------------

private data class PermissionInfo(
    val title: String,
    val description: String,
    val granted: Boolean,
    val action: () -> Unit
)

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }

    // Re-check when coming back from a system settings page.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val requestLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh++
    }

    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun appSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // `refresh` is read so the list rebuilds after returning from settings.
    val items = remember(refresh) {
        val list = mutableListOf<PermissionInfo>()
        val mediaPermissions = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        list += PermissionInfo(
            "Media access", "Find and play the videos and audio stored on this device",
            mediaPermissions.all { granted(it) }
        ) { requestLauncher.launch(mediaPermissions) }
        if (Build.VERSION.SDK_INT >= 33) {
            list += PermissionInfo(
                "Notifications", "Playback controls and the Remote access status",
                granted(Manifest.permission.POST_NOTIFICATIONS)
            ) { requestLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }
        }
        list += PermissionInfo(
            "Display over other apps", "Pop-up player on top of other apps",
            Settings.canDrawOverlays(context)
        ) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        list += PermissionInfo(
            "Modify system settings", "Set a video or audio file as your ringtone",
            Settings.System.canWrite(context)
        ) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        list += PermissionInfo(
            "Set wallpaper", "Cover on the lock screen while media plays (granted automatically at install)",
            granted(Manifest.permission.SET_WALLPAPER)
        ) { appSettings() }
        list += PermissionInfo(
            "Internet and network", "Streams, playlists, Remote access (granted automatically at install)",
            granted(Manifest.permission.INTERNET)
        ) { appSettings() }
        list
    }

    PrefScaffold("Permissions", onBack) {
        items.forEach { info ->
            PrefRow(
                title = info.title,
                subtitle = info.description + "\n" + if (info.granted) "Allowed" else "Not allowed — tap to allow",
                onClick = info.action
            )
        }
        PrefRow("All app permissions", "Open Android's settings page for DK Player") { appSettings() }
    }
}
