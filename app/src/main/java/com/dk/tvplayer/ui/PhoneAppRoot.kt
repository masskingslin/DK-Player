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
import com.dk.tvplayer.ui.library.VideoLibraryScreen
import com.dk.tvplayer.ui.placeholder.PlaceholderScreen
import com.dk.tvplayer.ui.player.PhonePlayerScreen
import com.dk.tvplayer.ui.settings.SettingsScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
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
        Screen.Library,
        Screen.Audio,
        Screen.Playlists,
        Screen.Settings
    )

    val isPlayerActive = currentRoute?.startsWith("player") == true

    Scaffold(
        bottomBar = {
            if (!isPlayerActive) {
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
            startDestination = Screen.Library.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Library.route) {
                VideoLibraryScreen(
                    viewModel = viewModel,
                    onPlayVideo = { url, title ->
                        val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                        val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                        navController.navigate("player/$encodedUrl/$encodedTitle")
                    }
                )
            }

            composable(Screen.Audio.route) {
                PlaceholderScreen(title = "Audio Library", subtitle = "Local audio track scanner coming soon")
            }

            composable(Screen.Playlists.route) {
                PlaceholderScreen(title = "Playlists", subtitle = "Custom M3U & XSPF list manager")
            }

            composable(Screen.Settings.route) {
                SettingsScreen()
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