package com.stream.tvplayer.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

@OptIn(UnstableApi::class)
class TvExoPlayerManager(private val context: Context) {

    var exoPlayer: ExoPlayer? = null
        private set

    fun initialize(onPlayerError: ((String) -> Unit)? = null) {
        if (exoPlayer == null) {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
                .setUserAgent("DK-Player/1.0 (Android TV)")

            val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

            exoPlayer = ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    playWhenReady = true
                    repeatMode = Player.REPEAT_MODE_OFF
                    addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            onPlayerError?.invoke(error.localizedMessage ?: "Playback Error")
                        }
                    })
                }
        }
    }

    fun playStream(url: String, licenseServerUrl: String? = null) {
        val player = exoPlayer ?: return
        val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(url))

        if (!licenseServerUrl.isNullOrBlank()) {
            mediaItemBuilder.setDrmConfiguration(
                MediaItem.DrmConfiguration.Builder(androidx.media3.common.C.WIDEVINE_UUID)
                    .setLicenseUri(licenseServerUrl)
                    .build()
            )
        }

        player.setMediaItem(mediaItemBuilder.build())
        player.prepare()
        player.play()
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
}
