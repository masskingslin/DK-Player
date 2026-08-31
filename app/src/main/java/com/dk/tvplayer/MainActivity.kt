package com.dk.tvplayer

import android.Manifest
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dk.tvplayer.data.local.LocalAudioScanner
import com.dk.tvplayer.data.local.LocalVideoScanner
import com.dk.tvplayer.data.local.SettingsDataStore
import com.dk.tvplayer.data.local.TvDatabase
import com.dk.tvplayer.data.repository.TvRepository
import com.dk.tvplayer.player.TvExoPlayerManager
import com.dk.tvplayer.ui.PhoneAppRoot
import com.dk.tvplayer.ui.TvPlayerViewModel
import com.dk.tvplayer.ui.screens.TvMainScreen
import com.dk.tvplayer.ui.theme.dkColorScheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Tracks whether a video is actively on-screen, so PiP can be auto-entered on user-leave. */
object PipState {
    var isVideoPlayerActive: Boolean = false
}

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    private val settingsDataStore by lazy { SettingsDataStore(applicationContext) }

    private val viewModel: TvPlayerViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = TvDatabase.getDatabase(applicationContext)
                val videoScanner = LocalVideoScanner(applicationContext)
                val audioScanner = LocalAudioScanner(applicationContext)
                val repo = TvRepository(
                    channelDao = db.channelDao(),
                    epgDao = db.epgDao(),
                    historyDao = db.historyDao(),
                    streamDao = db.streamDao(),
                    playlistDao = db.playlistDao(),
                    videoScanner = videoScanner,
                    audioScanner = audioScanner
                )
                // Hardware-acceleration preference is read synchronously once at player
                // creation time (see TvExoPlayerManager doc comment on why it isn't live-toggle).
                val hwAccel = runBlocking { settingsDataStore.settingsFlow.first().hwAcceleration }
                val playerManager = TvExoPlayerManager(applicationContext, hwAccelerationEnabled = hwAccel)
                playerManager.initCast()
                return TvPlayerViewModel(repo, playerManager, settingsDataStore) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRequiredPermissions()

        setContent {
            val state by viewModel.uiState.collectAsState()
            val colorScheme = dkColorScheme(state.appSettings.themeMode, state.appSettings.themeSeedColor)

            MaterialTheme(colorScheme = colorScheme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    AppEntry(viewModel = viewModel)
                }
            }
        }
    }

    /** Called from the phone player screen's PiP button, and automatically via onUserLeaveHint. */
    fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && PipState.isVideoPlayerActive) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            runCatching { enterPictureInPictureMode(params) }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        // Player + background-audio settings decide whether playback continues in PiP/background;
        // no extra work needed here since the shared ExoPlayer instance lives in the ViewModel
        // and keeps playing regardless of the composition being torn down.
    }

    override fun onStop() {
        super.onStop()
        // Leave local playback running when in PiP, when casting (irrelevant to this
        // device's screen), or when the user opted in to background audio playback.
        if (!isInPictureInPictureMode) {
            val backgroundAudioEnabled = runBlocking { settingsDataStore.settingsFlow.first().backgroundAudioPlayback }
            val isCasting = viewModel.playerManager.isCastingFlow.value
            if (!backgroundAudioEnabled && !isCasting && viewModel.playerManager.exoPlayer.isPlaying) {
                viewModel.playerManager.exoPlayer.pause()
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }
}

@Composable
fun AppEntry(viewModel: TvPlayerViewModel) {
    val context = LocalContext.current
    val isTv = remember { DeviceType.isTelevision(context) }

    if (isTv) {
        TvMainScreen(viewModel = viewModel)
    } else {
        PhoneAppRoot(viewModel = viewModel)
    }
}
