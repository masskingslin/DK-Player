package com.dk.tvplayer

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.dk.tvplayer.ui.PhoneAppRoot
import com.dk.tvplayer.ui.TvPlayerViewModel
import com.dk.tvplayer.ui.screens.TvMainScreen
import com.dk.tvplayer.ui.theme.DkPlayerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: TvPlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isTv = DeviceType.isTelevision(this)

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            DkPlayerTheme(themeMode = uiState.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (isTv) {
                        TvMainScreen(viewModel = viewModel)
                    } else {
                        PhoneAppRoot(
                            viewModel = viewModel,
                            uiState = uiState,
                            onEnterPip = { enterPictureInPicture() }
                        )
                    }
                }
            }
        }
    }

    private fun enterPictureInPicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (viewModel.uiState.value.currentlyPlayingChannel != null) {
            enterPictureInPicture()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.setPipMode(isInPictureInPictureMode)
    }
}
