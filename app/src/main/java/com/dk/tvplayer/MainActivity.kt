package com.dk.tvplayer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dk.tvplayer.ui.PhoneAppRoot
import com.stream.tvplayer.data.local.LocalVideoScanner
import com.stream.tvplayer.data.local.TvDatabase
import com.stream.tvplayer.data.repository.TvRepository
import com.stream.tvplayer.player.TvExoPlayerManager
import com.stream.tvplayer.ui.TvPlayerViewModel
import com.stream.tvplayer.ui.screens.TvMainScreen

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    private val viewModel: TvPlayerViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = TvDatabase.getDatabase(applicationContext)
                val scanner = LocalVideoScanner(applicationContext)
                val repo = TvRepository(
                    channelDao = db.channelDao(),
                    epgDao = db.epgDao(),
                    historyDao = db.historyDao(),
                    streamDao = db.streamDao(),
                    scanner = scanner
                )
                val playerManager = TvExoPlayerManager(applicationContext)
                return TvPlayerViewModel(repo, playerManager) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestRequiredPermissions()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    AppEntry(viewModel = viewModel)
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }
}

@Composable
fun AppEntry(viewModel: TvPlayerViewModel) {
    val context = LocalContext.current
    val isTv = remember { DeviceType.isTelevision(context) }

    if (isTv) {
        TvMainScreen(viewModel = viewModel)
    } else {
        PhoneAppRoot(viewModel = viewModel)
    }
}
