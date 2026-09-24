@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.dk.tvplayer.player

import android.content.Context
import android.content.Intent
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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.core.content.ContextCompat
import com.dk.tvplayer.data.parser.M3uParser
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

data class AudioTrackInfo(
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
    hwAccelerationEnabled: Boolean = true,
    cacheDataSourceFactory: CacheDataSource.Factory? = null
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
        .apply {
            // When a download cache is supplied, playback transparently reads from it for
            // anything that's been downloaded (see DownloadManagerHolder) and falls back
            // to the network for everything else — no special-casing needed at the call
            // site that starts playback.
            if (cacheDataSourceFactory != null) {
                setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            }
        }
        .build()

    /** Kept for backward compatibility with call sites that only ever used local playback (e.g. TV surface). */
    val exoPlayer: ExoPlayer get() = localPlayer

    private var castPlayer: CastPlayer? = null
    private var castAudioOnlyEnabled = false

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

    private val _isBufferingFlow = MutableStateFlow(false)
    val isBufferingFlow: StateFlow<Boolean> = _isBufferingFlow.asStateFlow()

    private val _isCastAvailable = MutableStateFlow(false)
    val isCastAvailableFlow: StateFlow<Boolean> = _isCastAvailable.asStateFlow()

    private val _isCasting = MutableStateFlow(false)
    val isCastingFlow: StateFlow<Boolean> = _isCasting.asStateFlow()

    private val _sleepTimerRemainingSec = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingSecFlow: StateFlow<Long?> = _sleepTimerRemainingSec.asStateFlow()

    // Frame rate of the currently playing local video track, if known — used by
    // MainActivity to optionally match the display's refresh rate to it.
    private val _videoFrameRateFlow = MutableStateFlow<Float?>(null)
    val videoFrameRateFlow: StateFlow<Float?> = _videoFrameRateFlow.asStateFlow()

    private var lastPlayedUrl: String? = null
    private var lastPlayedTitle: String? = null
    private var lastPlayedUserAgent: String? = null
    private var lastPlayedReferrer: String? = null
    private var retryAttempt = 0
    // Many real-world IPTV stream URLs (especially Xtream-Codes-style links) have no
    // file extension at all, so ExoPlayer's default container sniffing can't tell it's
    // HLS and fails with "parsing container unsupported". Since that failure is
    // deterministic — retrying the exact same MediaItem will fail the exact same way
    // every time — one retry attempt forces the MIME type to HLS instead of blindly
    // repeating the request. If the stream genuinely isn't HLS either, we stop
    // retrying immediately rather than wasting the usual backoff attempts on an error
    // that network conditions can't fix.
    private var forcedHlsRetry = false
    private var backgroundPlaybackEnabled = false

    /** Wraps localPlayer so PlaybackService (and, if ever needed, other controllers) can
     *  discover and control the same player instance the UI is using. */
    val mediaSession: MediaSession = MediaSession.Builder(context, localPlayer).build()

    /** Called by the ViewModel whenever the "Background Audio Playback" setting changes. */
    fun setBackgroundPlaybackEnabled(enabled: Boolean) {
        backgroundPlaybackEnabled = enabled
    }

    private fun maybeStartPlaybackService() {
        if (!backgroundPlaybackEnabled) return
        runCatching {
            val intent = Intent(context, PlaybackService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }

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
                    if (isPlaying) {
                        startProgressTracker()
                        if (player === localPlayer) {
                            maybeStartPlaybackService()
                        }
                    } else {
                        stopProgressTracker()
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (_activePlayer.value === player) {
                    _isBufferingFlow.value = playbackState == Player.STATE_BUFFERING
                    if (playbackState == Player.STATE_READY) {
                        _durationFlow.value = player.duration.coerceAtLeast(0L)
                        retryAttempt = 0
                        _playbackError.value = null
                        updateVideoFrameRate()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (_activePlayer.value === player) {
                    handlePlaybackError(error)
                }
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                if (_activePlayer.value === player) {
                    updateVideoFrameRate()
                }
            }
        })
    }

    private fun updateVideoFrameRate() {
        val frameRate = localPlayer.videoFormat?.frameRate
        _videoFrameRateFlow.value = if (frameRate != null && frameRate > 0f) frameRate else null
    }

    // ---- Playback ----

    /**
     * @param userAgent Per-stream User-Agent override (e.g. from an M3U's #EXTVLCOPT or
     * piped-URL directive — see [M3uParser]). Falls back to a generic browser-like UA,
     * matching VLC's behaviour of always sending *some* recognizable User-Agent rather
     * than none, which some CDNs reject outright.
     * @param referrer Per-stream Referer override, same source. Many channels in large
     * aggregated playlists (e.g. iptv-org's index.m3u) are hosted behind CDNs that 403
     * requests missing this — VLC/Kodi apply it per-channel, so we do too.
     */
    fun play(
        url: String,
        startPositionMs: Long = 0L,
        title: String? = null,
        forceHlsMimeType: Boolean = false,
        userAgent: String? = null,
        referrer: String? = null
    ) {
        lastPlayedUrl = url
        lastPlayedTitle = title ?: lastPlayedTitle
        lastPlayedUserAgent = userAgent
        lastPlayedReferrer = referrer
        retryAttempt = 0
        forcedHlsRetry = forceHlsMimeType
        _playbackError.value = null
        retryJob?.cancel()

        val target = _activePlayer.value
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(lastPlayedTitle ?: "").build())
        if (forceHlsMimeType) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }
        val mediaItem = mediaItemBuilder.build()

        // Cast's generic Player interface has no notion of custom request headers, and
        // this only matters for network IPTV channels that actually carry a header
        // override (see M3uParser) — local videos, custom streams and *downloaded*
        // content (which relies on the shared cache-backed factory set up in the
        // constructor for offline playback) must keep going through the player's
        // normal setMediaItem path unchanged. Only when there's a real override do we
        // build a one-off MediaSource via setMediaSource(...) that bypasses the cache
        // and applies it, mirroring how VLC/Kodi apply per-channel headers.
        val hasHeaderOverride = target is ExoPlayer && (!userAgent.isNullOrBlank() || !referrer.isNullOrBlank())
        if (hasHeaderOverride) {
            val requestHeaders = mutableMapOf<String, String>()
            if (!referrer.isNullOrBlank()) requestHeaders["Referer"] = referrer
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent?.takeIf { it.isNotBlank() } ?: M3uParser.DEFAULT_USER_AGENT)
                .setDefaultRequestProperties(requestHeaders)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(15_000)
            val mediaSource = DefaultMediaSourceFactory(httpDataSourceFactory).createMediaSource(mediaItem)
            (target as ExoPlayer).setMediaSource(mediaSource)
        } else {
            target.setMediaItem(mediaItem)
        }

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

    /** Fast seek trades exact-frame accuracy for speed by snapping to the nearest keyframe. */
    fun setFastSeekEnabled(enabled: Boolean) {
        localPlayer.setSeekParameters(if (enabled) SeekParameters.CLOSEST_SYNC else SeekParameters.EXACT)
    }

    /** Caps the max selected video track resolution; pass null to remove the cap. */
    fun setMaxVideoResolution(maxWidth: Int, maxHeight: Int) {
        trackSelector.parameters = trackSelector.parameters.buildUpon()
            .setMaxVideoSize(maxWidth, maxHeight)
            .build()
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
        // The friendly banner text is intentionally generic for users, but that means a
        // screenshot of it alone can't distinguish four different underlying failures
        // (malformed container vs. unsupported container vs. malformed/unsupported
        // manifest) or show *why* — logging the real exception (errorCodeName + cause)
        // here means `adb logcat -s DkPlayer:E` gives the actual root cause on demand
        // without needing to change the UI or ask the user to reproduce it again.
        android.util.Log.e(
            "DkPlayer",
            "Playback error for $lastPlayedUrl: ${error.errorCodeName} — ${error.message}",
            error.cause ?: error
        )
        _playbackError.value = friendlyErrorMessage(error)

        val isContainerParsingError = when (error.errorCode) {
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> true
            else -> false
        }

        if (isContainerParsingError) {
            // Deterministic failure — retrying the same request would just fail the
            // same way again. Try exactly once more, forcing HLS (the common case for
            // extension-less IPTV URLs); if we already tried that, give up immediately
            // instead of burning through the normal backoff retries.
            if (!forcedHlsRetry) {
                retryJob?.cancel()
                retryJob = scope.launch {
                    val url = lastPlayedUrl ?: return@launch
                    val position = _activePlayer.value.currentPosition
                    play(
                        url,
                        position,
                        lastPlayedTitle,
                        forceHlsMimeType = true,
                        userAgent = lastPlayedUserAgent,
                        referrer = lastPlayedReferrer
                    )
                }
            }
            return
        }

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

    /** Maps ExoPlayer's error codes to short, actionable messages instead of raw exception text. */
    private fun friendlyErrorMessage(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Network connection lost. Retrying…"
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            "Stream unavailable right now (server error). Retrying…"
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "Media file not found. It may have been moved or deleted."
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
            "Permission denied trying to access this media."
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED ->
            "This device can't decode this video/audio format."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
            "This stream's format isn't supported or the file is corrupted. (${error.errorCodeName})"
        PlaybackException.ERROR_CODE_TIMEOUT ->
            "Connection timed out."
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED ->
            "Couldn't load this stream. Check your connection and try again."
        else -> error.message ?: "Playback error (${error.errorCodeName})"
    }

    /** Manual retry (e.g. user taps a "Retry" banner) or automatic backoff retry. */
    fun retryPlayback() {
        val url = lastPlayedUrl ?: return
        val position = _activePlayer.value.currentPosition
        // Preserve whatever the last attempt was already using — otherwise tapping the
        // on-screen "Retry" button after an automatic forced-HLS retry (or on a channel
        // with a User-Agent/Referer override) would silently drop back to plain
        // defaults and immediately fail again the same way.
        val channel = lastPlayedUserAgent to lastPlayedReferrer
        play(
            url,
            position,
            lastPlayedTitle,
            forceHlsMimeType = forcedHlsRetry,
            userAgent = channel.first,
            referrer = channel.second
        )
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

    // ---- Audio tracks (multi-language / commentary tracks) ----

    fun availableAudioTracks(): List<AudioTrackInfo> {
        val tracks = localPlayer.currentTracks
        val result = mutableListOf<AudioTrackInfo>()
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val label = format.label ?: format.language ?: "Audio ${groupIndex + 1}.${trackIndex + 1}"
                    result.add(AudioTrackInfo(groupIndex, trackIndex, label, group.isTrackSelected(trackIndex)))
                }
            }
        }
        return result
    }

    fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {
        val group = localPlayer.currentTracks.groups.getOrNull(groupIndex) ?: return
        val override = TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
        trackSelector.parameters = trackSelector.parameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setOverrideForType(override)
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
            val metadataBuilder = MediaMetadata.Builder().setTitle(lastPlayedTitle ?: "")
            if (castAudioOnlyEnabled) {
                // Best-effort only: without a local transcoding pipeline (VLC's own
                // renderer strips video before sending; standard Google Cast has no
                // equivalent), the video track still reaches the receiver and gets
                // decoded there. This hint just tells the receiver's Default Media
                // Receiver UI to present it as an audio track (album-art style screen)
                // rather than a video player — it doesn't reduce bandwidth or actually
                // remove the video stream.
                metadataBuilder.setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            }
            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(metadataBuilder.build())
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

    /** Persisted preference plumbed in from Settings — see the doc comment on
     *  switchToCast for what this can and can't actually do. */
    fun setCastAudioOnly(enabled: Boolean) {
        castAudioOnlyEnabled = enabled
    }

    /**
     * Settings "Wireless casting" toggle. Disabling it tears down Cast entirely —
     * falling back to local playback if a session was active, and releasing the
     * CastContext/CastPlayer so the cast button disappears and no further device
     * discovery happens — rather than just hiding the button while still scanning.
     */
    fun setWirelessCastingEnabled(enabled: Boolean) {
        if (enabled) {
            if (castPlayer == null) initCast()
        } else {
            if (_isCasting.value) switchToLocal()
            castPlayer?.setSessionAvailabilityListener(null)
            castPlayer?.release()
            castPlayer = null
            _isCastAvailable.value = false
        }
    }

    fun release() {
        stopProgressTracker()
        retryJob?.cancel()
        sleepTimerJob?.cancel()
        mediaSession.release()
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        localPlayer.release()
    }
}
