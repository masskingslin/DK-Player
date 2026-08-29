package com.dk.tvplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.dk.tvplayer.ui.TvPlayerViewModel
import com.dk.tvplayer.ui.TvUiState
import com.dk.tvplayer.ui.components.PlaybackSpeedDialog
import com.dk.tvplayer.ui.components.SleepTimerDialog
import com.dk.tvplayer.ui.components.SubtitleSelectionDialog
import com.dk.tvplayer.util.handlePlaybackShortcuts

@Composable
fun PhonePlayerScreen(
    viewModel: TvPlayerViewModel,
    uiState: TvUiState,
    onBack: () -> Unit,
    onEnterPip: () -> Unit
) {
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .handlePlaybackShortcuts(
                onPlayPauseToggle = {
                    if (viewModel.playerManager.player.isPlaying) {
                        viewModel.playerManager.player.pause()
                    } else {
                        viewModel.playerManager.player.play()
                    }
                },
                onSeekForward = { viewModel.playerManager.player.seekForward() },
                onSeekBackward = { viewModel.playerManager.player.seekBack() },
                onNextChannel = { /* Next channel handler */ },
                onPreviousChannel = { /* Prev channel handler */ },
                onMuteToggle = {
                    val p = viewModel.playerManager.player
                    p.volume = if (p.volume > 0f) 0f else 1f
                },
                onPipToggle = onEnterPip
            )
    ) {
        // Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.playerManager.player
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Error message banner
        if (uiState.playerError != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = uiState.playerError,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // Overlay controls - Hidden in Picture-in-Picture mode
        if (!uiState.isInPipMode) {
            AnimatedVisibility(visible = controlsVisible, modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.TopStart),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Text(
                            text = uiState.currentlyPlayingChannel?.name ?: "DK-Player",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showSubtitleDialog = true }) {
                            Icon(Icons.Default.ClosedCaption, contentDescription = "Subtitles", tint = Color.White)
                        }
                        IconButton(onClick = { showSpeedDialog = true }) {
                            Icon(Icons.Default.Speed, contentDescription = "Speed", tint = Color.White)
                        }
                        IconButton(onClick = { showSleepDialog = true }) {
                            Icon(Icons.Default.Timer, contentDescription = "Sleep Timer", tint = Color.White)
                        }
                        IconButton(onClick = onEnterPip) {
                            Icon(Icons.Default.PictureInPicture, contentDescription = "PiP", tint = Color.White)
                        }
                    }

                    // Center playback controls
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.playerManager.player.seekBack() }) {
                            Icon(Icons.Default.Replay10, contentDescription = "Back 10s", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        IconButton(onClick = {
                            if (viewModel.playerManager.player.isPlaying) {
                                viewModel.playerManager.player.pause()
                            } else {
                                viewModel.playerManager.player.play()
                            }
                        }) {
                            Icon(
                                if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        IconButton(onClick = { viewModel.playerManager.player.seekForward() }) {
                            Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showSubtitleDialog) {
        SubtitleSelectionDialog(
            subtitleTracks = uiState.subtitleTracks,
            onSelectTrack = { g, t -> viewModel.playerManager.selectSubtitleTrack(g, t) },
            onDisableSubtitles = { viewModel.playerManager.disableSubtitles() },
            onDismiss = { showSubtitleDialog = false }
        )
    }

    if (showSpeedDialog) {
        PlaybackSpeedDialog(
            currentSpeed = uiState.playbackSpeed,
            onSpeedSelected = { viewModel.setPlaybackSpeed(it) },
            onDismiss = { showSpeedDialog = false }
        )
    }

    if (showSleepDialog) {
        SleepTimerDialog(
            currentMinutes = uiState.remainingSleepSeconds,
            onSetTimer = { viewModel.setSleepTimer(it) },
            onCancelTimer = { viewModel.setSleepTimer(0) },
            onDismiss = { showSleepDialog = false }
        )
    }
}
