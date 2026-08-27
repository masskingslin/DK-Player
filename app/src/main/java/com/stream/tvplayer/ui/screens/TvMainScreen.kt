package com.stream.tvplayer.ui.screens

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stream.tvplayer.player.TvExoPlayerManager
import com.stream.tvplayer.ui.TvPlayerViewModel
import com.stream.tvplayer.ui.components.TvEpgOverlay
import com.stream.tvplayer.ui.components.TvPlayerControls
import com.stream.tvplayer.ui.components.TvPlayerSurface

@Composable
fun TvMainScreen(viewModel: TvPlayerViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val playerManager = remember {
        TvExoPlayerManager(context).apply { initialize() }
    }

    DisposableEffect(Unit) {
        onDispose { playerManager.release() }
    }

    LaunchedEffect(uiState.currentChannel?.streamUrl) {
        uiState.currentChannel?.let { channel ->
            playerManager.playStream(channel.streamUrl)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            viewModel.toggleOverlay()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (!uiState.isSidebarOpen) {
                                viewModel.toggleSidebar(true)
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                            viewModel.prevChannel()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                            viewModel.nextChannel()
                            true
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            if (uiState.isSidebarOpen) {
                                viewModel.toggleSidebar(false)
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
    ) {
        TvPlayerSurface(player = playerManager.exoPlayer)

        // Overlay: Controls & EPG
        AnimatedVisibility(
            visible = uiState.isOverlayVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                TvEpgOverlay(
                    channel = uiState.currentChannel,
                    currentProgram = uiState.currentProgram,
                    nextProgram = uiState.nextProgram,
                    modifier = Modifier.align(Alignment.BottomStart)
                )

                TvPlayerControls(
                    isPlaying = playerManager.exoPlayer?.isPlaying ?: true,
                    isShuffle = uiState.shuffleEnabled,
                    isFavorite = uiState.currentChannel?.id?.let { uiState.favoriteChannelIds.contains(it) } ?: false,
                    onPrevious = { viewModel.prevChannel() },
                    onPlayPause = {
                        val player = playerManager.exoPlayer
                        if (player != null) {
                            if (player.isPlaying) player.pause() else player.play()
                        }
                    },
                    onNext = { viewModel.nextChannel() },
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onToggleFavorite = {
                        uiState.currentChannel?.id?.let { viewModel.toggleFavorite(it) }
                    },
                    onToggleSidebar = { viewModel.toggleSidebar() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 140.dp)
                )
            }
        }

        // Slide-out Channel Sidebar
        AnimatedVisibility(
            visible = uiState.isSidebarOpen,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it }),
            modifier = Modifier.fillMaxHeight()
        ) {
            Surface(
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight(),
                color = Color.Black.copy(alpha = 0.92f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Channels",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        IconButton(onClick = { viewModel.toggleSidebar(false) }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Sidebar", tint = Color.White)
                        }
                    }

                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = { Text("Search channel or number...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Categories Row
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = uiState.selectedCategory == null,
                                onClick = { viewModel.selectCategory(null) },
                                label = { Text("All") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = uiState.showFavoritesOnly,
                                onClick = { viewModel.toggleShowFavoritesOnly() },
                                label = { Text("Favorites") },
                                leadingIcon = {
                                    Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                        items(uiState.categories) { category ->
                            FilterChip(
                                selected = uiState.selectedCategory == category,
                                onClick = { viewModel.selectCategory(category) },
                                label = { Text(category) }
                            )
                        }
                    }

                    // Channel List
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.filteredChannels) { channel ->
                            val isSelected = channel.id == uiState.currentChannel?.id
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectChannel(channel)
                                        viewModel.toggleSidebar(false)
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color(0xFF1E1E1E)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.DarkGray)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${channel.channelNumber}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Text(
                                        text = channel.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
