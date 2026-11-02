@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.dk.tvplayer.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.mediarouter.app.MediaRouteButton
import com.dk.tvplayer.MainActivity
import com.dk.tvplayer.PipState
import com.dk.tvplayer.player.FloatingPlayerService
import com.dk.tvplayer.ui.TvPlayerViewModel
import com.dk.tvplayer.ui.components.AudioTrackDialog
import com.dk.tvplayer.ui.components.BookmarksDialog
import com.dk.tvplayer.ui.components.ControlSettingsDialog
import com.dk.tvplayer.ui.components.EqualizerDialog
import com.dk.tvplayer.ui.components.ErrorRetryBanner
import com.dk.tvplayer.ui.components.JumpToTimeDialog
import com.dk.tvplayer.ui.components.PlaybackSpeedMenu
import com.dk.tvplayer.ui.components.SavePlaylistDialog
import com.dk.tvplayer.ui.components.SleepTimerDialog
import com.dk.tvplayer.ui.components.SubtitleTrackDialog
import com.dk.tvplayer.ui.components.VideoFitOptions
import com.dk.tvplayer.ui.components.VideoInfoDialog
import com.dk.tvplayer.ui.components.VideoPlayerTipsDialog
import com.google.android.gms.cast.framework.CastButtonFactory
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.Locale
import java.util.concurrent.TimeUnit

private enum class GestureZone { BRIGHTNESS, VOLUME, SCRUB, NONE }

