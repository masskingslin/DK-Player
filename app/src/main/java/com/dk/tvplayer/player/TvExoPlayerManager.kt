package com.dk.tvplayer.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SubtitleTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String?,
    val isSelected: Boolean
)

class TvExoPlayerManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    val player: ExoPlayer

    private val _subtitleTracks = MutableStateFlow<List<SubtitleTrackInfo>>(emptyList())
    val subtitleTracks = _subtitleTracks.asStateFlow()

    private val _playerError = MutableStateFlow<String?>(null)
    val playerError = _playerError.asStateFlow()

    private var retryCount = 0
    private val maxRetries = 4
    private var retryJob: Job? = null

    val sleepTimer = SleepTimerManager {
        player.pause()
    }

    init {
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        player = ExoPlayer.Builder(context, renderersFactory).build()
        setupListeners()
    }

    private fun setupListeners() {
        player.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                updateSubtitleTracks()
            }

            override fun onPlayerError(error: PlaybackException) {
                handlePlaybackFailure(error)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    retryCount = 0
                    _playerError.value = null
                }
            }
        })
    }

    fun playStream(url: String, externalSubtitleUri: Uri? = null) {
        retryCount = 0
        retryJob?.cancel()
        _playerError.value = null

        val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(url))

        if (externalSubtitleUri != null) {
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(externalSubtitleUri)
                .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                .setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
        }

        player.setMediaItem(mediaItemBuilder.build())
        player.prepare()
        player.play()
    }

    fun setPlaybackSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed, 1.0f)
    }

    private fun updateSubtitleTracks() {
        val tracksList = mutableListOf<SubtitleTrackInfo>()
        val currentTracks = player.currentTracks

        for (groupIndex in 0 until currentTracks.groups.size) {
            val group = currentTracks.groups[groupIndex]
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val label = format.label ?: format.language ?: "Track ${tracksList.size + 1}"
                    tracksList.add(
                        SubtitleTrackInfo(
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            label = label,
                            language = format.language,
                            isSelected = group.isTrackSelected(trackIndex)
                        )
                    )
                }
            }
        }
        _subtitleTracks.value = tracksList
    }

    fun selectSubtitleTrack(groupIndex: Int, trackIndex: Int) {
        val trackGroup = player.currentTracks.groups[groupIndex].mediaTrackGroup
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(trackGroup, trackIndex))
            .build()
    }

    fun disableSubtitles() {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    private fun handlePlaybackFailure(error: PlaybackException) {
        if (retryCount < maxRetries) {
            retryCount++
            val delayMs = 1500L * (1L shl (retryCount - 1))
            _playerError.value = "Connection lost. Reconnecting ($retryCount/$maxRetries)..."
            retryJob = coroutineScope.launch(Dispatchers.Main) {
                delay(delayMs)
                val currentMedia = player.currentMediaItem ?: return@launch
                val pos = player.currentPosition
                player.setMediaItem(currentMedia, pos)
                player.prepare()
                player.play()
            }
        } else {
            _playerError.value = "Playback failed: ${error.localizedMessage ?: "Unknown Error"}"
        }
    }

    fun release() {
        sleepTimer.cancelTimer()
        retryJob?.cancel()
        player.release()
    }
}
