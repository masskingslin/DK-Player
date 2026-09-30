@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.dk.tvplayer.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.dk.tvplayer.DkPlayerApplication
import com.dk.tvplayer.MainActivity
import com.dk.tvplayer.R

/** Keeps the remote-access server alive while the app is in the background. */
class RemoteAccessService : Service() {

    companion object {
        private const val CHANNEL_ID = "remote_access"
        private const val NOTIFICATION_ID = 4711
        private const val ACTION_STOP = "com.dk.tvplayer.remote.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, RemoteAccessService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RemoteAccessService::class.java))
        }
    }

    private var server: RemoteAccessServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            RemoteAccessConfig.setEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }
        enterForeground()
        if (server == null) {
            val app = application as DkPlayerApplication
            val candidate = RemoteAccessServer(applicationContext, app.playerManager)
            candidate.start()
                .onSuccess {
                    server = candidate
                    RemoteAccessState.setError(null)
                    RemoteAccessState.setRunning(true)
                    RemoteAccessBridge.server = candidate
                    updateNotification()
                }
                .onFailure {
                    RemoteAccessState.setError(it.message ?: "Couldn't start the server")
                    RemoteAccessConfig.setEnabled(false)
                    stopSelf()
                }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        RemoteAccessBridge.server = null
        RemoteAccessState.reset()
        super.onDestroy()
    }

    private fun enterForeground() {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Remote access", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RemoteAccessService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val address = RemoteAccessState.addresses.value.firstOrNull()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_notification)
            .setContentTitle("Remote access is on")
            .setContentText(address ?: "Starting…")
            .setContentIntent(open)
            .addAction(0, "Turn off", stop)
            .setOngoing(true)
            .build()
    }
}

/** Lets the settings screen reach the running server (to refresh the code / sign everyone out). */
object RemoteAccessBridge {
    @Volatile var server: RemoteAccessServer? = null
}
