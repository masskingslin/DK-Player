package com.dk.tvplayer.download

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
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
 * a side channel. Since this factory backs *all* playback (not just downloaded items),
 * two things matter here that didn't when it only served downloads:
 *  1) The upstream must be able to open local device files, not just http(s) URLs —
 *     a plain DefaultHttpDataSource.Factory cannot open a local video/audio file path
 *     at all, which previously broke every non-network playback source outright.
 *  2) It must not silently write everything you casually watch into the download
 *     cache — only content you explicitly downloaded (via DownloadManager, which has
 *     its own internal writer) should end up on disk.
 */
@UnstableApi
class DownloadManagerHolder(context: Context) {

    private val downloadDirectory: File = File(context.filesDir, "downloads")
    private val databaseProvider = StandaloneDatabaseProvider(context)

    // NoOpCacheEvictor: downloaded content is kept until the user explicitly deletes it
    // (unlike a small streaming-playback cache, which would want an LRU size limit).
    //
    // SimpleCache's constructor does synchronous disk I/O to open/validate its index,
    // and can throw if that index is corrupted — which can genuinely happen if the app
    // (or the device) was killed mid-download of a large file. Since this class is built
    // unconditionally in DkPlayerApplication.onCreate(), an uncaught exception here would
    // crash the app on *every* subsequent launch with no way to recover from the UI. So:
    // try the existing cache first, and if it's corrupted, wipe it and start fresh rather
    // than taking the whole app down. Losing an in-progress/corrupted download is a much
    // better outcome than a permanent crash loop.
    val downloadCache: Cache = openCacheOrRebuild(downloadDirectory, databaseProvider)

    // Handles http(s) URLs (IPTV channels, custom streams, downloads) AND local
    // device file / content URIs (local videos, local audio) uniformly — a plain
    // DefaultHttpDataSource.Factory only handled the former.
    private val upstreamDataSourceFactory = DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory())

    val cacheDataSourceFactory: CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(downloadCache)
        .setUpstreamDataSourceFactory(upstreamDataSourceFactory)
        // If a read misses or fails against the cache (e.g. seeking past what's
        // downloaded so far, or a corrupted cache entry), fall back to streaming from
        // the network/disk instead of failing playback outright.
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        // Read from the cache when a completed download exists, but do NOT write new
        // content into it during ordinary playback — only DownloadManager's own
        // downloader (used explicitly via the download buttons) should populate the
        // cache. Without this, every local file and every stream you watched would
        // get silently copied into app storage.
        .setCacheWriteDataSinkFactory(null)

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

    companion object {
        private fun openCacheOrRebuild(directory: File, databaseProvider: StandaloneDatabaseProvider): Cache {
            return try {
                SimpleCache(directory, NoOpCacheEvictor(), databaseProvider)
            } catch (t: Throwable) {
                // Index is corrupted (e.g. an interrupted large download) — wipe it and
                // start clean rather than crashing the app on every future launch.
                directory.deleteRecursively()
                SimpleCache(directory, NoOpCacheEvictor(), databaseProvider)
            }
        }
    }
}
