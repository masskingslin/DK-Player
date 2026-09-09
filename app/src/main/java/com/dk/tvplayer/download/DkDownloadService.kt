package com.dk.tvplayer.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.dk.tvplayer.DkPlayerApplication
import com.dk.tvplayer.R

private const val JOB_ID = 1000
private const val FOREGROUND_NOTIFICATION_ID = 2000
private const val CHANNEL_ID = "download_channel"

@UnstableApi
class DkDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DownloadService.DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_channel_name,
    0
) {
    private val notificationHelper by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }

    override fun getDownloadManager(): DownloadManager {
        ensureNotificationChannel(this)
        return (application as DkPlayerApplication).downloadManagerHolder.downloadManager
    }

    override fun getScheduler(): Scheduler? =
        if (Build.VERSION.SDK_INT >= 21) PlatformScheduler(this, JOB_ID) else null

    override fun getForegroundNotification(downloads: List<Download>, notMetRequirements: Int): Notification {
        return notificationHelper.buildProgressNotification(
            this,
            R.drawable.ic_download_notification,
            null,
            null,
            downloads,
            notMetRequirements
        )
    }

    companion object {
        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.download_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
                manager.createNotificationChannel(channel)
            }
        }
    }
}
