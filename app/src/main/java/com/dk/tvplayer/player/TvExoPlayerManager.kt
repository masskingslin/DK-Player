@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.dk.tvplayer.player

import android.content.Context
import android.net.Uri
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SubtitleTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val isSelected: Boolean
)

/**
 * Wraps local ExoPlayer playback plus an optional Chromecast [CastPlayer].
 * The [activePlayerFlow] always reflects whichever player (local or cast) is
 * currently "live" — UI surfaces (PlayerView) should bind to it so playback
 * seamlessly hands off when a cast session starts/ends.
 *
 * Chromecast is treated as fully optional: if Play Services / the Cast
 * framework isn't in good shape on this device, [isCastAvailableFlow] simply
 * stays false and local playback is completely unaffected. This is
 * deliberately defensive — a broken Cast environment must never be able to
 * break local video/audio/IPTV playback.
 */
class TvExoPlayerManager(
    private val context: Context,
    hwAccelerationEnabled: Boolean = true
) {
    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
    }

    private val trackSelector = DefaultTrackSelector(context)

    private val renderersFactory = DefaultRenderersFactory(context).apply {
        // Hardware acceleration toggle: EXTENSION_RENDERER_MODE_OFF keeps decoding on
        // platform MediaCodec (hardware) decoders only; PREFER routes through software
        // extension decoders first when available. This is applied at player-creation
        // time — changing the Settings toggle takes effect on next app start.
        setExtensionRendererMode(
            if (hwAccelerationEnabled) {
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            } else {
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            }
        )
    }

    val localPlayer: ExoPlayer = ExoPlayer.Builder(context, renderersFactory)
        .setTrackSelector(trackSelector)
        .build()

    /** Kept for backward compatibility with call sites that only ever used local playback (e.g. TV surface). */
    val exoPlayer: ExoPlayer get() = localPlayer

    private var castPlayer: CastPlayer? = null

    private val _activePlayer = MutableStateFlow<Player>(localPlayer)
    val activePlayerFlow: StateFlow<Player> = _activePlayer.asStateFlow()

    private val _isPlayingFlow = MutableStateFlow(false)
    val isPlayingFlow: StateFlow<Boolean> = _isPlayingFlow.asStateFlow()

    private val _currentPositionFlow = MutableStateFlow(0L)
    val currentPositionFlow: StateFlow<Long> = _currentPositionFlow.asStateFlow()

    private val _durationFlow = MutableStateFlow(0L)
    val durationFlow: StateFlow<Long> = _durationFlow.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeedFlow: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackErrorFlow: StateFlow<String?> = _playbackError.asStateFlow()

    private val _isCastAvailable = MutableStateFlow(false)
    val isCastAvailableFlow: StateFlow<Boolean> = _isCastAvailable.asStateFlow()

    private val _isCasting = MutableStateFlow(false)
    val isCastingFlow: StateFlow<Boolean> = _isCasting.asStateFlow()

    private val _sleepTimerRemainingSec = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingSecFlow: StateFlow<Long?> = _sleepTimerRemainingSec.asStateFlow()

    private var lastPlayedUrl: String? = null
    private var lastPlayedTitle: String? = null
    private var retryAttempt = 0

    private var progressJob: Job? = null
    private var retryJob: Job? = null
    private var sleepTimerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        attachListener(localPlayer)
    }

    private fun attachListener(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (_activePlayer.value === player) {
                    _isPlayingFlow.value = isPlaying
                    if (isPlaying) startProgressTracker() else stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (_activePlayer.value === player) {
                    if (playbackState == Player.STATE_READY) {
                        _durationFlow.value = player.duration.coerceAtLeast(0L)
                        retryAttempt = 0
                        _playbackError.value = null
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (_activePlayer.value === player) {
                    handlePlaybackError(error)
                }
            }
        })
    }

    // ---- Playback ----

    fun play(url: String, startPositionMs: Long = 0L, title: String? = null) {
        lastPlayedUrl = url
        lastPlayedTitle = title ?: lastPlayedTitle
        retryAttempt = 0
        _playbackError.value = null
        retryJob?.cancel()

        val target = _activePlayer.value
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(lastPlayedTitle ?: "").build())
            .build()
        target.setMediaItem(mediaItem)
        if (startPositionMs > 0L) {
            target.seekTo(startPositionMs)
        }
        target.prepare()
        target.playWhenReady = true
        target.setPlaybackSpeed(_playbackSpeed.value)
    }

    fun togglePlayPause() {
        val player = _activePlayer.value
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        val player = _activePlayer.value
        player.seekTo(positionMs.coerceIn(0L, player.duration.coerceAtLeast(0L)))
        _currentPositionFlow.value = player.currentPosition
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        _activePlayer.value.setPlaybackSpeed(speed)
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = scope.launch {
            while (isActive) {
                val player = _activePlayer.value
                _currentPositionFlow.value = player.currentPosition
                _durationFlow.value = player.duration.coerceAtLeast(0L)
                delay(500)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    // ---- Error handling & retry ----

    private fun handlePlaybackError(error: PlaybackException) {
        _playbackError.value = error.message ?: error.errorCodeName
        if (retryAttempt < MAX_RETRY_ATTEMPTS) {
            val delayMs = 1000L * (1 shl retryAttempt)
            retryAttempt++
            retryJob?.cancel()
            retryJob = scope.launch {
                delay(delayMs)
                retryPlayback()
            }
        }
    }

    /** Manual retry (e.g. user taps a "Retry" banner) or automatic backoff retry. */
    fun retryPlayback() {
        val url = lastPlayedUrl ?: return
        val position = _activePlayer.value.currentPosition
        play(url, position, lastPlayedTitle)
    }

    fun clearError() {
        _playbackError.value = null
        retryJob?.cancel()
    }

    // ---- Subtitles ----

    fun availableSubtitleTracks(): List<SubtitleTrackInfo> {
        val tracks = localPlayer.currentTracks
        val result = mutableListOf<SubtitleTrackInfo>()
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val label = format.label ?: format.language ?: "Track ${groupIndex + 1}.${trackIndex + 1}"
                    result.add(SubtitleTrackInfo(groupIndex, trackIndex, label, group.isTrackSelected(trackIndex)))
                }
            }
        }
        return result
    }

    fun selectSubtitleTrack(groupIndex: Int, trackIndex: Int) {
        val group = localPlayer.currentTracks.groups.getOrNull(groupIndex) ?: return
        val override = TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
        trackSelector.parameters = trackSelector.parameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(override)
            .build()
    }

    fun disableSubtitles() {
        trackSelector.parameters = trackSelector.parameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
    }

    /** Loads an external subtitle file (.srt/.vtt/.ttml) alongside the currently playing media. */
    fun loadExternalSubtitle(subtitleUrl: String, languageLabel: String = "External") {
        val currentUrl = lastPlayedUrl ?: return
        val position = localPlayer.currentPosition
        val mimeType = when {
            subtitleUrl.endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
            subtitleUrl.endsWith(".ttml", ignoreCase = true) || subtitleUrl.endsWith(".xml", ignoreCase = true) ->
                MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
            .setMimeType(mimeType)
            .setLanguage(languageLabel)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(currentUrl)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(lastPlayedTitle ?: "").build())
            .setSubtitleConfigurations(listOf(subtitleConfig))
            .build()
        localPlayer.setMediaItem(mediaItem, position)
        localPlayer.prepare()
        localPlayer.playWhenReady = true
    }

    // ---- Sleep timer ----

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        var remaining = minutes * 60L
        _sleepTimerRemainingSec.value = remaining
        sleepTimerJob = scope.launch {
            while (isActive && remaining > 0) {
                delay(1000)
                remaining -= 1
                _sleepTimerRemainingSec.value = remaining
            }
            if (isActive) {
                _activePlayer.value.pause()
                _sleepTimerRemainingSec.value = null
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingSec.value = null
    }

    // ---- Chromecast ----

    /**
     * Call once (e.g. from MainActivity.onCreate) — safe no-op if Play Services / Cast
     * isn't available. Guarded on two levels: (1) a GoogleApiAvailability precheck so we
     * never even attempt CastContext initialization on a device that can't support it,
     * and (2) a try/catch around the SDK calls themselves for any other failure mode.
     * Local video/audio/IPTV playback never depends on this succeeding.
     */
    fun initCast() {
        if (castPlayer != null) return
        try {
            val availability = GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context)
            if (availability != ConnectionResult.SUCCESS) {
                _isCastAvailable.value = false
                return
            }

            val castContext = CastContext.getSharedInstance(context)
            val player = CastPlayer(castContext)
            player.setSessionAvailabilityListener(object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() = switchToCast()
                override fun onCastSessionUnavailable() = switchToLocal()
            })
            attachListener(player)
            castPlayer = player
            _isCastAvailable.value = true
        } catch (t: Throwable) {
            // No Play Services / no Cast receiver / outdated Play Services on this device
            // (common on some TVs and older phones) — local-only playback, no crash.
            _isCastAvailable.value = false
            castPlayer = null
        }
    }

    private fun switchToCast() {
        val cast = castPlayer ?: return
        val url = lastPlayedUrl
        val position = localPlayer.currentPosition
        localPlayer.pause()
        if (url != null) {
            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(lastPlayedTitle ?: "").build())
                .build()
            cast.setMediaItem(mediaItem, position)
            cast.prepare()
            cast.playWhenReady = true
        }
        _activePlayer.value = cast
        _isCasting.value = true
    }

    private fun switchToLocal() {
        val cast = castPlayer
        val position = cast?.currentPosition?.coerceAtLeast(0L) ?: localPlayer.currentPosition
        cast?.stop()
        _activePlayer.value = localPlayer
        _isCasting.value = false
        val url = lastPlayedUrl
        if (url != null) {
            play(url, position, lastPlayedTitle)
        }
    }

    fun release() {
        stopProgressTracker()
        retryJob?.cancel()
        sleepTimerJob?.cancel()
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        localPlayer.release()
    }
}
