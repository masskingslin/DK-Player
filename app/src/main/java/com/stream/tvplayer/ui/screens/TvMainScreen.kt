package com.stream.tvplayer.ui.screens

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.tv.material3.*
import com.stream.tvplayer.data.local.ChannelEntity
import com.stream.tvplayer.ui.TvPlayerViewModel
import com.stream.tvplayer.ui.components.TvEpgOverlay
import com.stream.tvplayer.ui.components.TvPlayerControls
import com.stream.tvplayer.ui.components.TvPlayerSurface

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMainScreen(viewModel: TvPlayerViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    var player by remember { mutableStateOf<Player?>(null) }
    var playerView by remember { mutableStateOf<androidx.media3.ui.PlayerView?>(null) }
    var isScreenLocked by remember { mutableStateOf(false) }

    // Intercept back button to close drawer first
    BackHandler(enabled = uiState.isSidebarOpen) {
        viewModel.toggleSidebar(open = false)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                // While locked, disable channel/sidebar shortcuts but let normal D-pad focus
                // movement and clicks (e.g. reaching and pressing the Lock button) work as usual.
                if (isScreenLocked) return@onPreviewKeyEvent false

                when (keyEvent.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER -> {
                        if (!uiState.isSidebarOpen) {
                            viewModel.toggleOverlay()
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_MENU -> {
                        if (!uiState.isSidebarOpen) {
                            viewModel.toggleSidebar(open = true)
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (uiState.isSidebarOpen) {
                            viewModel.toggleSidebar(open = false)
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_CHANNEL_UP -> {
                        if (!uiState.isSidebarOpen) {
                            viewModel.prevChannel()
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        if (!uiState.isSidebarOpen) {
                            viewModel.nextChannel()
                            true
                        } else false
                    }
                    else -> false
                }
            }
    ) {
        // Video Surface
        TvPlayerSurface(
            streamUrl = uiState.currentChannel?.streamUrl,
            licenseServerUrl = uiState.currentChannel?.licenseServerUrl,
            modifier = Modifier.fillMaxSize(),
            onPlayerReady = { player = it },
            onPlayerViewReady = { playerView = it }
        )

        // Leanback EPG Overlay
        uiState.currentChannel?.let { activeChannel ->
            TvEpgOverlay(
                channel = activeChannel,
                currentProgram = uiState.currentProgram,
                nextProgram = uiState.nextProgram,
                visible = uiState.isOverlayVisible && !uiState.isSidebarOpen,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // VLC-style playback controls: previous / play-pause / next / mute / playlist.
        // Shares the same visibility + auto-hide timing as the EPG overlay.
        AnimatedVisibility(
            visible = (uiState.isOverlayVisible || isScreenLocked) && !uiState.isSidebarOpen,
            enter = slideInHorizontally { 0 },
            exit = slideOutHorizontally { 0 },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            TvPlayerControls(
                player = player,
                playerView = playerView,
                onPrevious = { viewModel.prevChannel() },
                onNext = { viewModel.nextChannel() },
                onOpenPlaylist = { viewModel.toggleSidebar(open = true) },
                isLocked = isScreenLocked,
                onToggleLock = { isScreenLocked = !isScreenLocked },
                shuffleEnabled = uiState.shuffleEnabled,
                onToggleShuffle = { viewModel.toggleShuffle() }
            )
        }

        // Side Navigation Drawer
        AnimatedVisibility(
            visible = uiState.isSidebarOpen,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            Surface(
                colors = SurfaceDefaults.colors(containerColor = Color(0xF2101010)),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(360.dp)
                    .focusRequester(focusRequester)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "TV Guide",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // --- Search field ---
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        BasicTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            cursorBrush = SolidColor(Color.White),
                            decorationBox = { inner ->
                                if (uiState.searchQuery.isEmpty()) {
                                    Text("Search channels…", color = Color.Gray, fontSize = 14.sp)
                                }
                                inner()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // --- Category + Favorites filter chips ---
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                label = "All",
                                selected = uiState.selectedCategory == null && !uiState.showFavoritesOnly,
                                onClick = {
                                    viewModel.selectCategory(null)
                                    if (uiState.showFavoritesOnly) viewModel.toggleShowFavoritesOnly()
                                }
                            )
                        }
                        item {
                            FilterChip(
                                label = "★ Favorites",
                                selected = uiState.showFavoritesOnly,
                                onClick = { viewModel.toggleShowFavoritesOnly() }
                            )
                        }
                        items(uiState.categories) { category ->
                            FilterChip(
                                label = category,
                                selected = uiState.selectedCategory == category,
                                onClick = {
                                    viewModel.selectCategory(
                                        if (uiState.selectedCategory == category) null else category
                                    )
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // --- Channel list (search + category + favorites applied) ---
                    val visibleChannels = uiState.filteredChannels

                    if (visibleChannels.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "No channels found", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(visibleChannels, key = { it.id }) { channel ->
                                val isSelected = channel == uiState.currentChannel
                                val isFavorite = uiState.favoriteChannelIds.contains(channel.id)

                                DenseListItem(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.selectChannel(channel)
                                        viewModel.toggleSidebar(open = false)
                                    },
                                    headlineContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (isFavorite) "★ " else "☆ ",
                                                color = if (isFavorite) Color(0xFFFFD54F) else Color.Gray,
                                                fontSize = 14.sp,
                                                modifier = Modifier.clickable {
                                                    viewModel.toggleFavorite(channel.id)
                                                }
                                            )
                                            Text(
                                                text = "${channel.channelNumber}. ${channel.name}",
                                                color = if (isSelected) Color(0xFFFFD54F) else Color.White
                                            )
                                        }
                                    },
                                    supportingContent = channel.groupName?.let {
                                        { Text(text = it, color = Color.Gray, fontSize = 12.sp) }
                                    },
                                    colors = ListItemDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedContainerColor = Color(0x22FFFFFF)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bg = when {
        isFocused -> Color(0xFFFFD54F)
        selected -> Color(0xFF4C6EF5)
        else -> Color(0x1AFFFFFF)
    }
    val textColor = if (isFocused) Color.Black else Color.White
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(16.dp))
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(text = label, color = textColor, fontSize = 13.sp)
    }
}