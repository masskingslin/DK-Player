package com.dk.tvplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AdvancedPlayerControlsPanel(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    playbackSpeed: Float,
    onSpeedChanged: (Float) -> Unit,
    isMuted: Boolean,
    onMuteClick: () -> Unit,
    isPipEnabled: Boolean,
    onPipClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSubtitlesClick: () -> Unit,
    onCastClick: () -> Unit,
    showAdvancedOptions: Boolean = true
) {
    var showSpeedMenu by remember { mutableStateOf(false) }
    val speedOptions = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPlayPauseClick, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Box {
                Button(
                    onClick = { showSpeedMenu = !showSpeedMenu },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("${String.format("%.2f", playbackSpeed)}x", color = Color.White)
                }

                DropdownMenu(
                    expanded = showSpeedMenu,
                    onDismissRequest = { showSpeedMenu = false }
                ) {
                    speedOptions.forEach { speed ->
                        DropdownMenuItem(
                            text = { Text("${String.format("%.2f", speed)}x") },
                            onClick = {
                                onSpeedChanged(speed)
                                showSpeedMenu = false
                            }
                        )
                    }
                }
            }

            IconButton(onClick = onMuteClick, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(
                onClick = onSubtitlesClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.ClosedCaption,
                    contentDescription = "Subtitles",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(onClick = onPipClick, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (isPipEnabled) Icons.Default.PictureInPictureAlt else Icons.Default.PictureInPicture,
                    contentDescription = "Picture in Picture",
                    tint = if (isPipEnabled) Color.Green else Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(onClick = onCastClick, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.Cast,
                    contentDescription = "Chromecast",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            if (showAdvancedOptions) {
                IconButton(onClick = onSettingsClick, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PlaybackSpeedSelector(
    modifier: Modifier = Modifier,
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    Column(modifier = modifier) {
        Text("Playback Speed", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.DarkGray, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            speeds.forEach { speed ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSpeedSelected(speed) }
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(String.format("%.2f", speed) + "x", color = Color.White)
                    if (currentSpeed == speed) {
                        Icon(Icons.Default.Check, "Selected", tint = Color.Green)
                    }
                }
            }
        }
    }
}

@Composable
fun SubtitleControlPanel(
    modifier: Modifier = Modifier,
    availableSubtitles: List<String>,
    selectedSubtitle: String?,
    onSubtitleSelected: (String?) -> Unit,
    onSubtitleSizeChanged: (Float) -> Unit,
    subtitleSize: Float = 1.0f
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text("Subtitles", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(12.dp))

        if (availableSubtitles.isEmpty()) {
            Text("No subtitles available", style = MaterialTheme.typography.labelSmall)
        } else {
            availableSubtitles.forEach { subtitle ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSubtitleSelected(subtitle) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedSubtitle == subtitle,
                        onClick = { onSubtitleSelected(subtitle) }
                    )
                    Text(subtitle, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Subtitle Size", style = MaterialTheme.typography.labelSmall)
        Slider(
            value = subtitleSize,
            onValueChange = { onSubtitleSizeChanged(it) },
            valueRange = 0.5f..2.0f,
            steps = 9,
            modifier = Modifier.fillMaxWidth()
        )
        Text("${String.format("%.1f", subtitleSize)}x", style = MaterialTheme.typography.labelSmall)
    }
}
