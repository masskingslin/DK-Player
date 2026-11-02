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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.sp
import androidx.media3.ui.CaptionStyleCompat
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

    // ---- Local video previous / next ----
    // Next goes to the play queue first (if anything is queued), otherwise to the following
    // video in the library; previous goes to the preceding library video. Both wrap around.
    val videoPool = uiState.localVideos
    val canSkipVideo = currentChannel == null && !isLive && videoPool.isNotEmpty()
    fun skipVideo(step: Int) {
        if (step > 0) {
            val queued = viewModel.popNextQueued()
            if (queued != null) {
                mediaUrl = queued.first
                title = queued.second
                return
            }
        }
        if (videoPool.isEmpty()) return
        val idx = videoPool.indexOfFirst { it.filePath == mediaUrl }
        val target = if (idx < 0) {
            if (step > 0) videoPool.first() else videoPool.last()
        } else {
            videoPool[(idx + step).mod(videoPool.size)]
        }
        mediaUrl = target.filePath
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
    // Tap the right-hand time to flip between total length and time remaining.
    var showRemainingTime by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

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
    LaunchedEffect(showBrightnessHud, brightnessLevel) {
        if (showBrightnessHud) {
            delay(1000)
            showBrightnessHud = false
        }
    }

    LaunchedEffect(showVolumeHud, volumeLevel) {
        if (showVolumeHud) {
            delay(1000)
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

    // "Prefer clone" off + a secondary display attached: play the video there and let this
    // screen act as the remote.
    DisposableEffect(preferClone, activePlayer) {
        var presentation: VideoPresentation? = null
        if (!preferClone) {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
            val display = dm
                .getDisplays(android.hardware.display.DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                .firstOrNull()
            if (display != null) {
                presentation = runCatching {
                    VideoPresentation(activity ?: context, display, activePlayer).also { it.show() }
                }.getOrNull()
            }
        }
        presentationActive = presentation != null
        playerViewRef?.player = if (presentationActive) null else activePlayer
        onDispose {
            presentation?.let { runCatching { it.dismiss() } }
            presentationActive = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
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
                    }
                    else -> false
                }
            }
            .onSizeChanged {
                containerWidthPx = it.width.toFloat().coerceAtLeast(1f)
                containerHeightPx = it.height.toFloat().coerceAtLeast(1f)
            }
            // Single tap toggles the controls (or reveals the unlock button while locked);
            // double-tap on the left/right half seeks -10s / +10s.
            .pointerInput(isLocked, uiState.appSettings.doubleTapSeekEnabled, isLive) {
                detectTapGestures(
                    onTap = {
                        if (isLocked) showUnlockButton = true else showControls = !showControls
                    },
                    onDoubleTap = { offset ->
                        if (!isLocked && uiState.appSettings.doubleTapSeekEnabled && !isLive) {
                            val delta = if (offset.x < size.width / 2f) -10_000L else 10_000L
                            val target = (currentPosition + delta).coerceIn(0L, duration.coerceAtLeast(0L))
                            viewModel.playerManager.seekTo(target)
                        }
                    }
                )
            }
            // Vertical drag on the left half = brightness, right half = volume;
            // horizontal drag = scrub.
            .pointerInput(
                isLocked,
                uiState.appSettings.gestureSeekEnabled,
                uiState.appSettings.gestureBrightnessVolumeEnabled,
                isLive
            ) {
                if (isLocked) return@pointerInput
                var zone = GestureZone.NONE
                var startX = 0f
                var accumX = 0f
                var accumY = 0f
                var startPosition = 0L
                fun finishDrag() {
                    if (zone == GestureZone.SCRUB && isScrubbing) {
                        val target = (startPosition + scrubOffsetMs)
                            .coerceIn(0L, duration.coerceAtLeast(0L))
                        viewModel.playerManager.seekTo(target)
                    }
                    isScrubbing = false
                    scrubOffsetMs = 0L
                    zone = GestureZone.NONE
                }
                detectDragGestures(
                    onDragStart = { offset ->
                        zone = GestureZone.NONE
                        startX = offset.x
                        accumX = 0f
                        accumY = 0f
                        startPosition = currentPosition
                    },
                    onDragEnd = { finishDrag() },
                    onDragCancel = { finishDrag() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (zone == GestureZone.NONE) {
                            accumX += dragAmount.x
                            accumY += dragAmount.y
                            if (abs(accumX) > 24f || abs(accumY) > 24f) {
                                zone = if (abs(accumX) > abs(accumY)) {
                                    if (uiState.appSettings.gestureSeekEnabled && !isLive && duration > 0L) {
                                        GestureZone.SCRUB
                                    } else GestureZone.NONE
                                } else if (uiState.appSettings.gestureBrightnessVolumeEnabled) {
                                    if (startX < size.width / 2f) GestureZone.BRIGHTNESS else GestureZone.VOLUME
                                } else GestureZone.NONE
                                // Nothing to do for a disabled gesture: stay NONE but stop re-deciding.
                                if (zone == GestureZone.NONE) {
                                    accumX = 0f
                                    accumY = 0f
                                }
                            }
                        } else {
                            when (zone) {
                                GestureZone.BRIGHTNESS -> {
                                    applyBrightness(brightnessLevel - dragAmount.y / containerHeightPx * 1.2f)
                                    showBrightnessHud = true
                                }
                                GestureZone.VOLUME -> {
                                    applyVolume(volumeLevel - dragAmount.y / containerHeightPx * 1.2f)
                                    showVolumeHud = true
                                }
                                GestureZone.SCRUB -> {
                                    isScrubbing = true
                                    scrubOffsetMs += (dragAmount.x / containerWidthPx * 90_000f).toLong()
                                }
                                GestureZone.NONE -> Unit
                            }
                        }
                    }
                )
            }
    ) {
        // ---- Video surface ----
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    keepScreenOn = true
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                if (playerViewRef !== view) playerViewRef = view
                view.resizeMode = resizeMode
                if (presentationActive) {
                    if (view.player != null) view.player = null
                } else if (view.player !== activePlayer) {
                    view.player = activePlayer
                }
                view.subtitleView?.apply {
                    setFixedTextSize(
                        android.util.TypedValue.COMPLEX_UNIT_SP,
                        uiState.appSettings.subtitleTextSize.sp
                    )
                    setStyle(
                        CaptionStyleCompat(
                            uiState.appSettings.subtitleColor.colorArgb,
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // "Play as audio" / secondary-display placeholder.
        if (audioOnlyMode || presentationActive) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Headphones,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (presentationActive) "Playing on external display" else title,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        }

        if (isBuffering && playbackError == null) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).size(48.dp)
            )
        }

        playbackError?.let { message ->
            ErrorRetryBanner(
                message = message,
                onRetry = { viewModel.playerManager.retryPlayback() },
                onDismiss = { viewModel.playerManager.clearError() },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp, start = 16.dp, end = 16.dp)
            )
        }

        // ---- Gesture HUDs ----
        AnimatedVisibility(
            visible = showBrightnessHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp)
        ) { HudPill(icon = Icons.Filled.WbSunny, progress = brightnessLevel) }

        AnimatedVisibility(
            visible = showVolumeHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)
        ) { HudPill(icon = Icons.Filled.VolumeUp, progress = volumeLevel) }

        if (isScrubbing) {
            val sign = if (scrubOffsetMs >= 0) "+" else "-"
            val target = (currentPosition + scrubOffsetMs).coerceIn(0L, duration.coerceAtLeast(0L))
            Text(
                text = "$sign${formatPlayerTime(abs(scrubOffsetMs))}  →  ${formatPlayerTime(target)}",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // ---- Controls overlay ----
        AnimatedVisibility(
            visible = showControls && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
                // Top bar
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ControlIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back") { onBack() }
                    Text(
                        text = title,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    if (isCastAvailable && uiState.appSettings.wirelessCastingEnabled && !castButtonFailed) {
                        AndroidView(
                            factory = { ctx ->
                                // Wrapped in an AppCompat theme: MediaRouteButton crashes when its
                                // context lacks colorPrimary & co. Any failure hides the button.
                                runCatching {
                                    val themed = androidx.appcompat.view.ContextThemeWrapper(
                                        ctx,
                                        androidx.appcompat.R.style.Theme_AppCompat
                                    )
                                    MediaRouteButton(themed).also {
                                        CastButtonFactory.setUpMediaRouteButton(ctx.applicationContext, it)
                                    }
                                }.getOrElse {
                                    castButtonFailed = true
                                    android.view.View(ctx)
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    if (currentChannel != null) {
                        ControlIcon(
                            icon = if (currentIsFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            description = "Favourite",
                            tint = if (currentIsFavorite) MaterialTheme.colorScheme.primary else Color.White
                        ) { viewModel.toggleFavorite(currentChannel.channelId) }
                    }
                    Box {
                        ControlIcon(Icons.Filled.MoreVert, "More") { showMoreMenu = true }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Audio track") },
                                leadingIcon = { Icon(Icons.Filled.Audiotrack, null) },
                                onClick = { showMoreMenu = false; showAudioTrackDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Subtitles") },
                                leadingIcon = { Icon(Icons.Filled.ClosedCaption, null) },
                                onClick = { showMoreMenu = false; showSubtitleDialog = true }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (sleepTimerRemaining != null)
                                            "Sleep timer (${formatPlayerTime(sleepTimerRemaining!! * 1000)})"
                                        else "Sleep timer"
                                    )
                                },
                                leadingIcon = { Icon(Icons.Filled.Bedtime, null) },
                                onClick = { showMoreMenu = false; showSleepTimerDialog = true }
                            )
                            if (!isLive) {
                                DropdownMenuItem(
                                    text = { Text("Jump to time") },
                                    leadingIcon = { Icon(Icons.Filled.Update, null) },
                                    onClick = { showMoreMenu = false; showJumpToTimeDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Bookmarks") },
                                    leadingIcon = { Icon(Icons.Filled.BookmarkBorder, null) },
                                    onClick = { showMoreMenu = false; showBookmarksDialog = true }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when {
                                                abRepeat.pointAMs == null -> "A-B loop: set start (A)"
                                                abRepeat.pointBMs == null -> "A-B loop: set end (B)"
                                                else -> "A-B loop: clear"
                                            }
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Loop, null) },
                                    onClick = {
                                        showMoreMenu = false
                                        when {
                                            abRepeat.pointAMs == null -> viewModel.playerManager.setAbRepeatPointA()
                                            abRepeat.pointBMs == null -> viewModel.playerManager.setAbRepeatPointB()
                                            else -> viewModel.playerManager.clearAbRepeat()
                                        }
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (audioOnlyMode) "Play video" else "Play as audio") },
                                leadingIcon = { Icon(Icons.Filled.Headphones, null) },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.playerManager.setAudioOnlyModeEnabled(!audioOnlyMode)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Equalizer") },
                                leadingIcon = { Icon(Icons.Filled.Tune, null) },
                                onClick = { showMoreMenu = false; showEqualizerDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Add to playlist") },
                                leadingIcon = { Icon(Icons.Filled.PlaylistAdd, null) },
                                onClick = { showMoreMenu = false; showSavePlaylistDialog = true }
                            )
                            if (currentChannel != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (favoritesOnlyNav) "Zap: all channels"
                                            else "Zap: favourites only"
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Star, null) },
                                    onClick = { showMoreMenu = false; favoritesOnlyNav = !favoritesOnlyNav }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Pop-up player") },
                                leadingIcon = { Icon(Icons.Filled.PictureInPictureAlt, null) },
                                onClick = {
                                    showMoreMenu = false
                                    if (!Settings.canDrawOverlays(context)) {
                                        Toast.makeText(
                                            context,
                                            "Allow \"Display over other apps\" to use the pop-up player",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        runCatching {
                                            context.startActivity(
                                                Intent(
                                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    Uri.parse("package:${context.packageName}")
                                                )
                                            )
                                        }
                                    } else {
                                        runCatching {
                                            context.startService(Intent(context, FloatingPlayerService::class.java))
                                            activity?.moveTaskToBack(true)
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Video information") },
                                leadingIcon = { Icon(Icons.Filled.Info, null) },
                                onClick = { showMoreMenu = false; showVideoInfoDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Control settings") },
                                leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                onClick = { showMoreMenu = false; showControlSettingsDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Tips") },
                                leadingIcon = { Icon(Icons.Filled.Lightbulb, null) },
                                onClick = { showMoreMenu = false; showVideoTipsDialog = true }
                            )
                        }
                    }
                }

                // Centre transport: previous / (-10s) / play-pause / (+10s) / next
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentChannel != null) {
                        ControlIcon(Icons.Filled.SkipPrevious, "Previous channel", size = 56.dp) { zapChannel(-1) }
                    } else if (canSkipVideo) {
                        ControlIcon(Icons.Filled.SkipPrevious, "Previous video", size = 56.dp) { skipVideo(-1) }
                    }
                    if (currentChannel == null && !isLive) {
                        ControlIcon(Icons.Filled.Replay10, "Back 10 seconds", size = 52.dp) {
                            viewModel.playerManager.seekTo((currentPosition - 10_000).coerceAtLeast(0))
                        }
                    }
                    IconButton(
                        onClick = { viewModel.playerManager.togglePlayPause() },
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    if (currentChannel == null && !isLive) {
                        ControlIcon(Icons.Filled.Forward10, "Forward 10 seconds", size = 52.dp) {
                            viewModel.playerManager.seekTo(
                                (currentPosition + 10_000).coerceAtMost(duration.coerceAtLeast(0L))
                            )
                        }
                    }
                    if (currentChannel != null) {
                        ControlIcon(Icons.Filled.SkipNext, "Next channel", size = 56.dp) { zapChannel(1) }
                    } else if (canSkipVideo) {
                        ControlIcon(Icons.Filled.SkipNext, "Next video", size = 56.dp) { skipVideo(1) }
                    }
                }

                // Bottom: seek bar + quick actions
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (isLive || duration <= 0L) {
                        Text(
                            text = "LIVE",
                            color = Color.White,
                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatPlayerTime(if (isDraggingSlider) sliderValue.toLong() else currentPosition),
                                color = Color.White
                            )
                            Slider(
                                value = (if (isDraggingSlider) sliderValue else currentPosition.toFloat())
                                    .coerceIn(0f, duration.toFloat()),
                                onValueChange = {
                                    isDraggingSlider = true
                                    sliderValue = it
                                },
                                onValueChangeFinished = {
                                    viewModel.playerManager.seekTo(sliderValue.toLong())
                                    isDraggingSlider = false
                                },
                                valueRange = 0f..duration.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            val shownPosition = if (isDraggingSlider) sliderValue.toLong() else currentPosition
                            Text(
                                text = if (showRemainingTime) {
                                    "-" + formatPlayerTime((duration - shownPosition).coerceAtLeast(0L))
                                } else {
                                    formatPlayerTime(duration)
                                },
                                color = Color.White,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { showRemainingTime = !showRemainingTime }
                                    .padding(horizontal = 6.dp, vertical = 10.dp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text(
                            text = if (playbackSpeed == 1.0f) "1x" else "${playbackSpeed}x",
                            color = if (showSpeedMenu) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showSpeedMenu = !showSpeedMenu }
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        )
                        ControlIcon(Icons.Filled.ClosedCaption, "Subtitles") { showSubtitleDialog = true }
                        ControlIcon(Icons.Filled.Audiotrack, "Audio track") { showAudioTrackDialog = true }
                        ControlIcon(Icons.Filled.AspectRatio, "Video fit") {
                            val idx = VideoFitOptions.indexOfFirst { it.resizeMode == resizeMode }
                            val next = VideoFitOptions[(idx + 1).mod(VideoFitOptions.size)]
                            resizeMode = next.resizeMode
                            fitToast?.cancel()
                            fitToast = Toast.makeText(context, next.label, Toast.LENGTH_SHORT).also { it.show() }
                        }
                        ControlIcon(Icons.Filled.ScreenRotation, "Rotate") {
                            activity?.let { toggleScreenOrientation(it) }
                        }
                        ControlIcon(
                            icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            description = "Repeat",
                            tint = if (repeatMode == Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.primary
                        ) { viewModel.playerManager.cycleRepeatMode() }
                        ControlIcon(Icons.Filled.PictureInPictureAlt, "Picture in picture") {
                            (activity as? MainActivity)?.enterPip()
                        }
                        ControlIcon(Icons.Filled.Lock, "Lock controls") {
                            isLocked = true
                            showControls = false
                            showUnlockButton = true
                        }
                    }
                }
            }
        }

        // Playback speed menu (sits above the bottom bar).
        if (showSpeedMenu && showControls && !isLocked) {
            PlaybackSpeedMenu(
                currentSpeed = playbackSpeed,
                onSpeedSelected = {
                    viewModel.playerManager.setPlaybackSpeed(it)
                    showSpeedMenu = false
                },
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 96.dp)
            )
        }

        // Unlock button while locked: appears after a tap, then fades.
        AnimatedVisibility(
            visible = isLocked && showUnlockButton,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp)
        ) {
            IconButton(
                onClick = {
                    isLocked = false
                    showUnlockButton = false
                    showControls = true
                },
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(Icons.Filled.LockOpen, contentDescription = "Unlock", tint = Color.White)
            }
        }

        // ---- Dialogs ----
        if (showAudioTrackDialog) {
            AudioTrackDialog(
                tracks = viewModel.playerManager.availableAudioTracks(),
                onDismiss = { showAudioTrackDialog = false },
                onTrackSelected = { group, track ->
                    viewModel.playerManager.selectAudioTrack(group, track)
                    showAudioTrackDialog = false
                }
            )
        }
        if (showSubtitleDialog) {
            SubtitleTrackDialog(
                tracks = viewModel.playerManager.availableSubtitleTracks(),
                onDismiss = { showSubtitleDialog = false },
                onTrackSelected = { group, track ->
                    viewModel.playerManager.selectSubtitleTrack(group, track)
                    showSubtitleDialog = false
                },
                onSubtitlesOff = {
                    viewModel.playerManager.disableSubtitles()
                    showSubtitleDialog = false
                },
                onLoadExternalSubtitle = { url ->
                    viewModel.playerManager.loadExternalSubtitle(url)
                    showSubtitleDialog = false
                }
            )
        }
        if (showSleepTimerDialog) {
            SleepTimerDialog(
                activeRemainingSec = sleepTimerRemaining,
                onDismiss = { showSleepTimerDialog = false },
                onStart = { minutes ->
                    viewModel.playerManager.startSleepTimer(minutes)
                    showSleepTimerDialog = false
                },
                onCancel = {
                    viewModel.playerManager.cancelSleepTimer()
                    showSleepTimerDialog = false
                }
            )
        }
        if (showJumpToTimeDialog) {
            JumpToTimeDialog(
                durationMs = duration,
                onDismiss = { showJumpToTimeDialog = false },
                onJump = { positionMs ->
                    viewModel.playerManager.seekTo(positionMs)
                    showJumpToTimeDialog = false
                }
            )
        }
        if (showEqualizerDialog) {
            EqualizerDialog(
                available = equalizerState.available,
                onDismiss = { showEqualizerDialog = false },
                onChanged = { viewModel.playerManager.applyEqualizerSettings() }
            )
        }
        if (showBookmarksDialog) {
            BookmarksDialog(
                bookmarks = bookmarks,
                currentPositionMs = currentPosition,
                onDismiss = { showBookmarksDialog = false },
                onAddBookmark = { label ->
                    viewModel.addBookmark(mediaUrl, title, currentPosition, label)
                },
                onJumpTo = { bookmark ->
                    viewModel.playerManager.seekTo(bookmark.positionMs)
                    showBookmarksDialog = false
                },
                onDelete = { bookmark -> viewModel.deleteBookmark(bookmark) }
            )
        }
        if (showSavePlaylistDialog) {
            SavePlaylistDialog(
                playlists = uiState.playlists,
                onDismiss = { showSavePlaylistDialog = false },
                onAddToExisting = { playlist ->
                    viewModel.addToExistingPlaylist(playlist.id, title, mediaUrl)
                    showSavePlaylistDialog = false
                },
                onCreateNew = { name ->
                    viewModel.createPlaylistAndAddItem(name, title, mediaUrl)
                    showSavePlaylistDialog = false
                }
            )
        }
        if (showControlSettingsDialog) {
            ControlSettingsDialog(
                gestureSeekEnabled = uiState.appSettings.gestureSeekEnabled,
                gestureBrightnessVolumeEnabled = uiState.appSettings.gestureBrightnessVolumeEnabled,
                doubleTapSeekEnabled = uiState.appSettings.doubleTapSeekEnabled,
                onDismiss = { showControlSettingsDialog = false },
                onGestureSeekChange = { viewModel.setGestureSeekEnabled(it) },
                onGestureBrightnessVolumeChange = { viewModel.setGestureBrightnessVolumeEnabled(it) },
                onDoubleTapSeekChange = { viewModel.setDoubleTapSeekEnabled(it) }
            )
        }
        if (showVideoInfoDialog) {
            VideoInfoDialog(
                videoFormat = viewModel.playerManager.currentVideoFormat(),
                audioFormat = viewModel.playerManager.currentAudioFormat(),
                onDismiss = { showVideoInfoDialog = false }
            )
        }
        if (showVideoTipsDialog) {
            VideoPlayerTipsDialog(onDismiss = { showVideoTipsDialog = false })
        }
    }
}

/** Small pill showing a brightness/volume level while the user drags. */
@Composable
private fun HudPill(icon: ImageVector, progress: Float) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(10.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.width(96.dp)
        )
    }
}

@Composable
private fun ControlIcon(
    icon: ImageVector,
    description: String,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(size)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

private fun formatPlayerTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000L
    val h = TimeUnit.SECONDS.toHours(totalSeconds)
    val m = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun toggleScreenOrientation(activity: Activity) {
    val landscape = activity.resources.configuration.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    activity.requestedOrientation = if (landscape) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    } else {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
}
