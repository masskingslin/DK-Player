@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.dk.tvplayer.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.C
import androidx.media3.common.Format
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
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.core.content.ContextCompat
import com.dk.tvplayer.R
import com.dk.tvplayer.data.local.QueueFormat
import com.dk.tvplayer.data.local.QueueInfoPosition
import com.dk.tvplayer.data.parser.M3uParser
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

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
 * Loop points for the "A-B repeat" menu action, in player position ms. Once both are
 * set, the progress tracker (see TvExoPlayerManager.startProgressTracker) seeks back
 * to [pointAMs] whenever playback reaches [pointBMs].
 */
data class AbRepeatState(
    val pointAMs: Long? = null,
    val pointBMs: Long? = null
)

data class EqualizerBandInfo(
    val index: Int,
    val centerFreqHz: Int,
    val minLevelMillibel: Int,
    val maxLevelMillibel: Int,
    val currentLevelMillibel: Int
)

data class EqualizerState(
    // False until a real audio session id exists to attach to (i.e. before playback
    // has actually started) — the Equalizer dialog uses this to show "not available
    // yet" instead of an empty band list.
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<EqualizerBandInfo> = emptyList(),
    val presets: List<String> = emptyList(),
    // Index into `presets`, or -1 once a band's been hand-tuned away from any preset.
    val currentPreset: Int = -1
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

        // ---- Android Auto ----
        // The package Android Auto's phone-side app connects to media sessions with.
        // This is the standard "phone projecting to the car" setup the Settings ->
        // Android Auto toggles are about; it doesn't cover every possible Android
        // Automotive OS head unit, but it's the one documented, stable identifier for
        // Android Auto itself (see AOSP's allowed_media_browser_callers.xml sample).
        private const val ANDROID_AUTO_PACKAGE_NAME = "com.google.android.projection.gearhead"
        private const val ACTION_SEEK_BACK = "com.dk.tvplayer.androidauto.SEEK_BACK"
        private const val ACTION_SEEK_FORWARD = "com.dk.tvplayer.androidauto.SEEK_FORWARD"
        private const val ACTION_CYCLE_SPEED = "com.dk.tvplayer.androidauto.CYCLE_SPEED"
        private const val ANDROID_AUTO_SEEK_INCREMENT_MS = 10_000L
        private val ANDROID_AUTO_SPEED_STEPS = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 0.75f)
        // Baseline character budget for the title/subtitle sent to Android Auto at a
        // 1.0x text-size scale — see truncateForCarTextScale.
        private const val ANDROID_AUTO_TEXT_BASE_LENGTH = 60
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

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatModeFlow: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _abRepeatState = MutableStateFlow(AbRepeatState())
    val abRepeatStateFlow: StateFlow<AbRepeatState> = _abRepeatState.asStateFlow()

    private val _audioOnlyModeEnabled = MutableStateFlow(false)
    val audioOnlyModeEnabledFlow: StateFlow<Boolean> = _audioOnlyModeEnabled.asStateFlow()

    private var equalizer: android.media.audiofx.Equalizer? = null
    private val _equalizerState = MutableStateFlow(EqualizerState())
    val equalizerStateFlow: StateFlow<EqualizerState> = _equalizerState.asStateFlow()

    private var lastPlayedUrl: String? = null
    private var lastPlayedTitle: String? = null
    // Undecorated subtitle (e.g. an IPTV channel's category) as passed into play() —
    // kept separate from whatever ends up in the MediaItem's actual metadata so the
    // Android Auto queue-position segment (see buildDecoratedSubtitle) never gets
    // appended on top of itself across repeated metadata refreshes.
    private var lastPlayedSubtitle: String? = null
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

    // ---- Android Auto settings (see SettingsDataStore's androidAuto* fields) ----
    private var androidAutoTitleTextScale = 1.0f
    private var androidAutoSubtitleTextScale = 1.0f
    private var androidAutoQueueInfoPosition = QueueInfoPosition.BEFORE_SUBTITLE
    private var androidAutoQueueFormat = QueueFormat.POSITION_SLASH_SIZE
    private var androidAutoUseGlobalPlaybackSpeed = false
    private var androidAutoPlaybackSpeedControlEnabled = false
    private var androidAutoSeekButtonsEnabled = false
    // The persisted app-wide "Default Playback Speed" (distinct from _playbackSpeed,
    // which also tracks any one-off override made through the in-player speed menu) —
    // only consulted when androidAutoUseGlobalPlaybackSpeed is on, see play().
    private var defaultPlaybackSpeedSetting = 1.0f
    // Where the currently playing item sits within whatever browsable list the caller
    // knows about (e.g. the IPTV channel list being surfed) — null/null when there's no
    // such context, in which case no queue segment is shown. Set via
    // updateAndroidAutoQueueContext().
    private var androidAutoQueuePosition: Int? = null
    private var androidAutoQueueSize: Int? = null

    private fun isAndroidAutoController(controller: MediaSession.ControllerInfo): Boolean =
        controller.packageName == ANDROID_AUTO_PACKAGE_NAME

    /**
     * Builds the extra overflow buttons this controller should see, per the Settings ->
     * Android Auto -> Controls toggles. Only Android Auto itself gets these — the
     * phone's own notification controls are unaffected.
     */
    private fun buildAndroidAutoCustomLayout(controller: MediaSession.ControllerInfo): List<CommandButton> {
        if (!isAndroidAutoController(controller)) return emptyList()
        val buttons = mutableListOf<CommandButton>()
        if (androidAutoSeekButtonsEnabled) {
            buttons += CommandButton.Builder()
                .setDisplayName("Rewind 10 seconds")
                .setIconResId(android.R.drawable.ic_media_rew)
                .setSessionCommand(SessionCommand(ACTION_SEEK_BACK, Bundle.EMPTY))
                .build()
            buttons += CommandButton.Builder()
                .setDisplayName("Forward 10 seconds")
                .setIconResId(android.R.drawable.ic_media_ff)
                .setSessionCommand(SessionCommand(ACTION_SEEK_FORWARD, Bundle.EMPTY))
                .build()
        }
        if (androidAutoPlaybackSpeedControlEnabled) {
            buttons += CommandButton.Builder()
                .setDisplayName("Playback speed")
                .setIconResId(R.drawable.ic_playback_speed)
                .setSessionCommand(SessionCommand(ACTION_CYCLE_SPEED, Bundle.EMPTY))
                .build()
        }
        return buttons
    }

    /** Pushes an updated overflow layout to any already-connected Android Auto controller —
     *  called whenever one of the Controls toggles changes while the car is connected. */
    private fun refreshAndroidAutoCustomLayout() {
        mediaSession.connectedControllers
            .filter { isAndroidAutoController(it) }
            .forEach { controller -> mediaSession.setCustomLayout(controller, buildAndroidAutoCustomLayout(controller)) }
    }

    private fun cycleAndroidAutoPlaybackSpeed() {
        val current = localPlayer.playbackParameters.speed
        val currentIndex = ANDROID_AUTO_SPEED_STEPS.indexOfFirst { abs(it - current) < 0.01f }
        val next = ANDROID_AUTO_SPEED_STEPS[(currentIndex + 1).coerceAtLeast(0) % ANDROID_AUTO_SPEED_STEPS.size]
        setPlaybackSpeed(next)
    }

    private val androidAutoSessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(ACTION_SEEK_BACK, Bundle.EMPTY))
                .add(SessionCommand(ACTION_SEEK_FORWARD, Bundle.EMPTY))
                .add(SessionCommand(ACTION_CYCLE_SPEED, Bundle.EMPTY))
                .build()
            refreshSessionMetadataForCar()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(availableSessionCommands)
                .setCustomLayout(buildAndroidAutoCustomLayout(controller))
                .build()
        }

        override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
            if (isAndroidAutoController(controller)) {
                // Drop back to the un-truncated title/subtitle now that the car (the
                // only reason the metadata was shortened/queue-decorated) is gone.
                refreshSessionMetadataForCar()
            }
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                // seekTo() itself clamps into [0, duration], so the raw arithmetic here
                // doesn't need its own bounds-checking.
                ACTION_SEEK_BACK -> seekTo(_activePlayer.value.currentPosition - ANDROID_AUTO_SEEK_INCREMENT_MS)
                ACTION_SEEK_FORWARD -> seekTo(_activePlayer.value.currentPosition + ANDROID_AUTO_SEEK_INCREMENT_MS)
                ACTION_CYCLE_SPEED -> cycleAndroidAutoPlaybackSpeed()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    /** Wraps localPlayer so PlaybackService (and, if ever needed, other controllers) can
     *  discover and control the same player instance the UI is using. */
    val mediaSession: MediaSession = MediaSession.Builder(context, localPlayer)
        .setCallback(androidAutoSessionCallback)
        .build()

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
     * @param subtitle Optional secondary line (e.g. an IPTV channel's category) shown
     * under the title — also where the Android Auto "Queue information" segment gets
     * stitched in, see buildDecoratedSubtitle.
     */
    fun play(
        url: String,
        startPositionMs: Long = 0L,
        title: String? = null,
        forceHlsMimeType: Boolean = false,
        userAgent: String? = null,
        referrer: String? = null,
        subtitle: String? = null
    ) {
        // A-B loop points are positions within one specific item — a different item
        // would seek to meaningless spots. Same-URL replays (error retries, cast
        // handoff) keep the loop.
        if (url != lastPlayedUrl) _abRepeatState.value = AbRepeatState()
        lastPlayedUrl = url
        lastPlayedTitle = title ?: lastPlayedTitle
        lastPlayedSubtitle = subtitle
        lastPlayedUserAgent = userAgent
        lastPlayedReferrer = referrer
        retryAttempt = 0
        forcedHlsRetry = forceHlsMimeType
        _playbackError.value = null
        retryJob?.cancel()
        // A fresh play() call starts a new "now playing" item with no known queue
        // context until the caller says otherwise via updateAndroidAutoQueueContext —
        // otherwise a stale "3/12" from the previous item could linger onto this one.
        androidAutoQueuePosition = null
        androidAutoQueueSize = null

        val target = _activePlayer.value
        val metadataBuilder = MediaMetadata.Builder().setTitle(lastPlayedTitle ?: "")
        if (!subtitle.isNullOrBlank()) metadataBuilder.setSubtitle(subtitle)
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(metadataBuilder.build())
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
        // Normally whatever speed was last active (including a one-off override from
        // the in-player speed menu) carries forward to the next item. When "Use the
        // global playback speed" is on, every new item instead starts fresh at the
        // persisted Default Playback Speed, ignoring that carry-over.
        val speedToApply = if (androidAutoUseGlobalPlaybackSpeed) defaultPlaybackSpeedSetting else _playbackSpeed.value
        _playbackSpeed.value = speedToApply
        target.setPlaybackSpeed(speedToApply)
        refreshSessionMetadataForCar()
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

    // ---- Repeat mode ----

    fun cycleRepeatMode() {
        val next = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = next
        localPlayer.repeatMode = next
        castPlayer?.repeatMode = next
    }

    // ---- A-B repeat ----

    /** First tap of the "A-B repeat" menu item: marks the loop's start. Clears any
     *  previous B point, since a fresh A point means a fresh loop. */
    fun setAbRepeatPointA() {
        _abRepeatState.value = AbRepeatState(pointAMs = _activePlayer.value.currentPosition, pointBMs = null)
    }

    /** Second tap: marks the loop's end and starts enforcing it. Ignored if it's not
     *  actually after point A, since that's not a valid loop. */
    fun setAbRepeatPointB() {
        val a = _abRepeatState.value.pointAMs ?: return
        val position = _activePlayer.value.currentPosition
        if (position > a) {
            _abRepeatState.value = _abRepeatState.value.copy(pointBMs = position)
        }
    }

    fun clearAbRepeat() {
        _abRepeatState.value = AbRepeatState()
    }

    // ---- Play as audio ----

    /** Disables the video track selection so only audio decodes/renders — same
     *  practical effect as YouTube's "audio mode": less battery/CPU, and the phone
     *  screen can turn off without stopping playback (subject to backgroundPlaybackEnabled). */
    fun setAudioOnlyModeEnabled(enabled: Boolean) {
        _audioOnlyModeEnabled.value = enabled
        trackSelector.parameters = trackSelector.parameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, enabled)
            .build()
    }

    // ---- Video information ----

    /** Snapshot of the format the video/audio renderers are currently consuming —
     *  codec, resolution, bitrate, sample rate, etc. — for the "Video information" dialog. */
    fun currentVideoFormat(): Format? = localPlayer.videoFormat
    fun currentAudioFormat(): Format? = localPlayer.audioFormat

    // ---- Equalizer ----

    /** Attaches (or reuses) an Equalizer effect on the local player's current audio
     *  session. Returns null before playback has produced a real session id yet, or if
     *  the device genuinely has no equalizer effect available — both are normal and
     *  the dialog should just show "not available" rather than treating it as an error. */
    private fun ensureEqualizer(): android.media.audiofx.Equalizer? {
        val sessionId = localPlayer.audioSessionId
        if (sessionId == 0) return null
        equalizer?.let { return it }
        return try {
            android.media.audiofx.Equalizer(0, sessionId).also { equalizer = it }
        } catch (t: Throwable) {
            null
        }
    }

    fun refreshEqualizerState() {
        val eq = ensureEqualizer()
        if (eq == null) {
            _equalizerState.value = EqualizerState()
            return
        }
        val range = eq.bandLevelRange
        val numberOfBands = eq.numberOfBands.toInt()
        val bands = (0 until numberOfBands).map { i ->
            val band = i.toShort()
            EqualizerBandInfo(
                index = i,
                centerFreqHz = eq.getCenterFreq(band) / 1000,
                minLevelMillibel = range[0].toInt(),
                maxLevelMillibel = range[1].toInt(),
                currentLevelMillibel = eq.getBandLevel(band).toInt()
            )
        }
        val numberOfPresets = eq.numberOfPresets.toInt()
        val presets = (0 until numberOfPresets).map { eq.getPresetName(it.toShort()) }
        _equalizerState.value = EqualizerState(
            available = true,
            enabled = eq.enabled,
            bands = bands,
            presets = presets,
            currentPreset = runCatching { eq.currentPreset.toInt() }.getOrDefault(-1)
        )
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        val eq = ensureEqualizer() ?: return
        // AudioEffect.setEnabled() returns a status code rather than Unit, so it's
        // called explicitly here rather than via Kotlin's `eq.enabled = enabled`
        // synthetic-property syntax.
        eq.setEnabled(enabled)
        refreshEqualizerState()
    }

    fun setEqualizerBandLevel(bandIndex: Int, levelMillibel: Int) {
        val eq = ensureEqualizer() ?: return
        eq.setEnabled(true)
        eq.setBandLevel(bandIndex.toShort(), levelMillibel.toShort())
        refreshEqualizerState()
    }

    fun setEqualizerPreset(preset: Int) {
        val eq = ensureEqualizer() ?: return
        eq.setEnabled(true)
        eq.usePreset(preset.toShort())
        refreshEqualizerState()
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = scope.launch {
            while (isActive) {
                val player = _activePlayer.value
                _currentPositionFlow.value = player.currentPosition
                _durationFlow.value = player.duration.coerceAtLeast(0L)

                // A-B repeat enforcement: this 500ms poll interval means the loop can
                // overshoot point B by up to that much before snapping back — fine for
                // the "replay this line/verse" use case this feature targets.
                val ab = _abRepeatState.value
                val pointB = ab.pointBMs
                if (ab.pointAMs != null && pointB != null && player.currentPosition >= pointB) {
                    player.seekTo(ab.pointAMs)
                }

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
                val queueContext = androidAutoQueuePosition to androidAutoQueueSize
                retryJob = scope.launch {
                    val url = lastPlayedUrl ?: return@launch
                    val position = _activePlayer.value.currentPosition
                    play(
                        url,
                        position,
                        lastPlayedTitle,
                        forceHlsMimeType = true,
                        userAgent = lastPlayedUserAgent,
                        referrer = lastPlayedReferrer,
                        subtitle = lastPlayedSubtitle
                    )
                    // Same item, just forcing the HLS mimetype — restore queue context
                    // that play() otherwise clears for what it treats as a new item.
                    updateAndroidAutoQueueContext(queueContext.first, queueContext.second)
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
        val queueContext = androidAutoQueuePosition to androidAutoQueueSize
        play(
            url,
            position,
            lastPlayedTitle,
            forceHlsMimeType = forcedHlsRetry,
            userAgent = channel.first,
            referrer = channel.second,
            subtitle = lastPlayedSubtitle
        )
        // play() unconditionally clears queue context for what it treats as a "new"
        // item — but a retry is the same item, so restore it.
        updateAndroidAutoQueueContext(queueContext.first, queueContext.second)
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
            cast.repeatMode = _repeatMode.value
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
            play(url, position, lastPlayedTitle, subtitle = lastPlayedSubtitle)
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

    // ---- Android Auto ----

    fun setAndroidAutoTitleTextScale(scale: Float) {
        androidAutoTitleTextScale = scale
        refreshSessionMetadataForCar()
    }

    fun setAndroidAutoSubtitleTextScale(scale: Float) {
        androidAutoSubtitleTextScale = scale
        refreshSessionMetadataForCar()
    }

    fun setAndroidAutoQueueInfoPosition(position: QueueInfoPosition) {
        androidAutoQueueInfoPosition = position
        refreshSessionMetadataForCar()
    }

    fun setAndroidAutoQueueFormat(format: QueueFormat) {
        androidAutoQueueFormat = format
        refreshSessionMetadataForCar()
    }

    fun setAndroidAutoUseGlobalPlaybackSpeed(enabled: Boolean) {
        androidAutoUseGlobalPlaybackSpeed = enabled
    }

    fun setAndroidAutoPlaybackSpeedControlEnabled(enabled: Boolean) {
        androidAutoPlaybackSpeedControlEnabled = enabled
        refreshAndroidAutoCustomLayout()
    }

    fun setAndroidAutoSeekButtonsEnabled(enabled: Boolean) {
        androidAutoSeekButtonsEnabled = enabled
        refreshAndroidAutoCustomLayout()
    }

    /** Mirrors the persisted "Default Playback Speed" setting — see the doc comment on
     *  defaultPlaybackSpeedSetting for why this is tracked separately from _playbackSpeed. */
    fun setDefaultPlaybackSpeedSetting(speed: Float) {
        defaultPlaybackSpeedSetting = speed
    }

    /**
     * Lets a caller that knows the current item's place within a browsable list (e.g.
     * the IPTV channel list currently being surfed) report it, purely so it can be
     * reflected in the "Queue information" Android Auto metadata. Pass null/null when
     * there's no such context (e.g. a single local video with nothing "before" or
     * "after" it) so no queue segment is shown.
     */
    fun updateAndroidAutoQueueContext(position: Int?, size: Int?) {
        androidAutoQueuePosition = position
        androidAutoQueueSize = size
        refreshSessionMetadataForCar()
    }

    /**
     * Android Auto doesn't expose a font-size API to apps — the car head unit is what
     * actually renders the metadata text. What we *can* influence is how much of a long
     * title/subtitle fits on the fixed-width car display before Android Auto has to
     * clip it awkwardly: a larger "text size" here maps to a shorter kept length, on
     * the assumption bigger glyphs leave less horizontal room. Applied only while
     * Android Auto is actually connected (see refreshSessionMetadataForCar), so it
     * never shortens the title shown in the app's own UI, notification, or lock screen
     * when the car isn't in the picture.
     */
    private fun truncateForCarTextScale(text: String, scale: Float): String {
        val maxLength = (ANDROID_AUTO_TEXT_BASE_LENGTH / scale.coerceAtLeast(0.1f)).toInt().coerceAtLeast(12)
        return if (text.length <= maxLength) text else text.take(maxLength - 1).trimEnd() + "…"
    }

    /** The "N/M"-style (or similar) segment described by androidAutoQueueFormat, or
     *  null when there's no queue context or a "queue" of a single item to describe. */
    private fun androidAutoQueueSegment(): String? {
        if (androidAutoQueueInfoPosition == QueueInfoPosition.DISABLED) return null
        val position = androidAutoQueuePosition ?: return null
        val size = androidAutoQueueSize ?: return null
        if (size <= 1) return null
        return when (androidAutoQueueFormat) {
            QueueFormat.POSITION_SLASH_SIZE -> "$position/$size"
            QueueFormat.POSITION_ONLY -> "$position"
            QueueFormat.TRACKS_REMAINING -> {
                val remaining = (size - position).coerceAtLeast(0)
                if (remaining == 1) "1 track remaining" else "$remaining tracks remaining"
            }
        }
    }

    /** Stitches the queue segment above into the base (undecorated) subtitle, per
     *  androidAutoQueueInfoPosition. Returns the base subtitle unchanged if there's no
     *  segment to add. */
    private fun buildDecoratedSubtitle(baseSubtitle: String?): String? {
        val segment = androidAutoQueueSegment() ?: return baseSubtitle
        return when (androidAutoQueueInfoPosition) {
            QueueInfoPosition.BEFORE_SUBTITLE ->
                if (baseSubtitle.isNullOrBlank()) segment else "$segment · $baseSubtitle"
            QueueInfoPosition.AFTER_SUBTITLE ->
                if (baseSubtitle.isNullOrBlank()) segment else "$baseSubtitle · $segment"
            QueueInfoPosition.DISABLED -> baseSubtitle
        }
    }

    /**
     * Rebuilds the current MediaItem's title/subtitle metadata from the pristine
     * lastPlayedTitle/lastPlayedSubtitle (never from whatever's already in the
     * MediaItem, which may already be decorated from a previous call — reading that
     * back would compound the queue segment on every refresh) and pushes it via
     * replaceMediaItem, which updates the session's metadata without restarting
     * playback or losing position.
     *
     * Note this metadata is shared by every surface reading this MediaSession (the
     * phone notification and lock screen included) — Media3 has no per-controller
     * metadata, so while Android Auto is connected, its car-sized truncation is what
     * the phone notification shows too. That's why it's only applied while a car is
     * actually connected rather than unconditionally.
     */
    private fun refreshSessionMetadataForCar() {
        if (localPlayer.mediaItemCount == 0) return
        val current = localPlayer.currentMediaItem ?: return
        val carConnected = mediaSession.connectedControllers.any { isAndroidAutoController(it) }

        val baseTitle = lastPlayedTitle?.takeIf { it.isNotBlank() } ?: ""
        val decoratedSubtitle = buildDecoratedSubtitle(lastPlayedSubtitle)

        val newTitle = if (carConnected) truncateForCarTextScale(baseTitle, androidAutoTitleTextScale) else baseTitle
        val newSubtitle = when {
            decoratedSubtitle.isNullOrBlank() -> null
            carConnected -> truncateForCarTextScale(decoratedSubtitle, androidAutoSubtitleTextScale)
            else -> decoratedSubtitle
        }

        val metadataBuilder = current.mediaMetadata.buildUpon().setTitle(newTitle)
        if (newSubtitle != null) metadataBuilder.setSubtitle(newSubtitle)
        val updatedMetadata = metadataBuilder.build()
        if (updatedMetadata == current.mediaMetadata) return

        val index = localPlayer.currentMediaItemIndex
        localPlayer.replaceMediaItem(index, current.buildUpon().setMediaMetadata(updatedMetadata).build())
    }

    fun release() {
        stopProgressTracker()
        retryJob?.cancel()
        sleepTimerJob?.cancel()
        mediaSession.release()
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        equalizer?.release()
        localPlayer.release()
    }
}
