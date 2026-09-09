package com.dk.tvplayer.download

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import java.io.File
import java.util.concurrent.Executor

/**
 * Owns the on-disk download cache and the [DownloadManager] that manages downloads into
 * it. Application-scoped (built once, in [com.dk.tvplayer.DkPlayerApplication]) since
 * both the UI (starting/monitoring downloads) and [DkDownloadService] (running them in
 * the background) need to share the exact same cache and manager instance.
 *
 * The same [cacheDataSourceFactory] is also handed to the player (see
 * TvExoPlayerManager) so that once something finishes downloading, playing it
 * automatically reads from the local cache instead of hitting the network again —
 * that's the whole point of media3's cache being a first-class DataSource rather than
 * a side channel.
 */
@UnstableApi
class DownloadManagerHolder(context: Context) {

    private val downloadDirectory: File = File(context.filesDir, "downloads")
    private val databaseProvider = StandaloneDatabaseProvider(context)

    // NoOpCacheEvictor: downloaded content is kept until the user explicitly deletes it
    // (unlike a small streaming-playback cache, which would want an LRU size limit).
    val downloadCache: Cache = SimpleCache(downloadDirectory, NoOpCacheEvictor(), databaseProvider)

    private val upstreamDataSourceFactory = DefaultHttpDataSource.Factory()

    val cacheDataSourceFactory: CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(downloadCache)
        .setUpstreamDataSourceFactory(upstreamDataSourceFactory)
        // If a read misses the cache (e.g. seeking past what's downloaded so far),
        // fall back to streaming from the network instead of failing outright.
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    val downloadManager: DownloadManager = DownloadManager(
        context,
        databaseProvider,
        downloadCache,
        upstreamDataSourceFactory,
        Executor(Runnable::run)
    ).apply {
        maxParallelDownloads = 3
    }

    val downloadTracker = DownloadTracker(context, downloadManager)
}
