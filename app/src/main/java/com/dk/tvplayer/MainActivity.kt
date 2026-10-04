package com.dk.tvplayer

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dk.tvplayer.data.local.LocalAudioScanner
import com.dk.tvplayer.data.local.LocalVideoScanner
import com.dk.tvplayer.data.local.TvDatabase
import com.dk.tvplayer.data.repository.TvRepository
import com.dk.tvplayer.player.PlaybackService
import com.dk.tvplayer.ui.PhoneAppRoot
import com.dk.tvplayer.ui.TvPlayerViewModel
import com.dk.tvplayer.ui.screens.TvMainScreen
import com.dk.tvplayer.ui.theme.dkColorScheme
import com.dk.tvplayer.ui.theme.dkShapes
import com.dk.tvplayer.ui.theme.dkTypography
import com.dk.tvplayer.util.EXTRA_PLAY_PATH
import com.dk.tvplayer.util.EXTRA_PLAY_TITLE
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Tracks whether a video is actively on-screen, so PiP can be auto-entered on user-leave. */
object PipState {
    var isVideoPlayerActive: Boolean = false
}

/**
 * Set by FloatingPlayerService while its overlay window is showing, so MainActivity's
 * onStop() knows not to pause local playback just because the app was sent to the
 * background — that's the whole point of the pop-up: video keeps playing while another
 * app is in front.
 */
object FloatingPlayerState {
    var isActive: Boolean = false
}

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    private val app get() = application as DkPlayerApplication

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
                    audioScanner = audioScanner,
                    videoGroupDao = db.videoGroupDao(),
                    bookmarkDao = db.bookmarkDao()
                )
                // The player itself now lives on DkPlayerApplication (not created here) so
                // that PlaybackService can share the exact same instance for background
                // audio + lock-screen controls, surviving beyond this ViewModel's lifecycle.
                return TvPlayerViewModel(repo, app.playerManager, app.settingsDataStore) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRequiredPermissions()
        handleLaunchIntent(intent)
        // Remote access was left on: bring the server back up with the app.
        if (com.dk.tvplayer.remote.RemoteAccessConfig.enabled.value) {
            runCatching { com.dk.tvplayer.remote.RemoteAccessService.start(this) }
        }

        setContent {
            val state by viewModel.uiState.collectAsState()
            val colorScheme = dkColorScheme(state.appSettings.themeMode, state.appSettings.themeSeedColor)
            val videoFrameRate by viewModel.playerManager.videoFrameRateFlow.collectAsState()

            LaunchedEffect(state.appSettings.matchDisplayFrameRate, videoFrameRate) {
                if (state.appSettings.matchDisplayFrameRate && videoFrameRate != null) {
                    applyPreferredDisplayMode(videoFrameRate!!)
                } else {
                    resetPreferredDisplayMode()
                }
            }

            MaterialTheme(colorScheme = colorScheme, typography = dkTypography, shapes = dkShapes) {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    /** A pinned launcher shortcut carries the video path/title; hand it to the UI to play. */
    private fun handleLaunchIntent(intent: Intent?) {
        val path = intent?.getStringExtra(EXTRA_PLAY_PATH) ?: return
        val title = intent.getStringExtra(EXTRA_PLAY_TITLE) ?: path.substringAfterLast('/')
        viewModel.externalPlayRequest.value = path to title
        intent.removeExtra(EXTRA_PLAY_PATH)
    }

    /**
     * "Match Display Frame Rate": switches the display's refresh rate to the closest
     * whole multiple of the video's frame rate (e.g. a 24p film on a 120Hz-capable
     * display switching to 24Hz/48Hz/120Hz rather than staying at a default 60Hz that
     * causes judder). Best-effort and fully defensive — display mode APIs vary a lot
     * across OEM skins, so any failure here just leaves the display at its current mode.
     */
    private fun applyPreferredDisplayMode(frameRate: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        runCatching {
            val display = window.decorView.display ?: return
            val currentMode = display.mode
            val sameResolutionModes = display.supportedModes.filter {
                it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight
            }
            val candidates = sameResolutionModes.ifEmpty { display.supportedModes.toList() }

            val bestMode = candidates.minByOrNull { mode ->
                val remainder = mode.refreshRate % frameRate
                minOf(remainder, frameRate - remainder)
            }

            bestMode?.let { mode ->
                val attrs = window.attributes
                attrs.preferredDisplayModeId = mode.modeId
                window.attributes = attrs
            }
        }
    }

    private fun resetPreferredDisplayMode() {
        runCatching {
            val attrs = window.attributes
            attrs.preferredDisplayModeId = 0
            window.attributes = attrs
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
        // Background/PiP mode: only "Picture-in-picture" shrinks the video into a window.
        if (com.dk.tvplayer.util.PlaybackPrefs.backgroundMode.value == com.dk.tvplayer.util.BackgroundMode.PIP) {
            enterPip()
        }
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
        if (!isInPictureInPictureMode && !FloatingPlayerState.isActive) {
            val backgroundAudioEnabled = viewModel.effectiveBackgroundPlayback(
                runBlocking { app.settingsDataStore.settingsFlow.first().backgroundAudioPlayback }
            )
            val isCasting = viewModel.playerManager.isCastingFlow.value
            if (!backgroundAudioEnabled && !isCasting && viewModel.playerManager.exoPlayer.isPlaying) {
                viewModel.playerManager.exoPlayer.pause()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // The UI is visible again, so the persistent background-playback notification
        // isn't needed — playback itself is unaffected since the player lives on the
        // Application, not the service. The service simply gets restarted the next time
        // playback continues while the app is backgrounded.
        runCatching { stopService(Intent(this, PlaybackService::class.java)) }
        viewModel.notifyAppForegrounded()
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
    val isTv = remember { DeviceType.isTelevision(context) || com.dk.tvplayer.util.UiPrefs.forceTvInterface.value }

    if (isTv) {
        TvMainScreen(viewModel = viewModel)
    } else {
        PhoneAppRoot(viewModel = viewModel)
    }
}
