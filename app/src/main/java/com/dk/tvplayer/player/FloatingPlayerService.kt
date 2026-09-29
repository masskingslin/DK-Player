package com.dk.tvplayer.player

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.dk.tvplayer.DkPlayerApplication
import com.dk.tvplayer.FloatingPlayerState
import com.dk.tvplayer.MainActivity
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A real floating "pop-up player" — as opposed to Android's built-in Picture-in-Picture,
 * which only works while this app's own Activity is still transitioning to the
 * background and is confined to a PiP-managed corner slot. This is a genuine system
 * overlay window (android.permission.SYSTEM_ALERT_WINDOW): it keeps showing live video
 * no matter what other app the person switches to afterwards, and it can be dragged
 * anywhere on screen.
 *
 * It attaches its own [PlayerView] directly to the single shared ExoPlayer instance
 * ([DkPlayerApplication.playerManager]'s `localPlayer`) — the same player
 * PhonePlayerScreen renders. Starting the pop-up just relocates *where* that player's
 * video surface is drawn (ExoPlayer only ever draws to the most recently attached
 * PlayerView); it does not create a second, independent playback session, so play/pause/
 * seek state is exactly what it was in the app.
 *
 * This service deliberately does not run as its own foreground service with its own
 * notification. Real playback — and the process-keeping-alive foreground state — is
 * already owned by [PlaybackService], which is running whenever there's anything to
 * show in a pop-up in the first place; this service only needs to survive as long as
 * that one does.
 */
@UnstableApi
class FloatingPlayerService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingContainer: View? = null
    private var playerView: PlayerView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (floatingContainer == null) {
            showFloatingWindow()
        }
        return START_NOT_STICKY
    }

    private fun showFloatingWindow() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val player = (application as DkPlayerApplication).playerManager.localPlayer
        val density = resources.displayMetrics.density
        val widthPx = (240 * density).roundToInt()
        val heightPx = (widthPx * 9 / 16)
        val closeButtonSizePx = (28 * density).roundToInt()
        // How far a touch has to move before it counts as a drag rather than a tap that
        // should expand back into the app — matches Android's typical touch-slop feel.
        val dragThresholdPx = (8 * density)

        val container = FrameLayout(this)

        val pv = PlayerView(this).apply {
            this.player = player
            useController = false
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        playerView = pv
        container.addView(pv)

        val closeButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(160, 0, 0, 0))
            }
            layoutParams = FrameLayout.LayoutParams(closeButtonSizePx, closeButtonSizePx, Gravity.TOP or Gravity.END).apply {
                topMargin = (4 * density).roundToInt()
                rightMargin = (4 * density).roundToInt()
            }
            // A real click listener (not routed through the container's drag handler
            // below) works here because it's a separate child view: Android offers a
            // touch sequence to whichever view is under the initial ACTION_DOWN first,
            // so a tap starting on this button never reaches the container at all.
            setOnClickListener { stopSelf() }
        }
        container.addView(closeButton)

        floatingContainer = container

        val params = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            // TYPE_APPLICATION_OVERLAY is the only overlay window type available from
            // API 26 onward (this app's minSdk), so there's no older fallback to branch on.
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (16 * density).roundToInt()
            y = (140 * density).roundToInt()
        }

        // Dragging and "tap the video to expand" share one touch listener on the
        // container (rather than a separate click listener on the video area) because
        // a click listener there would swallow the ACTION_DOWN and prevent this drag
        // logic from ever seeing the gesture. A touch only counts as a tap — and
        // triggers the expand action — if it never moved past the drag threshold.
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!isDragging && (abs(dx) > dragThresholdPx || abs(dy) > dragThresholdPx)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = initialX + dx.roundToInt()
                        params.y = initialY + dy.roundToInt()
                        runCatching { wm.updateViewLayout(container, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) expandToApp()
                    true
                }
                else -> false
            }
        }

        runCatching { wm.addView(container, params) }
            .onSuccess { FloatingPlayerState.isActive = true }
            .onFailure {
                // Permission was revoked, or something else about this window is invalid —
                // either way there's nothing to show, so don't leave the service running.
                stopSelf()
            }
    }

    private fun expandToApp() {
        val expandIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        startActivity(expandIntent)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        FloatingPlayerState.isActive = false
        // Detach rather than release: this is the shared app-wide player, still very
        // possibly playing in the background, and PhonePlayerScreen (or the next
        // PlayerView that binds to it) needs it very much alive.
        playerView?.player = null
        playerView = null
        floatingContainer?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        floatingContainer = null
        windowManager = null
    }
}
