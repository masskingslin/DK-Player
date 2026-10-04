package com.dk.tvplayer.ui.player

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Display
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView

/**
 * Plays the current video full screen on a secondary display (HDMI / Chromecast screen),
 * leaving the phone free to act as the remote control. Used when "Prefer clone" is off.
 */
class VideoPresentation(
    context: Context,
    display: Display,
    private val videoPlayer: Player
) : Presentation(context, display) {

    private var playerView: PlayerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = PlayerView(context).apply {
            useController = false
            setShutterBackgroundColor(Color.BLACK)
            player = videoPlayer
        }
        playerView = view
        setContentView(view)
    }

    override fun onStop() {
        playerView?.player = null
        super.onStop()
    }
}
