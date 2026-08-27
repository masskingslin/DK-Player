package com.stream.tvplayer.ui.components

import android.graphics.Bitmap
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream

/**
 * VLC-style playback control bar for TV remotes.
 *
 * Layout:
 *  Primary row  - Stop, Prev, Play/Pause, Next, Loop, Shuffle, Volume(+mute), Playlist, More
 *  Secondary row (behind "More") - Audio track, Subtitles, Speed, Aspect ratio, Sleep timer, Snapshot, Lock
 *
 * Sliders are replaced with D-pad "press left/right while focused" steppers, since
 * a TV remote has no mouse to drag with - Volume, Speed and Sleep Timer all work this way.
 */
@Composable
fun TvPlayerControls(
    player: Player?,
    playerView: PlayerView?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenPlaylist: () -> Unit,
    isLocked: Boolean,
    onToggleLock: () -> Unit,
    shuffleEnabled: Boolean,
    onToggleShuffle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var volumePercent by remember { mutableStateOf(100) }
    var repeatMode by remember { mutableStateOf(Player.REPEAT_MODE_OFF) }
    var showMore by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(1.0f) }
    var resizeModeIndex by remember { mutableStateOf(0) }
    var sleepMinutes by remember { mutableStateOf(0) } // 0 = off
    var audioTrackLabel by remember { mutableStateOf("Auto") }
    var subtitleLabel by remember { mutableStateOf("Off") }
    var snapshotMessage by remember { mutableStateOf<String?>(null) }

    val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Fill"
    )

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player?.addListener(listener)
        isPlaying = player?.isPlaying ?: true
        onDispose { player?.removeListener(listener) }
    }

    // Sleep timer countdown
    LaunchedEffect(sleepMinutes) {
        if (sleepMinutes > 0) {
            delay(sleepMinutes * 60_000L)
            player?.pause()
            sleepMinutes = 0
        }
    }

    // Snapshot confirmation auto-clears
    LaunchedEffect(snapshotMessage) {
        if (snapshotMessage != null) {
            delay(2000)
            snapshotMessage = null
        }
    }

    fun adjustVolume(increase: Boolean) {
        volumePercent = (volumePercent + if (increase) 5 else -5).coerceIn(0, 100)
        isMuted = volumePercent == 0
        player?.volume = volumePercent / 100f
    }

    fun toggleMute() {
        isMuted = !isMuted
        player?.volume = if (isMuted) 0f else volumePercent / 100f
    }

    fun cycleRepeat() {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player?.repeatMode = repeatMode
    }

    fun adjustSpeed(increase: Boolean) {
        val steps = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        val currentIndex = steps.indexOfFirst { it == speed }.coerceAtLeast(0)
        val newIndex = (currentIndex + if (increase) 1 else -1).coerceIn(0, steps.lastIndex)
        speed = steps[newIndex]
        player?.setPlaybackSpeed(speed)
    }

    fun cycleAspectRatio() {
        resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
        playerView?.resizeMode = resizeModes[resizeModeIndex].first
    }

    fun adjustSleepTimer(increase: Boolean) {
        val steps = listOf(0, 15, 30, 45, 60, 90)
        val currentIndex = steps.indexOf(sleepMinutes).coerceAtLeast(0)
        val newIndex = (currentIndex + if (increase) 1 else -1).coerceIn(0, steps.lastIndex)
        sleepMinutes = steps[newIndex]
    }

    fun cycleAudioTrack() {
        val p = player ?: return
        val groups = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
        if (groups.isEmpty()) return
        // Find current selection, move to next track (wrap to "Auto" / clear override at the end).
        val flatTracks = groups.flatMapIndexed { gi, g -> (0 until g.length).map { ti -> gi to ti } }
        val selectedFlatIndex = flatTracks.indexOfFirst { (gi, ti) -> groups[gi].isTrackSelected(ti) }
        val nextFlatIndex = (selectedFlatIndex + 1) % (flatTracks.size + 1) // +1 slot = "Auto"

        if (nextFlatIndex == flatTracks.size) {
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .build()
            audioTrackLabel = "Auto"
        } else {
            val (gi, ti) = flatTracks[nextFlatIndex]
            val group = groups[gi]
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, ti))
                .build()
            val format = group.getTrackFormat(ti)
            audioTrackLabel = format.language ?: format.label ?: "Track ${gi + 1}"
        }
    }

    fun cycleSubtitleTrack() {
        val p = player ?: return
        val groups = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
        if (groups.isEmpty()) {
            subtitleLabel = "None"
            return
        }
        val flatTracks = groups.flatMapIndexed { gi, g -> (0 until g.length).map { ti -> gi to ti } }
        val selectedFlatIndex = flatTracks.indexOfFirst { (gi, ti) -> groups[gi].isTrackSelected(ti) }
        val nextFlatIndex = selectedFlatIndex + 1 // -1 (off) -> 0 -> 1 -> ... -> off

        if (nextFlatIndex >= flatTracks.size) {
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .build()
            subtitleLabel = "Off"
        } else {
            val (gi, ti) = flatTracks[nextFlatIndex]
            val group = groups[gi]
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, ti))
                .build()
            val format = group.getTrackFormat(ti)
            subtitleLabel = format.language ?: format.label ?: "Track ${gi + 1}"
        }
    }

    fun takeSnapshot() {
        // Requires PlayerView to be using a SurfaceView (the default surface_type).
        // If the project's PlayerView is configured with surface_type="texture_view",
        // this needs TextureView.getBitmap() instead - swap that in if snapshots come back blank.
        val surfaceView = playerView?.videoSurfaceView as? android.view.SurfaceView
        if (playerView == null || surfaceView == null) {
            snapshotMessage = "Snapshot unavailable"
            return
        }
        val bmp = Bitmap.createBitmap(playerView.width, playerView.height, Bitmap.Config.ARGB_8888)
        try {
            android.view.PixelCopy.request(
                surfaceView,
                bmp,
                { result ->
                    if (result == android.view.PixelCopy.SUCCESS) {
                        try {
                            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                            val file = File(dir, "snapshot_${System.currentTimeMillis()}.png")
                            FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
                            snapshotMessage = "Saved to ${file.name}"
                        } catch (e: Exception) {
                            snapshotMessage = "Snapshot failed"
                        }
                    } else {
                        snapshotMessage = "Snapshot failed"
                    }
                },
                android.os.Handler(android.os.Looper.getMainLooper())
            )
        } catch (e: Exception) {
            snapshotMessage = "Snapshot failed"
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {

        snapshotMessage?.let {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xE6000000))
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = it, color = Color.White, fontSize = 13.sp)
            }
        }

        if (showMore) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xB3000000))
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LabeledButton(symbol = "🎧", label = audioTrackLabel, description = "Audio track", onClick = { cycleAudioTrack() })
                Spacer(28.dp)
                LabeledButton(symbol = "💬", label = subtitleLabel, description = "Subtitles", onClick = { cycleSubtitleTrack() })
                Spacer(28.dp)
                LabeledButton(
                    symbol = "⏩",
                    label = "${speed}x",
                    description = "Playback speed",
                    onClick = {},
                    onAdjust = { increase -> adjustSpeed(increase) }
                )
                Spacer(28.dp)
                LabeledButton(symbol = "⬛", label = resizeModes[resizeModeIndex].second, description = "Aspect ratio", onClick = { cycleAspectRatio() })
                Spacer(28.dp)
                LabeledButton(
                    symbol = "⏱",
                    label = if (sleepMinutes == 0) "Off" else "${sleepMinutes}m",
                    description = "Sleep timer",
                    onClick = {},
                    onAdjust = { increase -> adjustSleepTimer(increase) }
                )
                Spacer(28.dp)
                LabeledButton(symbol = "📷", label = "Snapshot", description = "Take snapshot", onClick = { takeSnapshot() })
                Spacer(28.dp)
                LabeledButton(symbol = "🔒", label = "Lock", description = "Lock screen", onClick = onToggleLock)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xCC000000))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton(
                symbol = "■",
                contentDescription = "Stop",
                enabled = !isLocked,
                onClick = {
                    player?.stop()
                    player?.seekTo(0)
                }
            )
            Spacer(20.dp)
            ControlButton(symbol = "⏮", contentDescription = "Previous channel", enabled = !isLocked, onClick = onPrevious)
            Spacer(20.dp)
            ControlButton(
                symbol = if (isPlaying) "⏸" else "▶",
                contentDescription = if (isPlaying) "Pause" else "Play",
                primary = true,
                enabled = !isLocked,
                onClick = {
                    if (player?.isPlaying == true) player.pause() else player?.play()
                }
            )
            Spacer(20.dp)
            ControlButton(symbol = "⏭", contentDescription = "Next channel", enabled = !isLocked, onClick = onNext)
            Spacer(20.dp)
            ControlButton(
                symbol = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> "🔂"
                    Player.REPEAT_MODE_ALL -> "🔁"
                    else -> "➡"
                },
                contentDescription = "Repeat mode",
                enabled = !isLocked,
                onClick = { cycleRepeat() }
            )
            Spacer(20.dp)
            ControlButton(
                symbol = "🔀",
                contentDescription = "Shuffle",
                enabled = !isLocked,
                highlighted = shuffleEnabled,
                onClick = onToggleShuffle
            )
            Spacer(28.dp)
            LabeledButton(
                symbol = if (isMuted || volumePercent == 0) "🔇" else "🔊",
                label = "$volumePercent%",
                description = "Volume, use Left/Right to adjust",
                enabled = !isLocked,
                onClick = { toggleMute() },
                onAdjust = { increase -> adjustVolume(increase) }
            )
            Spacer(28.dp)
            ControlButton(symbol = "☰", contentDescription = "Playlist", enabled = !isLocked, onClick = onOpenPlaylist)
            Spacer(20.dp)
            ControlButton(
                symbol = if (showMore) "▲" else "⋯",
                contentDescription = "More controls",
                enabled = !isLocked,
                onClick = { showMore = !showMore }
            )
            Spacer(20.dp)
            ControlButton(
                symbol = if (isLocked) "🔒" else "🔓",
                contentDescription = "Lock",
                highlighted = isLocked,
                onClick = onToggleLock
            )
        }
    }
}

