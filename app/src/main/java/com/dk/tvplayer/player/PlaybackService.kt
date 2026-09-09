package com.dk.tvplayer.player

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.dk.tvplayer.DkPlayerApplication

/**
 * A thin host for the shared [TvExoPlayerManager]'s [MediaSession]. It doesn't own or
 * create the player — that lives on [DkPlayerApplication] — it just exists so the
 * system can (a) keep the process alive as a foreground service while audio/video is
 * playing with the app backgrounded, and (b) show lock-screen / notification playback
 * controls, both of which [MediaSessionService] provides automatically once it's
 * hosting an active session with a playing player.
 */
class PlaybackService : MediaSessionService() {

    override fun onCreate() {
        super.onCreate()
        // The MediaSession is created once inside TvExoPlayerManager (shared with the
        // rest of the app); this service just needs to expose it via onGetSession.
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
}