@Composable
fun PhonePlayerScreen(
    startUrl: String,
    startTitle: String,
    isLive: Boolean,
    viewModel: TvPlayerViewModel,
    onBack: () -> Unit
) {
    // What is playing right now. Starts as the navigation argument and changes when the
    // viewer zaps to the next/previous IPTV channel without leaving the player.
    var mediaUrl by androidx.compose.runtime.saveable.rememberSaveable(startUrl) { mutableStateOf(startUrl) }
    var title by androidx.compose.runtime.saveable.rememberSaveable(startTitle) { mutableStateOf(startTitle) }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // "Video screen orientation" (General settings).
    val orientationMode by com.dk.tvplayer.util.PlaybackPrefs.videoOrientation.flow.collectAsState()
    DisposableEffect(orientationMode, activity) {
        activity?.requestedOrientation = orientationMode.androidValue
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    val uiState by viewModel.uiState.collectAsState()
    val activePlayer by viewModel.playerManager.activePlayerFlow.collectAsState()

    // ---- IPTV channel zapping (previous / next, optionally favourites only) ----
    val currentChannel = remember(uiState.channels, mediaUrl) {
        uiState.channels.firstOrNull { it.streamUrl == mediaUrl }
    }
    var favoritesOnlyNav by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val zapPool = remember(uiState.channels, uiState.filteredChannels, uiState.favoriteChannelIds, favoritesOnlyNav) {
        if (favoritesOnlyNav) {
            uiState.channels
                .filter { it.isFavorite || it.channelId in uiState.favoriteChannelIds }
                .sortedBy { it.name.lowercase() }
        } else {
            // The list as browsed (category / search / sort), so zapping follows the same order.
            uiState.filteredChannels.ifEmpty { uiState.channels }
        }
    }
    val currentIsFavorite = currentChannel != null &&
        (currentChannel.isFavorite || currentChannel.channelId in uiState.favoriteChannelIds)
    fun zapChannel(step: Int) {
        if (zapPool.isEmpty()) return
        val idx = zapPool.indexOfFirst { it.channelId == currentChannel?.channelId }
        val target = if (idx < 0) {
            if (step > 0) zapPool.first() else zapPool.last()
        } else {
            zapPool[(idx + step).mod(zapPool.size)]
        }
        mediaUrl = target.streamUrl
        title = target.name
    }
    var showControls by remember { mutableStateOf(true) }
    val isPlaying by viewModel.playerManager.isPlayingFlow.collectAsState()
    val currentPosition by viewModel.playerManager.currentPositionFlow.collectAsState()
    val duration by viewModel.playerManager.durationFlow.collectAsState()
    val playbackSpeed by viewModel.playerManager.playbackSpeedFlow.collectAsState()
    val playbackError by viewModel.playerManager.playbackErrorFlow.collectAsState()
    val isBuffering by viewModel.playerManager.isBufferingFlow.collectAsState()
    val isCastAvailable by viewModel.playerManager.isCastAvailableFlow.collectAsState()
    val isCasting by viewModel.playerManager.isCastingFlow.collectAsState()
    val sleepTimerRemaining by viewModel.playerManager.sleepTimerRemainingSecFlow.collectAsState()

    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }

    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    // Tapping the fit button cycles modes directly; this holds the last label toast so rapid
    // taps replace it instead of queueing up.
    var fitToast by remember { mutableStateOf<Toast?>(null) }
    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showJumpToTimeDialog by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showSavePlaylistDialog by remember { mutableStateOf(false) }
    var showControlSettingsDialog by remember { mutableStateOf(false) }
    var showVideoInfoDialog by remember { mutableStateOf(false) }
    var showVideoTipsDialog by remember { mutableStateOf(false) }
    // While locked, controls stay hidden and every gesture/key handler below is
    // skipped entirely except the dedicated unlock button — this isn't just a visual
    // state, it actually disables input so an accidental touch (e.g. in a pocket)
    // can't seek, pause, or otherwise disrupt playback.
    var isLocked by remember { mutableStateOf(false) }
    var showUnlockButton by remember { mutableStateOf(false) }

    val repeatMode by viewModel.playerManager.repeatModeFlow.collectAsState()
    val abRepeat by viewModel.playerManager.abRepeatStateFlow.collectAsState()
    val audioOnlyMode by viewModel.playerManager.audioOnlyModeEnabledFlow.collectAsState()
    val equalizerState by viewModel.playerManager.equalizerStateFlow.collectAsState()
    val bookmarks by remember(mediaUrl) { viewModel.getBookmarksForMedia(mediaUrl) }
        .collectAsState(initial = emptyList())
    // Kept so subtitle style (size/color) can be reapplied whenever the setting changes.
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    // True while the video is being shown on a secondary display ("Prefer clone" off) — this
    // screen then must not grab the video surface back.
    var presentationActive by remember { mutableStateOf(false) }
    val preferClone by com.dk.tvplayer.util.UiPrefs.preferClone.collectAsState()
    // Video Fit / Zoom / Stretch / Fixed Width / Fixed Height — applied to PlayerView below.
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // Reclaims the video surface on resume — needed because returning from the floating
    // "Pop-Up player" (which attaches its own PlayerView to this same shared ExoPlayer
    // while the app is backgrounded) doesn't otherwise guarantee this screen's AndroidView
    // gets a fresh `update` call just because the Activity became visible again.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activePlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !presentationActive) {
                playerViewRef?.player = activePlayer
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // If setting up the Cast button ever throws (e.g. Cast framework not fully ready on
    // this device even though the availability precheck passed), hide the button rather
    // than let the AndroidView factory crash the screen.
    var castButtonFailed by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    // Gesture HUD state
    var brightnessLevel by remember {
        mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it in 0f..1f } ?: 0.5f)
    }
    var volumeLevel by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume)
    }
    var showBrightnessHud by remember { mutableStateOf(false) }
    var showVolumeHud by remember { mutableStateOf(false) }
    var scrubOffsetMs by remember { mutableLongStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }
    // Tracked via Modifier.onSizeChanged so it stays correct across rotation, unlike a
    // one-shot pointerInput(Unit) block which only ever captured the size once.
    var containerWidthPx by remember { mutableFloatStateOf(1f) }
    var containerHeightPx by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(mediaUrl) {
        viewModel.playMedia(mediaUrl, title)
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    DisposableEffect(Unit) {
        PipState.isVideoPlayerActive = true
        // Immersive playback: hide the status/navigation bars (swipe from the edge to peek).
        val bars = activity?.window?.let { w ->
            androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).also {
                it.systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                it.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            bars?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            PipState.isVideoPlayerActive = false
            viewModel.savePlaybackProgress(mediaUrl, title, currentPosition, duration)
            // Per-viewing toggles: "Play as audio" and an A-B loop shouldn't silently carry
            // over into whatever gets played next.
            viewModel.playerManager.setAudioOnlyModeEnabled(false)
            viewModel.playerManager.clearAbRepeat()
            // Reset window brightness override on exit so other screens aren't affected.
            activity?.window?.attributes = activity?.window?.attributes?.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    // Held open while the ⋮ menu is showing — otherwise the controls (and the menu with
    // them) would vanish after 4s in the middle of scrolling a long list.
    LaunchedEffect(showControls, showMoreMenu) {
        if (showControls && !showMoreMenu) {
            delay(4000)
            showControls = false
        }
    }

    // While locked, the unlock button is only shown briefly after locking or after a tap
    // on the screen, then fades away so it doesn't sit over the picture.
    LaunchedEffect(isLocked, showUnlockButton) {
        if (isLocked && showUnlockButton) {
            delay(3000)
            showUnlockButton = false
        }
    }

    // Auto-hide brightness/volume HUD pills shortly after the last change, so they never
    // sit on screen and block the picture while a movie is playing.
    LaunchedEffect(showBrightnessHud) {
        if (showBrightnessHud) {
            delay(900)
            showBrightnessHud = false
        }
    }

    LaunchedEffect(showVolumeHud) {
        if (showVolumeHud) {
            delay(900)
            showVolumeHud = false
        }
    }

    fun applyBrightness(value: Float) {
        val clamped = value.coerceIn(0.01f, 1f)
        brightnessLevel = clamped
        activity?.window?.attributes = activity?.window?.attributes?.apply {
            screenBrightness = clamped
        }
    }

    fun applyVolume(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        volumeLevel = clamped
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            (clamped * maxVolume).roundToInt(),
            0
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (isLocked) {
                    // Back/Escape still work while locked so the person is never
                    // trapped on the player screen — everything else is ignored.
                    return@onKeyEvent when (keyEvent.key) {
                        Key.Back, Key.Escape -> { onBack(); true }
                        else -> false
                    }
                }
                // Keyboard shortcuts: works with bluetooth/attached keyboards, remote D-pads.
                when (keyEvent.key) {
                    Key.Spacebar, Key.MediaPlayPause -> {
                        viewModel.playerManager.togglePlayPause(); true
                    }
                    Key.DirectionLeft, Key.MediaRewind -> {
                        viewModel.playerManager.seekTo((currentPosition - 10_000).coerceAtLeast(0)); true
                    }
                    Key.DirectionRight, Key.MediaFastForward -> {
                        viewModel.playerManager.seekTo((currentPosition + 10_000).coerceAtMost(duration)); true
                    }
                    Key.DirectionUp -> {
                        applyVolume(volumeLevel + 0.1f); showVolumeHud = true; true
                    }
                    Key.DirectionDown -> {
                        applyVolume(volumeLevel - 0.1f); showVolumeHud = true; true
                    }
                    Key.C -> {
                        showSubtitleDialog = true; true
                    }
                    Key.F -> {
                        activity?.let { toggleScreenOrientation(it) }; true
                    }
                    Key.Back, Key.Escape -> {
                        onBack(); true
