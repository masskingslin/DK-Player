package com.stream.tvplayer.ui.screens

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.tv.material3.*
import com.stream.tvplayer.ui.TvPlayerViewModel
import com.stream.tvplayer.ui.components.TvEpgOverlay
import com.stream.tvplayer.ui.components.TvPlayerSurface

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMainScreen(viewModel: TvPlayerViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }

    // Intercept back button to close drawer first
    BackHandler(enabled = uiState.isSidebarOpen) {
        viewModel.toggleSidebar(open = false)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

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
            modifier = Modifier.fillMaxSize()
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

                    TvLazyColumn(
                        LazyColumn(
    verticalArrangement = Arrangement.spacedBy(6.dp),
    modifier = Modifier.fillMaxSize()
) {
    itemsIndexed(uiState.channels) { index, channel ->
        val isSelected = index == uiState.currentChannelIndex
        DenseListItem(
            selected = isSelected,
            onClick = {
                viewModel.selectChannel(index)
                viewModel.toggleSidebar(open = false)
            },
            headlineContent = {
                Text(
                    text = "${channel.channelNumber}. ${channel.name}",
                    color = if (isSelected) Color(0xFFFFD54F) else Color.White
                )
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