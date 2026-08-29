package com.dk.tvplayer.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dk.tvplayer.ui.home.HistoryAndStreamsScreen
import com.dk.tvplayer.ui.library.AudioLibraryScreen
import com.dk.tvplayer.ui.library.PlaylistManagementScreen
import com.dk.tvplayer.ui.library.VideoLibraryScreen
import com.dk.tvplayer.ui.player.PhonePlayerScreen
import com.dk.tvplayer.ui.settings.SettingsScreen

sealed class PhoneScreen(val route: String, val title: String, val icon: ImageVector) {
    data object Videos : PhoneScreen("videos", "Videos", Icons.Default.Folder)
    data object Audio : PhoneScreen("audio", "Audio", Icons.Default.MusicNote)
    data object Playlists : PhoneScreen("playlists", "Playlists", Icons.Default.PlaylistPlay)
    data object Settings : PhoneScreen("settings", "Settings", Icons.Default.Settings)
}

private const val PLAYER_ROUTE = "player"
private const val HISTORY_STREAMS_ROUTE = "history_streams"

@Composable
fun PhoneAppRoot(
    viewModel: TvPlayerViewModel,
    uiState: TvUiState,
    onEnterPip: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val items = listOf(
        PhoneScreen.Videos,
        PhoneScreen.Audio,
        PhoneScreen.Playlists,
        PhoneScreen.Settings
    )

    // Starts playback of a raw media url/title (local video, local audio, or a
    // custom/history stream) and navigates to the player screen, which reads
    // the currently-playing item back off the player manager / view model.
    val playMedia: (String, String) -> Unit = { url, title ->
        viewModel.playerManager.playStream(url)
        viewModel.recordHistory(title, url)
        navController.navigate(PLAYER_ROUTE)
    }

    Scaffold(
        bottomBar = {
            val currentRoute = currentDestination?.route
            if (currentRoute == null || currentRoute == PLAYER_ROUTE) return@Scaffold
            NavigationBar {
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.route == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = PhoneScreen.Videos.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(PhoneScreen.Videos.route) {
                VideoLibraryScreen(
                    viewModel = viewModel,
                    onPlayVideo = { url, title -> playMedia(url, title) }
                )
            }
            composable(PhoneScreen.Audio.route) {
                AudioLibraryScreen(
                    viewModel = viewModel,
                    onPlayAudio = { filePath, title -> playMedia(filePath, title) }
                )
            }
            composable(PhoneScreen.Playlists.route) {
                PlaylistManagementScreen(
                    playlists = uiState.playlists,
                    onAddPlaylist = { title, urlOrPath, isLocal ->
                        viewModel.addPlaylist(title, urlOrPath, isLocal)
                    },
                    onDeletePlaylist = { playlist -> viewModel.deletePlaylist(playlist) },
                    onSyncPlaylist = { playlist -> viewModel.syncPlaylist(playlist) },
                    onSelectPlaylist = {
                        // Browsing a playlist's channels happens in the Videos tab.
                        navController.navigate(PhoneScreen.Videos.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(PhoneScreen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onOpenHistoryAndStreams = { navController.navigate(HISTORY_STREAMS_ROUTE) }
                )
            }
            composable(HISTORY_STREAMS_ROUTE) {
                HistoryAndStreamsScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onBack = { navController.popBackStack() },
                    onPlayMedia = { url, title -> playMedia(url, title) }
                )
            }
            composable(PLAYER_ROUTE) {
                PhonePlayerScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onBack = { navController.popBackStack() },
                    onEnterPip = onEnterPip
                )
            }
        }
    }
}
