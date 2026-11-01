package com.dk.tvplayer

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import com.dk.tvplayer.data.local.SettingsDataStore
import com.dk.tvplayer.download.DownloadManagerHolder
import com.dk.tvplayer.player.TvExoPlayerManager
import com.dk.tvplayer.util.CrashLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * The player used to live entirely inside the ViewModel, which is fine for surviving
 * configuration changes but doesn't survive the Activity being destroyed — and a
 * background playback Service needs to reach the *same* player instance the UI is
 * using, not a second independent one. Hoisting it up to the Application makes both
 * MainActivity's ViewModel and PlaybackService share one player. The download manager
 * is here for the same reason: DkDownloadService needs the same DownloadManager/cache
 * instance the UI uses to start and monitor downloads.
 *
 * Startup is split in two so the process no longer blocks the main thread on disk I/O
 * before the first frame:
 *  1. [onCreate] only installs the crash logger and kicks off [startup] on a background
 *     thread: every SharedPreferences-backed store, plus the one DataStore read the
 *     player needs (hardware acceleration), which used to be a `runBlocking` on main.
 *  2. [playerManager] / [downloadManagerHolder] are built lazily, on the main thread
 *     (media3 players and the Cast SDK require the application looper), the first time
 *     something needs them — normally MainActivity, after it has drawn its first frame
 *     and awaited [awaitStartup]. Services started by the system before the UI exists
 *     simply wait for [startup] themselves.
 */
@UnstableApi
class DkPlayerApplication : Application() {

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var settingsDataStore: SettingsDataStore
        private set

    /** Completes with the stored "hardware acceleration" setting once all prefs are loaded. */
    private lateinit var startup: Deferred<Boolean>

    /** True once background initialisation has finished (safe to touch the prefs stores). */
    val isStartupDone: Boolean get() = startup.isCompleted

    /** Suspends (without blocking the main thread) until background initialisation is done. */
    suspend fun awaitStartup() {
        startup.await()
    }

    private class Core(
        val downloadManagerHolder: DownloadManagerHolder,
        val playerManager: TvExoPlayerManager
    )

    private val core: Core by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { buildCore() }

    val downloadManagerHolder: DownloadManagerHolder get() = core.downloadManagerHolder

    val playerManager: TvExoPlayerManager get() = core.playerManager

    override fun onCreate() {
        super.onCreate()
        // Installed before anything else so a crash during startup itself (rare, but
        // real — e.g. a bad DB migration) still gets logged rather than only ever
        // being visible to whoever happens to have a debugger attached.
        CrashLogger.install(this)
        recoverFromStartupCrashLoop()
        settingsDataStore = SettingsDataStore(this)

        startup = startupScope.async {
            // Each store is isolated: one corrupt preferences file must never take the
            // whole app down (or leave the other stores uninitialised).
            val inits = listOf<() -> Unit>(
                { com.dk.tvplayer.player.EqualizerStore.init(this@DkPlayerApplication) },
                { com.dk.tvplayer.util.DebugLogger.init(this@DkPlayerApplication) },
                { com.dk.tvplayer.util.AdvancedPrefs.init(this@DkPlayerApplication) },
                { com.dk.tvplayer.util.ParentalControl.init(this@DkPlayerApplication) },
                { com.dk.tvplayer.util.UiPrefs.init(this@DkPlayerApplication) },
                { com.dk.tvplayer.util.PlaybackPrefs.init(this@DkPlayerApplication) },
                { com.dk.tvplayer.util.MediaListCache.init(this@DkPlayerApplication) },
                { com.dk.tvplayer.util.SubtitlePrefs.init(this@DkPlayerApplication) },
                { com.dk.tvplayer.util.LockscreenCover.init(this@DkPlayerApplication) },
                { com.dk.tvplayer.remote.RemoteAccessConfig.init(this@DkPlayerApplication) }
            )
            inits.forEach { runCatching(it) }
            // Hardware acceleration is baked into the player at construction time (see
            // TvExoPlayerManager's doc comment), so this one settings read has to finish
            // before the player is built. If the preferences file can't be read it falls
            // back to the safe default (hardware acceleration on) instead of crashing.
            runCatching { settingsDataStore.settingsFlow.first().hwAcceleration }.getOrDefault(true)
        }
    }

    /**
     * Crash-loop guard. Every launch bumps a counter; the UI resets it once it has come up
     * (or the app is closed cleanly). If the counter reaches 2 the previous two launches never
     * got that far, so the saved settings file is the likeliest culprit: delete it so the app
     * opens with defaults instead of being stuck until the user clears app data by hand.
     */
    private fun recoverFromStartupCrashLoop() {
        val guard = getSharedPreferences("dk_startup_guard", MODE_PRIVATE)
        val failures = guard.getInt("failures", 0)
        if (failures >= 2) {
            runCatching { java.io.File(filesDir, "datastore").deleteRecursively() }
            guard.edit().putInt("failures", 0).commit()
        } else {
            guard.edit().putInt("failures", failures + 1).commit()
        }
    }

    /** Called once the UI is up or the app was closed normally: this launch was healthy. */
    fun markStartupHealthy() {
        getSharedPreferences("dk_startup_guard", MODE_PRIVATE).edit().putInt("failures", 0).apply()
    }

    private fun buildCore(): Core {
        // Normally already finished; only blocks if a service wins the race with startup.
        val hwAccel = runCatching { runBlocking { startup.await() } }.getOrDefault(true)
        val holder = DownloadManagerHolder(
            this,
            userAgent = com.dk.tvplayer.util.AdvancedPrefs.httpUserAgent.value
        )
        val player = TvExoPlayerManager(
            this,
            hwAccelerationEnabled = hwAccel &&
                com.dk.tvplayer.util.PlaybackPrefs.hardwareAcceleration.value != com.dk.tvplayer.util.HardwareAcceleration.DISABLED,
            cacheDataSourceFactory = holder.cacheDataSourceFactory,
            networkCachingMs = com.dk.tvplayer.util.AdvancedPrefs.networkCachingMs.value
        )
        // The Cast SDK must be initialised on the main thread, and probing Google Play
        // services is slow on some devices — do it right after startup, not inside it.
        Handler(Looper.getMainLooper()).post { runCatching { player.initCast() } }
        return Core(holder, player)
    }
}
