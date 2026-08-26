package com.stream.tvplayer.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.stream.tvplayer.player.TvExoPlayerManager

@OptIn(UnstableApi::class)
@Composable
fun TvPlayerSurface(
    streamUrl: String?,
    licenseServerUrl: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playerManager = remember { TvExoPlayerManager(context) }

    LaunchedEffect(streamUrl, licenseServerUrl) {
        if (!streamUrl.isNullOrBlank()) {
            playerManager.playChannel(streamUrl, licenseServerUrl)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playerManager.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = playerManager.player
                useController = false
                keepScreenOn = true
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        }
    )
}
