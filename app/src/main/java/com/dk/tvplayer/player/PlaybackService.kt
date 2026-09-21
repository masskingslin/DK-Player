package com.dk.tvplayer.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.dk.tvplayer.DkPlayerApplication
import com.dk.tvplayer.R

/**
 * A thin host for the shared [TvExoPlayerManager]'s [MediaSession]. It doesn't own or
 * create the player — that lives on [DkPlayerApplication] — it just exists so the
 * system can (a) keep the process alive as a foreground service while audio/video is
 * playing with the app backgrounded, and (b) show lock-screen / notification playback
 * controls, both of which [MediaSessionService] provides automatically once it's
 * hosting an active session with a playing player.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    override fun onCreate() {
        super.onCreate()
        // TvExoPlayerManager starts this service with Context.startForegroundService(),
        // which gives the OS a hard ~5s deadline to call Service.startForeground() —
        // miss it and the system kills the whole process with
        // ForegroundServiceDidNotStartInTimeException (this is exactly what showed up
        // in the field: "sometimes crashes" because it's a race, not a guaranteed
        // failure). MediaSessionService normally promotes itself to foreground on its
        // own once it notices the session's player is actively playing, but that path
        // depends on its internal listener attaching and (for the real notification)
        // on the channel's logo loading over the network for the notification icon —
        // neither of which is guaranteed to land inside that 5s window, especially
        // when the player was *already* playing before this service process was even
        // started (so there's no new state-change event left to trigger it) or the
        // logo host is slow/unreachable. Posting an immediate, no-network-dependency
        // placeholder here guarantees the deadline is met regardless of that timing;
        // MediaSessionService's own notification then simply replaces it moments later
        // once it's ready — calling startForeground() a second time to swap the
        // notification is normal and explicitly supported.
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(PLACEHOLDER_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    PLACEHOLDER_CHANNEL_ID,
                    "Playback",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val placeholderNotification = NotificationCompat.Builder(this, PLACEHOLDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("DK Player")
            .setContentText("Starting playback…")
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this,
            PLACEHOLDER_NOTIFICATION_ID,
            placeholderNotification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return (application as DkPlayerApplication).playerManager.mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // If the user swipes the app away from Recents while nothing is actually
        // playing, there's no reason to keep the foreground service (and its
        // notification) alive.
        val player = (application as DkPlayerApplication).playerManager.exoPlayer
        if (!player.isPlaying) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // Do NOT release the player or the MediaSession here — both are owned by
        // DkPlayerApplication and may still be in active use (e.g. the app UI is still
        // open, or a Cast session is running).
        super.onDestroy()
    }

    private companion object {
        const val PLACEHOLDER_CHANNEL_ID = "dk_playback_starting"
        const val PLACEHOLDER_NOTIFICATION_ID = 9001
    }
}
