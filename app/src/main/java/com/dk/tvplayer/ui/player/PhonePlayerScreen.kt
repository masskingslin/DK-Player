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
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import com.dk.tvplayer.ui.components.VideoFitMenu
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
    mediaUrl: String,
    title: String,
    isLive: Boolean,
    viewModel: TvPlayerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    val uiState by viewModel.uiState.collectAsState()
    val activePlayer by viewModel.playerManager.activePlayerFlow.collectAsState()
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
    var showFitMenu by remember { mutableStateOf(false) }
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
    // Video Fit / Zoom / Stretch / Fixed Width / Fixed Height — applied to PlayerView below.
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // Reclaims the video surface on resume — needed because returning from the floating
    // "Pop-Up player" (which attaches its own PlayerView to this same shared ExoPlayer
    // while the app is backgrounded) doesn't otherwise guarantee this screen's AndroidView
    // gets a fresh `update` call just because the Activity became visible again.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activePlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
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
        onDispose {
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
                    }
                    else -> false
                }
            }
            // Reactive size tracking (fixes stale brightness/volume zone boundaries after rotation).
            .onSizeChanged { size ->
                containerWidthPx = size.width.toFloat().coerceAtLeast(1f)
                containerHeightPx = size.height.toFloat().coerceAtLeast(1f)
            }
            // Single pointerInput block running both gesture detectors in their own
            // coroutines — combining drag + tap detection in one block (rather than as
            // separate .pointerInput() modifiers) is what makes both reliably see every
            // touch event instead of intermittently stealing events from each other.
            .pointerInput(duration) {
                coroutineScope {
                    launch {
                        detectTapGestures(
                            onTap = {
                                if (isLocked) {
                                    // Locked: a tap does nothing except surface the unlock
                                    // button for a few seconds.
                                    showUnlockButton = true
                                    return@detectTapGestures
                                }
                                showControls = !showControls
                            },
                            onDoubleTap = { offset ->
                                if (isLocked) return@detectTapGestures
                                // "Double-tap to seek" (Control settings) only governs the
                                // left/right zones; double-tapping the middle still toggles
                                // play/pause either way.
                                val inMiddleZone = offset.x >= containerWidthPx * 0.4f &&
                                    offset.x <= containerWidthPx * 0.6f
                                if (inMiddleZone) {
                                    viewModel.playerManager.togglePlayPause()
                                    return@detectTapGestures
                                }
                                if (!uiState.appSettings.doubleTapSeekEnabled) return@detectTapGestures
                                if (offset.x < containerWidthPx * 0.4f) {
                                    viewModel.playerManager.seekTo((currentPosition - 10_000).coerceAtLeast(0))
                                } else {
                                    viewModel.playerManager.seekTo((currentPosition + 10_000).coerceAtMost(duration))
                                }
                            }
                        )
                    }
                    launch {
                        var activeZone = GestureZone.NONE
                        var dragStartVolume = 0f
                        var dragStartBrightness = 0f
                        var dragStartPositionMs = 0L

                        detectDragGestures(
                            onDragStart = { offset ->
                                val settings = uiState.appSettings
                                val requestedZone = when {
                                    offset.x < containerWidthPx * 0.4f -> GestureZone.BRIGHTNESS
                                    offset.x > containerWidthPx * 0.6f -> GestureZone.VOLUME
                                    else -> GestureZone.SCRUB
                                }
                                // A zone whose gesture is switched off in "Control settings" —
                                // or any zone at all while the screen is locked — resolves to
                                // NONE, which the drag handler below treats as a no-op.
                                activeZone = when {
                                    isLocked -> GestureZone.NONE
                                    requestedZone == GestureZone.SCRUB && !settings.gestureSeekEnabled -> GestureZone.NONE
                                    requestedZone != GestureZone.SCRUB && !settings.gestureBrightnessVolumeEnabled -> GestureZone.NONE
                                    else -> requestedZone
                                }
                                dragStartVolume = volumeLevel
                                dragStartBrightness = brightnessLevel
                                dragStartPositionMs = currentPosition
                                if (activeZone == GestureZone.SCRUB) {
                                    isScrubbing = true
                                    scrubOffsetMs = 0L
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                when (activeZone) {
                                    GestureZone.BRIGHTNESS -> {
                                        val delta = -dragAmount.y / containerHeightPx
                                        applyBrightness(dragStartBrightness + delta)
                                        dragStartBrightness = brightnessLevel
                                        showBrightnessHud = true
                                    }
                                    GestureZone.VOLUME -> {
                                        val delta = -dragAmount.y / containerHeightPx
                                        applyVolume(dragStartVolume + delta)
                                        dragStartVolume = volumeLevel
                                        showVolumeHud = true
                                    }
                                    GestureZone.SCRUB -> {
                                        if (duration > 0) {
                                            val deltaMs = (dragAmount.x / containerWidthPx) * duration
                                            scrubOffsetMs = (scrubOffsetMs + deltaMs.toLong())
                                                .coerceIn(-dragStartPositionMs, duration - dragStartPositionMs)
                                        }
                                    }
                                    GestureZone.NONE -> Unit
                                }
                            },
                            onDragEnd = {
                                if (activeZone == GestureZone.SCRUB && isScrubbing) {
                                    val target = (dragStartPositionMs + scrubOffsetMs).coerceIn(0L, duration)
                                    viewModel.playerManager.seekTo(target)
                                }
                                isScrubbing = false
                                scrubOffsetMs = 0L
                                // Force the HUD to hide right away once the finger lifts,
                                // instead of waiting out the auto-hide delay.
                                if (activeZone == GestureZone.BRIGHTNESS) showBrightnessHud = false
                                if (activeZone == GestureZone.VOLUME) showVolumeHud = false
                                activeZone = GestureZone.NONE
                            }
                        )
                    }
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = activePlayer
                    this.resizeMode = resizeMode
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    playerViewRef = this
                }
            },
            update = { playerView ->
                playerView.player = activePlayer
                playerView.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // Visible feedback while the player is buffering (e.g. loading a large offline
        // download, or waiting on a slow network) — without this, a long stall just
        // looked like a frozen black screen with no indication anything was happening.
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }

        // Applies the persisted subtitle size/color preference to the caption view
        // whenever the player view is (re)created or the settings change.
        LaunchedEffect(playerViewRef, uiState.appSettings.subtitleTextSize, uiState.appSettings.subtitleColor) {
            val subtitleView = playerViewRef?.subtitleView ?: return@LaunchedEffect
            subtitleView.setFixedTextSize(
                android.util.TypedValue.COMPLEX_UNIT_SP,
                uiState.appSettings.subtitleTextSize.sp
            )
            subtitleView.setStyle(
                androidx.media3.ui.CaptionStyleCompat(
                    uiState.appSettings.subtitleColor.colorArgb,
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                    androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    android.graphics.Color.BLACK,
                    null
                )
            )
        }

        // Brightness HUD pill (left edge)
        AnimatedVisibility(
            visible = showBrightnessHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            GestureHudPill(
                icon = Icons.Default.WbSunny,
                percent = (brightnessLevel * 100).roundToInt()
            )
        }

        // Volume HUD pill (right edge)
        AnimatedVisibility(
            visible = showVolumeHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            GestureHudPill(
                icon = Icons.Default.VolumeUp,
                percent = (volumeLevel * 100).roundToInt()
            )
        }

        // Scrub time-offset flag
        AnimatedVisibility(
            visible = isScrubbing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                val sign = if (scrubOffsetMs >= 0) "+" else "-"
                Text(
                    text = "$sign${formatTime(abs(scrubOffsetMs))}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        // Persistent Cast button in the top-right corner — reachable with one tap at any
        // time, not hidden behind "tap to show controls" first. This matches how VLC and
        // most streaming apps handle casting: it's a direct, always-visible affordance
        // rather than one more icon buried in the auto-hiding control row.
        if (isCastAvailable && !castButtonFailed && !isLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                AndroidView(
                    factory = { ctx ->
                        try {
                            MediaRouteButton(ctx).apply {
                                CastButtonFactory.setUpMediaRouteButton(ctx, this)
                            }
                        } catch (t: Throwable) {
                            // Cast framework wasn't actually ready despite the
                            // availability precheck — degrade gracefully instead of
                            // crashing the player screen.
                            castButtonFailed = true
                            android.widget.FrameLayout(ctx)
                        }
                    }
                )
            }
        }

        if (isCasting) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Casting to device", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
        }

        playbackError?.let { errorMessage ->
            ErrorRetryBanner(
                message = errorMessage,
                onRetry = { viewModel.playerManager.retryPlayback() },
                onDismiss = { viewModel.playerManager.clearError() },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // The only interactive element while locked. Appears for a few seconds after
        // locking or after tapping the screen, then fades so it never sits over the picture.
        AnimatedVisibility(
            visible = isLocked && showUnlockButton,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
        ) {
            IconButton(
                onClick = {
                    isLocked = false
                    showUnlockButton = false
                    showControls = true
                },
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = "Unlock", tint = Color.White)
            }
        }

        AnimatedVisibility(
            visible = showControls && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.align(Alignment.TopCenter)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (isCastAvailable && !castButtonFailed) {
                            // Reserves the same width the persistent corner cast button
                            // occupies, so the title doesn't jump under it as that
                            // button fades in/out with showControls.
                            Spacer(modifier = Modifier.width(48.dp))
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(if (isDraggingSlider) sliderValue.toLong() else currentPosition),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = when {
                                // Remaining time (counting down, "-1:32:10" style) reads
                                // better mid-movie than a static total — matches what
                                // most video players show here.
                                duration > 0 -> "-" + formatTime(
                                    (duration - if (isDraggingSlider) sliderValue.toLong() else currentPosition)
                                        .coerceAtLeast(0L)
                                )
                                isLive -> "LIVE"
                                else -> "--:--" // VOD/local file whose duration just hasn't loaded yet — never "LIVE"
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (duration > 0) {
                        Slider(
                            value = if (isDraggingSlider) sliderValue else currentPosition.toFloat(),
                            onValueChange = {
                                isDraggingSlider = true
                                sliderValue = it
                            },
                            onValueChangeFinished = {
                                isDraggingSlider = false
                                viewModel.playerManager.seekTo(sliderValue.toLong())
                            },
                            valueRange = 0f..duration.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (isLive) {
                        // Live streams have no meaningful duration/seek bar — just show
                        // that the transport is active without implying a scrubbable timeline.
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        // Duration just hasn't loaded yet for this file — same generic
                        // "in progress" affordance, but the label above already reads
                        // "--:--" instead of "LIVE" so it isn't mistaken for a live stream.
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Single consolidated transport row — fit on the left, rewind/play/
                    // forward centered, overflow ("...") on the right for everything
                    // used less often, instead of spreading every action across a
                    // separate top icon row.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showFitMenu = !showFitMenu }) {
                            Icon(Icons.Default.AspectRatio, contentDescription = "Video fit", tint = Color.White)
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.playerManager.seekTo((currentPosition - 10_000).coerceAtLeast(0)) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "Rewind 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.playerManager.togglePlayPause() },
                                modifier = Modifier
                                    .size(56.dp)
                                    .border(width = 1.5.dp, color = Color.White, shape = CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.playerManager.seekTo((currentPosition + 10_000).coerceAtMost(duration)) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "Fast Forward 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White)
                            }
                            // Scrolls on its own when taller than the screen (Material3's
                            // DropdownMenu content is vertically scrollable), which matters
                            // in landscape where this many items won't fit.
                            DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Lock") },
                                    leadingIcon = { MenuIcon(Icons.Default.Lock) },
                                    onClick = {
                                        isLocked = true
                                        showControls = false
                                        showUnlockButton = true
                                        showMoreMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Audio track") },
                                    leadingIcon = { MenuIcon(Icons.Default.Audiotrack) },
                                    onClick = { showAudioTrackDialog = true; showMoreMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Subtitles") },
                                    leadingIcon = { MenuIcon(Icons.Default.ClosedCaption) },
                                    onClick = { showSubtitleDialog = true; showMoreMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sleep timer") },
                                    leadingIcon = { MenuIcon(Icons.Default.Bedtime, active = sleepTimerRemaining != null) },
                                    onClick = { showSleepTimerDialog = true; showMoreMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Playback speed") },
                                    leadingIcon = { MenuIcon(Icons.Default.Speed) },
                                    onClick = { showSpeedMenu = true; showMoreMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Jump to Time") },
                                    leadingIcon = { MenuIcon(Icons.Default.Update) },
                                    onClick = { showJumpToTimeDialog = true; showMoreMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Equalizer") },
                                    leadingIcon = { MenuIcon(Icons.Default.Tune) },
                                    onClick = { showEqualizerDialog = true; showMoreMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (audioOnlyMode) "Play video" else "Play as audio") },
                                    leadingIcon = { MenuIcon(Icons.Default.Headphones, active = audioOnlyMode) },
                                    onClick = {
                                        viewModel.playerManager.setAudioOnlyModeEnabled(!audioOnlyMode)
                                        showMoreMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Pop-Up player") },
                                    leadingIcon = { MenuIcon(Icons.Default.PictureInPictureAlt) },
                                    onClick = {
                                        showMoreMenu = false
                                        if (Settings.canDrawOverlays(context)) {
                                            context.startService(Intent(context, FloatingPlayerService::class.java))
                                            // The overlay now owns the video surface; sending
                                            // this Activity to the background (rather than
                                            // finishing it) is what makes the floating
                                            // window's "expand" action able to bring the exact
                                            // same PhonePlayerScreen straight back.
                                            activity?.moveTaskToBack(true)
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Allow \"display over other apps\" to use the pop-up player",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            context.startActivity(
                                                Intent(
                                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    Uri.parse("package:${context.packageName}")
                                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Rotate screen") },
                                    leadingIcon = { MenuIcon(Icons.Default.ScreenRotation) },
                                    onClick = { activity?.let { toggleScreenOrientation(it) }; showMoreMenu = false }
                                )
                                // Stays open after a tap so the label visibly steps through
                                // Off -> One -> All without reopening the menu each time.
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when (repeatMode) {
                                                Player.REPEAT_MODE_ONE -> "Repeat mode: One"
                                                Player.REPEAT_MODE_ALL -> "Repeat mode: All"
                                                else -> "Repeat mode: Off"
                                            }
                                        )
                                    },
                                    leadingIcon = {
                                        MenuIcon(
                                            if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                            active = repeatMode != Player.REPEAT_MODE_OFF
                                        )
                                    },
                                    onClick = { viewModel.playerManager.cycleRepeatMode() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Video information") },
                                    leadingIcon = { MenuIcon(Icons.Default.Info) },
                                    onClick = { showVideoInfoDialog = true; showMoreMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Bookmarks") },
                                    leadingIcon = { MenuIcon(Icons.Default.BookmarkBorder) },
                                    onClick = { showBookmarksDialog = true; showMoreMenu = false }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when {
                                                abRepeat.pointAMs == null -> "A-B repeat: set start (A)"
                                                abRepeat.pointBMs == null -> "A-B repeat: set end (B)"
                                                else -> "A-B repeat: clear"
                                            }
                                        )
                                    },
                                    leadingIcon = { MenuIcon(Icons.Default.Loop, active = abRepeat.pointAMs != null) },
                                    onClick = {
                                        when {
                                            abRepeat.pointAMs == null -> viewModel.playerManager.setAbRepeatPointA()
                                            abRepeat.pointBMs == null -> viewModel.playerManager.setAbRepeatPointB()
                                            else -> viewModel.playerManager.clearAbRepeat()
                                        }
                                        showMoreMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Save Playlist") },
                                    leadingIcon = { MenuIcon(Icons.Default.PlaylistAdd) },
                                    onClick = { showSavePlaylistDialog = true; showMoreMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Control settings") },
                                    leadingIcon = { MenuIcon(Icons.Default.Settings) },
                                    onClick = { showControlSettingsDialog = true; showMoreMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Video player tips") },
                                    leadingIcon = { MenuIcon(Icons.Default.Lightbulb) },
                                    onClick = { showVideoTipsDialog = true; showMoreMenu = false }
                                )
                            }
                        }
                    }
                }

                // Fit / speed popups float just above the bottom bar they're triggered
                // from, rather than pushing the bar's own layout around.
                AnimatedVisibility(
                    visible = showFitMenu,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 110.dp, start = 16.dp)
                ) {
                    VideoFitMenu(
                        currentResizeMode = resizeMode,
                        onModeSelected = { mode ->
                            resizeMode = mode
                            showFitMenu = false
                        }
                    )
                }

                AnimatedVisibility(
                    visible = showSpeedMenu,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 110.dp, end = 16.dp)
                ) {
                    PlaybackSpeedMenu(
                        currentSpeed = playbackSpeed,
                        onSpeedSelected = { speed ->
                            viewModel.playerManager.setPlaybackSpeed(speed)
                            showSpeedMenu = false
                        }
                    )
                }
            }
        }
    }

    if (showAudioTrackDialog) {
        AudioTrackDialog(
            tracks = viewModel.playerManager.availableAudioTracks(),
            onDismiss = { showAudioTrackDialog = false },
            onTrackSelected = { groupIndex, trackIndex ->
                viewModel.playerManager.selectAudioTrack(groupIndex, trackIndex)
                showAudioTrackDialog = false
            }
        )
    }

    if (showSubtitleDialog) {
        SubtitleTrackDialog(
            tracks = viewModel.playerManager.availableSubtitleTracks(),
            onDismiss = { showSubtitleDialog = false },
            onTrackSelected = { groupIndex, trackIndex ->
                viewModel.playerManager.selectSubtitleTrack(groupIndex, trackIndex)
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
        // The equalizer can only attach once the player has a real audio session, so
        // (re)read its state every time the dialog opens rather than once at startup.
        LaunchedEffect(Unit) { viewModel.playerManager.refreshEqualizerState() }
        EqualizerDialog(
            state = equalizerState,
            onDismiss = { showEqualizerDialog = false },
            onEnabledChange = { viewModel.playerManager.setEqualizerEnabled(it) },
            onBandLevelChange = { band, level -> viewModel.playerManager.setEqualizerBandLevel(band, level) },
            onPresetSelected = { viewModel.playerManager.setEqualizerPreset(it) }
        )
    }

    if (showVideoInfoDialog) {
        VideoInfoDialog(
            videoFormat = viewModel.playerManager.currentVideoFormat(),
            audioFormat = viewModel.playerManager.currentAudioFormat(),
            onDismiss = { showVideoInfoDialog = false }
        )
    }

    if (showBookmarksDialog) {
        BookmarksDialog(
            bookmarks = bookmarks,
            currentPositionMs = currentPosition,
            onDismiss = { showBookmarksDialog = false },
            onAddBookmark = { label ->
                viewModel.addBookmark(mediaUrl, title, currentPosition, label.trim())
            },
            onJumpTo = { bookmark ->
                viewModel.playerManager.seekTo(bookmark.positionMs)
                showBookmarksDialog = false
            },
            onDelete = { viewModel.deleteBookmark(it) }
        )
    }

    if (showSavePlaylistDialog) {
        SavePlaylistDialog(
            playlists = uiState.playlists,
            onDismiss = { showSavePlaylistDialog = false },
            onAddToExisting = { playlist ->
                viewModel.addToExistingPlaylist(playlist.id, title, mediaUrl)
                Toast.makeText(context, "Added to ${playlist.name}", Toast.LENGTH_SHORT).show()
                showSavePlaylistDialog = false
            },
            onCreateNew = { name ->
                viewModel.createPlaylistAndAddItem(name.trim(), title, mediaUrl)
                Toast.makeText(context, "Saved to new playlist \"${name.trim()}\"", Toast.LENGTH_SHORT).show()
                showSavePlaylistDialog = false
            }
        )
    }

    if (showControlSettingsDialog) {
        val gestureSettings = uiState.appSettings
        ControlSettingsDialog(
            gestureSeekEnabled = gestureSettings.gestureSeekEnabled,
            gestureBrightnessVolumeEnabled = gestureSettings.gestureBrightnessVolumeEnabled,
            doubleTapSeekEnabled = gestureSettings.doubleTapSeekEnabled,
            onDismiss = { showControlSettingsDialog = false },
            onGestureSeekChange = { viewModel.setGestureSeekEnabled(it) },
            onGestureBrightnessVolumeChange = { viewModel.setGestureBrightnessVolumeEnabled(it) },
            onDoubleTapSeekChange = { viewModel.setDoubleTapSeekEnabled(it) }
        )
    }

    if (showVideoTipsDialog) {
        VideoPlayerTipsDialog(onDismiss = { showVideoTipsDialog = false })
    }
}

/** Menu icon that turns the accent color while its feature is active (repeat on,
 *  A-B loop set, audio-only, sleep timer running) — otherwise inherits the menu's
 *  normal icon color, which is why it reads LocalContentColor at the call site. */
@Composable
private fun MenuIcon(imageVector: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean = false) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = if (active) MaterialTheme.colorScheme.primary else LocalContentColor.current
    )
}

@Composable
private fun GestureHudPill(icon: androidx.compose.ui.graphics.vector.ImageVector, percent: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(vertical = 16.dp, horizontal = 16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Spacer(modifier = Modifier.size(8.dp))
        Text(text = "$percent%", color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun toggleScreenOrientation(activity: Activity) {
    activity.requestedOrientation = if (
        activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    ) {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    } else {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0))
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
