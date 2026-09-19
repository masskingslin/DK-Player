package com.dk.tvplayer

import android.app.Application
import androidx.media3.common.util.UnstableApi
import com.dk.tvplayer.data.local.SettingsDataStore
import com.dk.tvplayer.download.DownloadManagerHolder
import com.dk.tvplayer.player.TvExoPlayerManager
import com.dk.tvplayer.util.CrashLogger
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
 */
@UnstableApi
class DkPlayerApplication : Application() {

    lateinit var settingsDataStore: SettingsDataStore
        private set

    lateinit var playerManager: TvExoPlayerManager
        private set

    lateinit var downloadManagerHolder: DownloadManagerHolder
        private set

    override fun onCreate() {
        super.onCreate()
        // Installed before anything else so a crash during startup itself (rare, but
        // real — e.g. a bad DB migration) still gets logged rather than only ever
        // being visible to whoever happens to have a debugger attached.
        CrashLogger.install(this)
        settingsDataStore = SettingsDataStore(this)
        // Hardware acceleration is baked into the player at construction time (see
        // TvExoPlayerManager's doc comment), so this one settings read has to happen
        // synchronously before the player is built. It normally resolves instantly
        // against the DataStore's cached/default value, but it's still disk I/O on the
        // main thread during app startup — if the preferences file is slow to open or
        // fails to read for any reason, this must never be allowed to crash or hang
        // app launch, so it falls back to the safe default (hardware acceleration on).
        val hwAccel = runCatching {
            runBlocking { settingsDataStore.settingsFlow.first().hwAcceleration }
        }.getOrDefault(true)
        downloadManagerHolder = DownloadManagerHolder(this)
        playerManager = TvExoPlayerManager(
            this,
            hwAccelerationEnabled = hwAccel,
            cacheDataSourceFactory = downloadManagerHolder.cacheDataSourceFactory
        )
        playerManager.initCast()
    }
}
