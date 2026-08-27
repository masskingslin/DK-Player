package com.stream.tvplayer.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TvExoPlayerManager(context: Context) {
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _isPlayingFlow = MutableStateFlow(false)
    val isPlayingFlow: StateFlow<Boolean> = _isPlayingFlow.asStateFlow()

    private val _currentPositionFlow = MutableStateFlow(0L)
    val currentPositionFlow: StateFlow<Long> = _currentPositionFlow.asStateFlow()

    private val _durationFlow = MutableStateFlow(0L)
    val durationFlow: StateFlow<Long> = _durationFlow.asStateFlow()

    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlayingFlow.value = isPlaying
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _durationFlow.value = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
        })
    }

    fun play(url: String, startPositionMs: Long = 0L) {
        val mediaItem = MediaItem.fromUri(url)
        exoPlayer.setMediaItem(mediaItem)
        if (startPositionMs > 0L) {
            exoPlayer.seekTo(startPositionMs)
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs.coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L)))
        _currentPositionFlow.value = exoPlayer.currentPosition
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = scope.launch {
            while (isActive) {
                _currentPositionFlow.value = exoPlayer.currentPosition
                _durationFlow.value = exoPlayer.duration.coerceAtLeast(0L)
                delay(500)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stopProgressTracker()
        exoPlayer.release()
    }
}
