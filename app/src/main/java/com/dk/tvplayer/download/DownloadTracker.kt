package com.dk.tvplayer.download

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DownloadItem(
    val url: String,
    val title: String,
    val state: Int, // Download.STATE_* constants
    val percentDownloaded: Float,
    val bytesDownloaded: Long
)

/**
 * Bridges [DownloadManager]'s listener-based API to a StateFlow the UI can collect,
 * and provides the simple start/remove actions the Downloads screen and playlist
 * "download" buttons need. One instance lives on [DownloadManagerHolder] for the
 * app's lifetime so it's always listening, even when no download screen is visible.
 */
@UnstableApi
class DownloadTracker(private val context: Context, private val downloadManager: DownloadManager) {

    private val _downloads = MutableStateFlow<Map<String, DownloadItem>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadItem>> = _downloads.asStateFlow()

    init {
        // Seed with whatever DownloadManager already knows about (e.g. downloads that
        // were in progress before the app was last closed).
        downloadManager.downloadIndex.getDownloads().use { cursor ->
            val initial = mutableMapOf<String, DownloadItem>()
            while (cursor.moveToNext()) {
                val download = cursor.download
                initial[download.request.uri.toString()] = download.toDownloadItem()
            }
            _downloads.value = initial
        }

        downloadManager.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                val key = download.request.uri.toString()
                _downloads.value = _downloads.value.toMutableMap().apply { put(key, download.toDownloadItem()) }
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                val key = download.request.uri.toString()
                _downloads.value = _downloads.value.toMutableMap().apply { remove(key) }
            }
        })
    }

    fun isDownloaded(url: String): Boolean = _downloads.value[url]?.state == Download.STATE_COMPLETED

    fun downloadStateFor(url: String): DownloadItem? = _downloads.value[url]

    fun startDownload(url: String, title: String) {
        val downloadRequest = DownloadRequest.Builder(url, android.net.Uri.parse(url))
            .setCustomCacheKey(url)
            .setData(title.toByteArray())
            .build()
        DownloadService.sendAddDownload(context, DkDownloadService::class.java, downloadRequest, false)
    }

    fun removeDownload(url: String) {
        DownloadService.sendRemoveDownload(context, DkDownloadService::class.java, url, false)
    }

    private fun Download.toDownloadItem(): DownloadItem {
        val title = runCatching { String(request.data) }.getOrDefault(request.uri.toString())
        return DownloadItem(
            url = request.uri.toString(),
            title = title,
            state = state,
            percentDownloaded = percentDownloaded,
            bytesDownloaded = bytesDownloaded
        )
    }
}
