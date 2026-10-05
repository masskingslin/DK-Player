package com.dk.tvplayer.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dk.tvplayer.data.local.QueueFormat
import com.dk.tvplayer.data.local.QueueInfoPosition
import com.dk.tvplayer.ui.TvPlayerViewModel

/** Settings → Casting (moved here from the long Settings page). */
@Composable
fun CastingSettingsScreen(viewModel: TvPlayerViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val settings = state.appSettings
    PrefScaffold("Casting", onBack) {
        PrefCheck(
            "Wireless Casting", "Show the cast button and allow casting to Chromecast devices",
            settings.wirelessCastingEnabled
        ) { viewModel.setWirelessCastingEnabled(it) }
        PrefCheck(
            "Audio Only", "Cast only audio, no video",
            settings.castAudioOnly, enabled = settings.wirelessCastingEnabled
        ) { viewModel.setCastAudioOnly(it) }
        Text(
            "Note: unlike VLC's own renderer, standard Chromecast can't transcode video away locally — " +
                "\"Audio Only\" tells the TV to show an audio-style screen, but doesn't reduce the data actually sent.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

private enum class AutoDialog { QueuePosition, QueueFormat }

/** Settings → Android Auto (moved here from the long Settings page). */
@Composable
fun AndroidAutoSettingsScreen(viewModel: TvPlayerViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val settings = state.appSettings
    var dialog by remember { mutableStateOf<AutoDialog?>(null) }

    PrefScaffold("Android Auto", onBack) {
        PrefSection("Interface")
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Title text size", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = settings.androidAutoTitleTextScale,
                onValueChange = { viewModel.setAndroidAutoTitleTextScale(it) },
                valueRange = 0.8f..1.4f
            )
            Text("Subtitle text size", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = settings.androidAutoSubtitleTextScale,
                onValueChange = { viewModel.setAndroidAutoSubtitleTextScale(it) },
                valueRange = 0.8f..1.4f
            )
        }
        PrefRow("Queue information", settings.androidAutoQueueInfoPosition.label) { dialog = AutoDialog.QueuePosition }
        PrefRow("Queue format", settings.androidAutoQueueFormat.label) { dialog = AutoDialog.QueueFormat }

        PrefSection("Controls")
        PrefCheck(
            "Use the global playback speed",
            "Use the playback speed set globally for all the tracks. Individual tracks' playback speed will be ignored.",
            settings.androidAutoUseGlobalPlaybackSpeed
        ) { viewModel.setAndroidAutoUseGlobalPlaybackSpeed(it) }
        PrefCheck(
            "Android Auto playback speed", "Show speed control in the overflow menu",
            settings.androidAutoPlaybackSpeedControlEnabled
        ) { viewModel.setAndroidAutoPlaybackSpeedControlEnabled(it) }
        PrefCheck(
            "Android Auto seek buttons",
            "Show rewind and fast forward in the overflow menu. Try holding steering wheel previous and next buttons before enabling.",
            settings.androidAutoSeekButtonsEnabled
        ) { viewModel.setAndroidAutoSeekButtonsEnabled(it) }
    }

    when (dialog) {
        AutoDialog.QueuePosition -> ChoiceDialog(
            "Queue information", QueueInfoPosition.entries, { it.label }, settings.androidAutoQueueInfoPosition,
            onPick = { viewModel.setAndroidAutoQueueInfoPosition(it); dialog = null }, onDismiss = { dialog = null }
        )
        AutoDialog.QueueFormat -> ChoiceDialog(
            "Queue format", QueueFormat.entries, { it.label }, settings.androidAutoQueueFormat,
            onPick = { viewModel.setAndroidAutoQueueFormat(it); dialog = null }, onDismiss = { dialog = null }
        )
        null -> Unit
    }
}
