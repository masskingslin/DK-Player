package com.dk.tvplayer.util

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts a single frame from a local video file to use as a list thumbnail, with a
 * small in-memory LRU cache so scrolling the video library doesn't repeatedly hit disk.
 * Used by VideoLibraryScreen when "Video thumbnails in lists" is enabled in Settings.
 */
object VideoThumbnailLoader {

    // Sized generously enough for a couple of screens' worth of thumbnails; each entry
    // is a small downscaled bitmap so this stays well within a reasonable memory budget.
    private val cache = LruCache<String, Bitmap>(60)

    suspend fun getThumbnail(filePath: String): Bitmap? {
        cache.get(filePath)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(filePath)
                    val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    frame?.also { cache.put(filePath, it) }
                }
            }.getOrNull()
        }
    }

    /** MediaMetadataRetriever doesn't implement Closeable on older API levels — small shim. */
    private inline fun <T : MediaMetadataRetriever, R> T.use(block: (T) -> R): R {
        try {
            return block(this)
        } finally {
            runCatching { release() }
        }
    }
}
