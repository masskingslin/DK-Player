package com.dk.tvplayer.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.dk.tvplayer.ui.downloads.DownloadsScreen
import com.dk.tvplayer.ui.home.HomeHubScreen
import com.dk.tvplayer.ui.library.AudioLibraryScreen
import com.dk.tvplayer.ui.library.VideoLibraryScreen
import com.dk.tvplayer.ui.player.PhonePlayerScreen
import com.dk.tvplayer.ui.playlists.PlaylistDetailScreen
import com.dk.tvplayer.ui.playlists.PlaylistsScreen
import com.dk.tvplayer.ui.settings.SettingsScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Library : Screen("library", "Videos", Icons.Default.Folder)
    data object Audio : Screen("audio", "Audio", Icons.Default.MusicNote)
    data object Playlists : Screen("playlists", "Playlists", Icons.Default.PlaylistPlay)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun PhoneAppRoot(viewModel: TvPlayerViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val navItems = listOf(
        Screen.Home,
        Screen.Library,
        Screen.Audio,
        Screen.Playlists,
        Screen.Settings
    )

    // Hide the bottom bar on the full-screen player and on secondary/detail screens
    // reached via Settings or Playlists, matching the player's own behavior.
    val hideBottomBar = currentRoute?.startsWith("player") == true ||
        currentRoute == "downloads" ||
        currentRoute?.startsWith("playlist_detail") == true

    fun navigateToPlayer(url: String, title: String) {
        val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
        val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
        navController.navigate("player/$encodedUrl/$encodedTitle")
    }

    Scaffold(
        bottomBar = {
            if (!hideBottomBar) {
                NavigationBar {
                    navItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentRoute == screen.route,
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
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
            // A quick crossfade between tabs and screens reads as much more deliberate
            // than Navigation-Compose's default (an abrupt cut with no transition at all).
            enterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(200)) },
            exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(150)) },
            popEnterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(200)) },
            popExitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(150)) }
        ) {
            composable(Screen.Home.route) {
                HomeHubScreen(
                    viewModel = viewModel,
                    onPlayMedia = { url, title -> navigateToPlayer(url, title) },
                    onOpenDownloads = { navController.navigate("downloads") },
                    onOpenPlaylists = {
                        navController.navigate(Screen.Playlists.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(Screen.Library.route) {
                VideoLibraryScreen(
                    viewModel = viewModel,
                    onPlayVideo = { url, title -> navigateToPlayer(url, title) }
                )
            }

            composable(Screen.Audio.route) {
                AudioLibraryScreen(
                    viewModel = viewModel,
                    onPlayAudio = { filePath, title -> navigateToPlayer(filePath, title) }
                )
            }

            composable(Screen.Playlists.route) {
                PlaylistsScreen(
                    viewModel = viewModel,
                    onOpenPlaylist = { playlist -> navController.navigate("playlist_detail/${playlist.id}") }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    onOpenDownloads = { navController.navigate("downloads") }
                )
            }

            composable("downloads") {
                DownloadsScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "playlist_detail/{playlistId}",
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: -1L
                val state by viewModel.uiState.collectAsState()
                val playlist = state.playlists.find { it.id == playlistId }
                if (playlist != null) {
                    PlaylistDetailScreen(
                        playlist = playlist,
                        viewModel = viewModel,
                        onPlayItem = { url, title -> navigateToPlayer(url, title) },
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(
                route = "player/{mediaUrl}/{title}",
                arguments = listOf(
                    navArgument("mediaUrl") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val rawUrl = backStackEntry.arguments?.getString("mediaUrl").orEmpty()
                val rawTitle = backStackEntry.arguments?.getString("title").orEmpty()
                val mediaUrl = URLDecoder.decode(rawUrl, StandardCharsets.UTF_8.toString())
                val title = URLDecoder.decode(rawTitle, StandardCharsets.UTF_8.toString())

                PhonePlayerScreen(
                    mediaUrl = mediaUrl,
                    title = title,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
