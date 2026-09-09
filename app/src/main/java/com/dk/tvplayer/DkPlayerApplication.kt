package com.dk.tvplayer

import android.app.Application
import androidx.media3.common.util.UnstableApi
import com.dk.tvplayer.data.local.SettingsDataStore
import com.dk.tvplayer.download.DownloadManagerHolder
import com.dk.tvplayer.player.TvExoPlayerManager
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
        settingsDataStore = SettingsDataStore(this)
        // Hardware acceleration is baked into the player at construction time (see
        // TvExoPlayerManager's doc comment), so this one settings read has to happen
        // synchronously before the player is built. It resolves instantly against the
        // DataStore's cached/default value, so this isn't a meaningful startup cost.
        val hwAccel = runBlocking { settingsDataStore.settingsFlow.first().hwAcceleration }
        downloadManagerHolder = DownloadManagerHolder(this)
        playerManager = TvExoPlayerManager(
            this,
            hwAccelerationEnabled = hwAccel,
            cacheDataSourceFactory = downloadManagerHolder.cacheDataSourceFactory
        )
        playerManager.initCast()
    }
}
