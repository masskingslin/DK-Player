package com.dk.tvplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dk.tvplayer.data.local.TvChannelEntity
import com.dk.tvplayer.ui.TvPlayerViewModel
import com.dk.tvplayer.ui.components.ErrorRetryBanner
import com.dk.tvplayer.ui.components.TvEpgOverlay
import com.dk.tvplayer.ui.components.TvPlayerControls
import com.dk.tvplayer.ui.components.TvPlayerSurface
import com.dk.tvplayer.ui.library.SortMenuButton
import com.dk.tvplayer.ui.theme.DkShapes

@Composable
fun TvMainScreen(viewModel: TvPlayerViewModel) {
    val state by viewModel.uiState.collectAsState()
    val isPlaying by viewModel.playerManager.isPlayingFlow.collectAsState()
    val playbackError by viewModel.playerManager.playbackErrorFlow.collectAsState()
    val focusRequester = remember { FocusRequester() }

    fun goToNextChannel() {
        val channels = state.filteredChannels
        val currentIndex = channels.indexOfFirst { it.id == state.selectedChannel?.id }
        if (currentIndex != -1 && currentIndex + 1 < channels.size) {
            viewModel.selectChannel(channels[currentIndex + 1])
        }
    }

    fun goToPreviousChannel() {
        val channels = state.filteredChannels
        val currentIndex = channels.indexOfFirst { it.id == state.selectedChannel?.id }
        if (currentIndex > 0) {
            viewModel.selectChannel(channels[currentIndex - 1])
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                // Remote-control / attached-keyboard shortcuts for TV playback.
                when (keyEvent.key) {
                    Key.Spacebar, Key.MediaPlayPause, Key.DirectionCenter, Key.Enter -> {
                        viewModel.playerManager.togglePlayPause(); true
                    }
                    Key.ChannelUp, Key.PageUp, Key.MediaNext -> {
                        goToNextChannel(); true
                    }
                    Key.ChannelDown, Key.PageDown, Key.MediaPrevious -> {
                        goToPreviousChannel(); true
                    }
                    Key.DirectionLeft, Key.MediaRewind -> {
                        val pos = viewModel.playerManager.currentPositionFlow.value
                        viewModel.playerManager.seekTo((pos - 10_000).coerceAtLeast(0)); true
                    }
                    Key.DirectionRight, Key.MediaFastForward -> {
                        val pos = viewModel.playerManager.currentPositionFlow.value
                        val dur = viewModel.playerManager.durationFlow.value
                        viewModel.playerManager.seekTo((pos + 10_000).coerceAtMost(dur)); true
                    }
                    else -> false
                }
            }
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )
                .padding(16.dp)
        ) {
            Text(
                text = "DK-Player TV",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Search channel...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                SortMenuButton(current = state.sortOption, onSelected = { viewModel.setSortOption(it) })
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
            ) {
                items(state.categories) { category ->
                    CategoryChip(
                        label = category,
                        isSelected = state.selectedCategory == category,
                        onClick = { viewModel.selectCategory(category) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(state.filteredChannels) { channel ->
                    TvChannelRow(
                        channel = channel,
                        isSelected = state.selectedChannel?.id == channel.id,
                        onSelect = { viewModel.selectChannel(channel) }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color.Black)
        ) {
            TvPlayerSurface(
                exoPlayer = viewModel.playerManager.exoPlayer,
                modifier = Modifier.fillMaxSize()
            )

            TvEpgOverlay(
                channel = state.selectedChannel,
                programs = state.currentEpgPrograms,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            playbackError?.let { errorMessage ->
                ErrorRetryBanner(
                    message = errorMessage,
                    onRetry = { viewModel.playerManager.retryPlayback() },
                    onDismiss = { viewModel.playerManager.clearError() },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            TvPlayerControls(
                isPlaying = isPlaying,
                onPlayPause = { viewModel.playerManager.togglePlayPause() },
                onNextChannel = { goToNextChannel() },
                onPreviousChannel = { goToPreviousChannel() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/**
 * A sidebar category filter styled as a pill chip. TV navigation is driven entirely
 * by D-pad focus rather than touch, so the focused-but-not-yet-selected state needs
 * its own clearly visible treatment (an outline + slight scale-up) distinct from the
 * "currently selected" filled state — otherwise a user moving the D-pad has no idea
 * which row they're about to press.
 */
@Composable
private fun CategoryChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.03f else 1f, label = "categoryScale")
    val containerColor by animateColorAsState(
        when {
            isSelected -> MaterialTheme.colorScheme.primary
            isFocused -> MaterialTheme.colorScheme.surfaceVariant
            else -> Color.Transparent
        },
        label = "categoryContainer"
    )
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Text(
        text = label,
        style = if (isSelected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
        color = contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(DkShapes.pill)
            .background(containerColor)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
fun TvChannelRow(
    channel: TvChannelEntity,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.02f else 1f, label = "channelScale")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = DkShapes.medium
            )
            .clickable(onClick = onSelect),
        shape = DkShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                isFocused -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused) 4.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = channel.groupTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
