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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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
import com.dk.tvplayer.ui.advanced.AdvancedScreen
import com.dk.tvplayer.ui.parental.ParentalControlScreen
import com.dk.tvplayer.ui.parental.PinPromptHost
import com.dk.tvplayer.ui.parental.SettingsPinGate
import com.dk.tvplayer.ui.advanced.DebugLogsScreen
import com.dk.tvplayer.ui.remote.RemoteAccessScreen
import com.dk.tvplayer.ui.settings.AudioSettingsScreen
import com.dk.tvplayer.ui.settings.GeneralSettingsScreen
import com.dk.tvplayer.ui.settings.InterfaceSettingsScreen
import com.dk.tvplayer.ui.settings.MediaFoldersScreen
import com.dk.tvplayer.ui.settings.PermissionsScreen
import com.dk.tvplayer.ui.settings.SettingsScreen
import com.dk.tvplayer.ui.settings.SubtitlesSettingsScreen
import com.dk.tvplayer.ui.settings.VideoSettingsScreen
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
        currentRoute == "remote_access" ||
        currentRoute == "advanced" ||
        currentRoute == "parental_control" ||
        currentRoute == "pref_interface" ||
        currentRoute == "pref_video" ||
        currentRoute == "pref_subtitles" ||
        currentRoute == "pref_audio" ||
        currentRoute == "pref_general" ||
        currentRoute == "pref_folders" ||
        currentRoute == "pref_permissions" ||
        currentRoute == "debug_logs" ||
        currentRoute?.startsWith("playlist_detail") == true

    fun startPlayer(url: String, title: String, isLive: Boolean = false) {
        val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
        val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
        navController.navigate("player/$encodedUrl/$encodedTitle/$isLive")
    }

    // "Action for streams when the connection is metered": warn or refuse before streaming over
    // mobile data. Local files and finished downloads are never affected.
    var meteredPrompt by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    val appContext = androidx.compose.ui.platform.LocalContext.current

    fun navigateToPlayer(url: String, title: String, isLive: Boolean = false) {
        val action = com.dk.tvplayer.util.PlaybackPrefs.meteredAction.value
        val isRemote = url.startsWith("http://") || url.startsWith("https://")
        if (action != com.dk.tvplayer.util.MeteredAction.NOTHING && isRemote) {
            val metered = (appContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager).isActiveNetworkMetered
            val downloaded = (appContext.applicationContext as com.dk.tvplayer.DkPlayerApplication)
                .downloadManagerHolder.downloadTracker.isDownloaded(url)
            if (metered && !downloaded) {
                when (action) {
                    com.dk.tvplayer.util.MeteredAction.BLOCK -> android.widget.Toast.makeText(
                        appContext, "Streaming is blocked on a metered connection", android.widget.Toast.LENGTH_LONG
                    ).show()
                    else -> meteredPrompt = Triple(url, title, isLive)
                }
                return
            }
        }
        startPlayer(url, title, isLive)
    }

    meteredPrompt?.let { (url, title, isLive) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { meteredPrompt = null },
            title = { androidx.compose.material3.Text("Metered connection") },
            text = {
                androidx.compose.material3.Text(
                    "You're on a metered connection (such as mobile data). Streaming may use a lot of data."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    meteredPrompt = null
                    startPlayer(url, title, isLive)
                }) { androidx.compose.material3.Text("Play anyway") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { meteredPrompt = null }) {
                    androidx.compose.material3.Text("Cancel")
                }
            }
        )
    }


    // Play-queue advance: when the current item ends while the player screen is up,
    // swap it for the next queued one (replacing the player route so Back still exits).
    val latestRoute by rememberUpdatedState(currentRoute)
    LaunchedEffect(Unit) {
        viewModel.playbackEnded.collect {
            if (latestRoute?.startsWith("player") == true) {
                val next = viewModel.popNextQueued() ?: return@collect
                val encodedUrl = URLEncoder.encode(next.first, StandardCharsets.UTF_8.toString())
                val encodedTitle = URLEncoder.encode(next.second, StandardCharsets.UTF_8.toString())
                navController.navigate("player/$encodedUrl/$encodedTitle/false") {
                    popUpTo("player/{mediaUrl}/{title}/{isLive}") { inclusive = true }
                }
            }
        }
    }

    // "Restore video from background": coming back to the app while a video is still playing
    // (background playback) reopens the player instead of the list you left.
    LaunchedEffect(Unit) {
        viewModel.appForegrounded.collect {
            if (!com.dk.tvplayer.util.UiPrefs.restoreVideoFromBackground.value) return@collect
            if (latestRoute?.startsWith("player") == true) return@collect
            if (com.dk.tvplayer.FloatingPlayerState.isActive) return@collect
            val pm = viewModel.playerManager
            val url = pm.currentMediaUrl ?: return@collect
            if (pm.isPlayingFlow.value && !pm.audioOnlyModeEnabledFlow.value) {
                navigateToPlayer(url, pm.currentMediaTitle ?: url.substringAfterLast('/'), false)
            }
        }
    }

    // Launcher-shortcut / external "play this file" requests.
    val externalRequest by viewModel.externalPlayRequest.collectAsState()
    LaunchedEffect(externalRequest) {
        externalRequest?.let { (path, title) ->
            navigateToPlayer(path, title)
            viewModel.externalPlayRequest.value = null
        }
    }

    PinPromptHost()

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
                    onPlayMedia = { url, title, isLive -> navigateToPlayer(url, title, isLive) },
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
                    onPlayVideo = { url, title, isLive -> navigateToPlayer(url, title, isLive) }
                )
            }

            composable(Screen.Audio.route) {
                AudioLibraryScreen(
                    viewModel = viewModel,
                    // Local audio files are never "live".
                    onPlayAudio = { filePath, title -> navigateToPlayer(filePath, title, isLive = false) }
                )
            }

            composable(Screen.Playlists.route) {
                PlaylistsScreen(
                    viewModel = viewModel,
                    onOpenPlaylist = { playlist -> navController.navigate("playlist_detail/${playlist.id}") }
                )
            }

            composable(Screen.Settings.route) {
                val restrictSettings by com.dk.tvplayer.util.ParentalControl.restrictSettings.collectAsState()
                var unlocked by remember { mutableStateOf(com.dk.tvplayer.util.ParentalControl.isSettingsUnlocked()) }
                if (!restrictSettings || unlocked || com.dk.tvplayer.util.ParentalControl.isSettingsUnlocked()) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onOpenDownloads = { navController.navigate("downloads") },
                        onOpenRemoteAccess = { navController.navigate("remote_access") },
                        onOpenAdvanced = { navController.navigate("advanced") },
                        onOpenParentalControl = { navController.navigate("parental_control") },
                        onOpenInterface = { navController.navigate("pref_interface") },
                        onOpenVideoSettings = { navController.navigate("pref_video") },
                        onOpenSubtitles = { navController.navigate("pref_subtitles") },
                        onOpenAudio = { navController.navigate("pref_audio") },
                        onOpenGeneral = { navController.navigate("pref_general") }
                    )
                } else {
                    SettingsPinGate(onUnlocked = {
                        com.dk.tvplayer.util.ParentalControl.markSettingsUnlocked()
                        unlocked = true
                    })
                }
            }

            composable("pref_interface") {
                InterfaceSettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }

            composable("pref_video") {
                VideoSettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }

            composable("pref_audio") {
                AudioSettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }

            composable("pref_general") {
                GeneralSettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenFolders = { navController.navigate("pref_folders") },
                    onOpenPermissions = { navController.navigate("pref_permissions") }
                )
            }

            composable("pref_folders") {
                MediaFoldersScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }

            composable("pref_permissions") {
                PermissionsScreen(onBack = { navController.popBackStack() })
            }

            composable("pref_subtitles") {
                SubtitlesSettingsScreen(onBack = { navController.popBackStack() })
            }

            composable("parental_control") {
                ParentalControlScreen(onBack = { navController.popBackStack() })
            }

            composable("advanced") {
                AdvancedScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenDebugLogs = { navController.navigate("debug_logs") }
                )
            }

            composable("debug_logs") {
                DebugLogsScreen(onBack = { navController.popBackStack() })
            }

            composable("remote_access") {
                RemoteAccessScreen(onBack = { navController.popBackStack() })
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
                        // Playlist items are user-curated saved media, not live channels.
                        onPlayItem = { url, title -> navigateToPlayer(url, title, isLive = false) },
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(
                route = "player/{mediaUrl}/{title}/{isLive}",
                arguments = listOf(
                    navArgument("mediaUrl") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType },
                    navArgument("isLive") { type = NavType.BoolType }
                )
            ) { backStackEntry ->
                val rawUrl = backStackEntry.arguments?.getString("mediaUrl").orEmpty()
                val rawTitle = backStackEntry.arguments?.getString("title").orEmpty()
                val mediaUrl = URLDecoder.decode(rawUrl, StandardCharsets.UTF_8.toString())
                val title = URLDecoder.decode(rawTitle, StandardCharsets.UTF_8.toString())
                val isLive = backStackEntry.arguments?.getBoolean("isLive") ?: false

                PhonePlayerScreen(
                    mediaUrl = mediaUrl,
                    title = title,
                    isLive = isLive,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
