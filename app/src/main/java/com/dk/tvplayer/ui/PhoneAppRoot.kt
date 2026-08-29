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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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

@Composable
fun PhoneAppRoot(viewModel: TvPlayerViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val items = listOf(
        PhoneScreen.Videos,
        PhoneScreen.Audio,
        PhoneScreen.Playlists,
        PhoneScreen.Settings
    )

    Scaffold(
        bottomBar = {
            val currentRoute = currentDestination?.route
            if (currentRoute == null || !currentRoute.startsWith("player/")) {
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
                    onPlayVideo = { videoId ->
                        navController.navigate("player/$videoId/video")
                    }
                )
            }
            composable(PhoneScreen.Audio.route) {
                AudioLibraryScreen(
                    viewModel = viewModel,
                    onPlayAudio = { audioId ->
                        navController.navigate("player/$audioId/audio")
                    }
                )
            }
            composable(PhoneScreen.Playlists.route) {
                PlaylistManagementScreen(
                    viewModel = viewModel,
                    onPlayChannel = { channelId ->
                        navController.navigate("player/$channelId/channel")
                    }
                )
            }
            composable(PhoneScreen.Settings.route) {
                SettingsScreen(viewModel = viewModel)
            }
            composable(
                route = "player/{mediaId}/{mediaType}",
                arguments = listOf(
                    navArgument("mediaId") { type = NavType.StringType },
                    navArgument("mediaType") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val mediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
                val mediaType = backStackEntry.arguments?.getString("mediaType") ?: "video"
                PhonePlayerScreen(
                    mediaId = mediaId,
                    mediaType = mediaType,
                    viewModel = viewModel,
                    onBackPressed = { navController.popBackStack() }
                )
            }
        }
    }
}
