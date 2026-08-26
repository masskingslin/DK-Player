package com.stream.tvplayer.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer

@OptIn(UnstableApi::class)
class TvExoPlayerManager(context: Context) {

    val player: ExoPlayer

    init {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000, // Min buffer
                50000, // Max buffer
                2500,  // Playback buffer
                5000   // Rebuffer
            )
            .build()

        player = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        // Auto-retry live stream on connection drops
                        prepare()
                        play()
                    }
                })
            }
    }

    fun playChannel(streamUrl: String, licenseServerUrl: String? = null) {
        val mediaItemBuilder = MediaItem.Builder().setUri(streamUrl)

        if (!licenseServerUrl.isNullOrBlank()) {
            mediaItemBuilder.setDrmConfiguration(
                MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri(licenseServerUrl)
                    .build()
            )
        }

        player.setMediaItem(mediaItemBuilder.build())
        player.prepare()
    }

    fun release() {
        player.release()
    }
}