@Composable
private fun RowScope.Spacer(width: androidx.compose.ui.unit.Dp) {
    Box(modifier = Modifier.size(width = width, height = 1.dp))
}

@Composable
private fun ControlButton(
    symbol: String,
    contentDescription: String,
    primary: Boolean = false,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val size = if (primary) 56.dp else 44.dp
    val fontSize = if (primary) 26.sp else 20.sp

    val backgroundColor = when {
        isFocused -> Color(0xFFFFD54F)
        highlighted -> Color(0xFF4C6EF5)
        primary -> Color(0x33FFFFFF)
        else -> Color(0x1AFFFFFF)
    }
    val symbolColor = if (isFocused) Color.Black else Color.White

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .clickableNoRipple(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = symbol, color = symbolColor, fontSize = fontSize, fontWeight = if (primary) FontWeight.Bold else FontWeight.Normal)
    }
}

/** A button with a small value label underneath, optionally D-pad-adjustable left/right while focused. */
@Composable
private fun LabeledButton(
    symbol: String,
    label: String,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onAdjust: ((Boolean) -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val backgroundColor = if (isFocused) Color(0xFFFFD54F) else Color(0x1AFFFFFF)
    val symbolColor = if (isFocused) Color.Black else Color.White

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(backgroundColor)
                .focusable(enabled = enabled, interactionSource = interactionSource)
                .onKeyEvent { event ->
                    if (onAdjust != null && event.key == Key.DirectionRight) {
                        onAdjust(true); true
                    } else if (onAdjust != null && event.key == Key.DirectionLeft) {
                        onAdjust(false); true
                    } else false
                }
                .clickableNoRipple(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(text = symbol, color = symbolColor, fontSize = 18.sp)
        }
        Text(text = label, color = Color(0xFFCCCCCC), fontSize = 11.sp)
    }
}

@Composable
private fun Modifier.clickableNoRipple(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    this.then(
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
    )
