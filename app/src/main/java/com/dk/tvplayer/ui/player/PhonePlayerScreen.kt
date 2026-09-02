@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.dk.tvplayer.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.mediarouter.app.MediaRouteButton
import com.dk.tvplayer.MainActivity
import com.dk.tvplayer.PipState
import com.dk.tvplayer.ui.TvPlayerViewModel
import com.dk.tvplayer.ui.components.ErrorRetryBanner
import com.dk.tvplayer.ui.components.PlaybackSpeedMenu
import com.dk.tvplayer.ui.components.SleepTimerDialog
import com.dk.tvplayer.ui.components.SubtitleTrackDialog
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
    viewModel: TvPlayerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    val activePlayer by viewModel.playerManager.activePlayerFlow.collectAsState()
    var showControls by remember { mutableStateOf(true) }
    val isPlaying by viewModel.playerManager.isPlayingFlow.collectAsState()
    val currentPosition by viewModel.playerManager.currentPositionFlow.collectAsState()
    val duration by viewModel.playerManager.durationFlow.collectAsState()
    val playbackSpeed by viewModel.playerManager.playbackSpeedFlow.collectAsState()
    val playbackError by viewModel.playerManager.playbackErrorFlow.collectAsState()
    val isCastAvailable by viewModel.playerManager.isCastAvailableFlow.collectAsState()
    val isCasting by viewModel.playerManager.isCastingFlow.collectAsState()
    val sleepTimerRemaining by viewModel.playerManager.sleepTimerRemainingSecFlow.collectAsState()

    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }

    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

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
            // Reset window brightness override on exit so other screens aren't affected.
            activity?.window?.attributes = activity?.window?.attributes?.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(4000)
            showControls = false
        }
    }

    LaunchedEffect(showBrightnessHud) {
        if (showBrightnessHud) {
            delay(1200)
            showBrightnessHud = false
        }
    }

    LaunchedEffect(showVolumeHud) {
        if (showVolumeHud) {
            delay(1200)
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
            // coroutines. Using separate .pointerInput(...) modifiers for drag vs tap
            // caused them to intermittently steal events from each other; running them
            // together via coroutineScope is the pattern Compose expects for combining
            // multiple gesture detectors on the same target.
            .pointerInput(duration) {
                coroutineScope {
                    launch {
                        detectTapGestures(
                            onTap = { showControls = !showControls },
                            onDoubleTap = { offset ->
                                when {
                                    offset.x < containerWidthPx * 0.4f ->
                                        viewModel.playerManager.seekTo((currentPosition - 10_000).coerceAtLeast(0))
                                    offset.x > containerWidthPx * 0.6f ->
                                        viewModel.playerManager.seekTo((currentPosition + 10_000).coerceAtMost(duration))
                                    else ->
                                        viewModel.playerManager.togglePlayPause()
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
                                activeZone = when {
                                    offset.x < containerWidthPx * 0.4f -> GestureZone.BRIGHTNESS
                                    offset.x > containerWidthPx * 0.6f -> GestureZone.VOLUME
                                    else -> GestureZone.SCRUB
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
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView -> playerView.player = activePlayer },
            modifier = Modifier.fillMaxSize()
        )

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

        AnimatedVisibility(
            visible = showControls,
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
                            modifier = Modifier.weight(1f)
                        )

                        if (isCastAvailable && !castButtonFailed) {
                            AndroidView(
                                factory = { ctx ->
                                    try {
                                        MediaRouteButton(ctx).apply {
                                            CastButtonFactory.setUpMediaRouteButton(ctx, this)
                                        }
                                    } catch (t: Throwable) {
                                        // Cast framework wasn't actually ready despite the
                                        // availability precheck — degrade gracefully instead
                                        // of crashing the player screen.
                                        castButtonFailed = true
                                        android.widget.FrameLayout(ctx)
                                    }
                                }
                            )
                        }

                        IconButton(onClick = { showSubtitleDialog = true }) {
                            Icon(Icons.Default.ClosedCaption, contentDescription = "Subtitles", tint = Color.White)
                        }
                        IconButton(onClick = { showSpeedMenu = !showSpeedMenu }) {
                            Icon(Icons.Default.Speed, contentDescription = "Playback speed", tint = Color.White)
                        }
                        IconButton(onClick = { showSleepTimerDialog = true }) {
                            Icon(
                                Icons.Default.Bedtime,
                                contentDescription = "Sleep timer",
                                tint = if (sleepTimerRemaining != null) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }
                        IconButton(onClick = { (activity as? MainActivity)?.enterPip() }) {
                            Icon(Icons.Default.PictureInPictureAlt, contentDescription = "Picture in Picture", tint = Color.White)
                        }
                        IconButton(onClick = { activity?.let { toggleScreenOrientation(it) } }) {
                            Icon(
                                imageVector = Icons.Default.ScreenRotation,
                                contentDescription = "Rotate Screen",
                                tint = Color.White
                            )
                        }
                    }

                    AnimatedVisibility(visible = showSpeedMenu, enter = fadeIn(), exit = fadeOut()) {
                        PlaybackSpeedMenu(
                            currentSpeed = playbackSpeed,
                            onSpeedSelected = { speed ->
                                viewModel.playerManager.setPlaybackSpeed(speed)
                                showSpeedMenu = false
                            },
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(end = 16.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.playerManager.seekTo((currentPosition - 10_000).coerceAtLeast(0)) },
                        modifier = Modifier.size(54.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Rewind 10s",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.playerManager.togglePlayPause() },
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(56.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.playerManager.seekTo((currentPosition + 10_000).coerceAtMost(duration)) },
                        modifier = Modifier.size(54.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Fast Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
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
                            text = if (duration > 0) formatTime(duration) else "LIVE",
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
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
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
