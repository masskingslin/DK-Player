package com.dk.tvplayer.util

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Executors

/**
 * "Media cover on Lockscreen": while media plays, the lock screen wallpaper becomes that
 * media's cover — the embedded cover art of an audio file, or a frame from a video — and the
 * original wallpaper is put back when playback stops.
 *
 * Android only lets an app *replace* the lock wallpaper, and on recent versions apps can
 * rarely read the current one back. So the original is backed up when readable; otherwise
 * "restore" clears the lock wallpaper, which makes the lock screen follow the home wallpaper
 * again. A separate, custom lock wallpaper (or a live wallpaper) can therefore not always
 * be recovered — which is why the option is off by default and explained when enabled.
 */
object LockscreenCover {
    private const val PREFS = "dk_lockscreen_cover"
    private const val RESTORE_DELAY_MS = 2500L

    private var appContext: Context? = null
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var generation = 0
    @Volatile private var lastAppliedPath: String? = null
    private val restoreRunnable = Runnable { restoreNow() }

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        // If the app was killed while a cover was on the lock screen, put things back now.
        if (prefs()?.getBoolean("active", false) == true) restoreNow()
    }

    /** Called whenever a new item starts playing. */
    fun update(mediaPath: String?) {
        val ctx = appContext ?: return
        if (mediaPath == null || !UiPrefs.mediaCoverOnLockscreen.value) return
        if (!mediaPath.startsWith("/")) return // only local files have a cover we can read quickly
        main.removeCallbacks(restoreRunnable)
        if (mediaPath == lastAppliedPath && prefs()?.getBoolean("active", false) == true) return
        val gen = ++generation
        worker.execute {
            runCatching {
                val cover = extractCover(mediaPath) ?: return@runCatching
                if (gen != generation) return@runCatching // a newer item took over
                val wm = WallpaperManager.getInstance(ctx)
                if (prefs()?.getBoolean("active", false) != true) backupOriginal(ctx, wm)
                val metrics = ctx.resources.displayMetrics
                val w = wm.desiredMinimumWidth.takeIf { it > 0 } ?: metrics.widthPixels
                val h = wm.desiredMinimumHeight.takeIf { it > 0 } ?: metrics.heightPixels
                val composed = compose(cover, w, h)
                wm.setBitmap(composed, null, false, WallpaperManager.FLAG_LOCK)
                prefs()?.edit()?.putBoolean("active", true)?.apply()
                lastAppliedPath = mediaPath
                composed.recycle()
                cover.recycle()
            }
        }
    }

    /** Playback stopped or ended: restore shortly after, unless something else starts first. */
    fun scheduleRestore() {
        if (prefs()?.getBoolean("active", false) != true) return
        main.removeCallbacks(restoreRunnable)
        main.postDelayed(restoreRunnable, RESTORE_DELAY_MS)
    }

    /** Playback is active again (buffering/ready): a pending restore must not fire. */
    fun cancelRestore() {
        main.removeCallbacks(restoreRunnable)
    }

    /** Puts the original lock wallpaper back right away (e.g. when the option is turned off). */
    fun restoreNow() {
        val ctx = appContext ?: return
        main.removeCallbacks(restoreRunnable)
        generation++
        lastAppliedPath = null
        worker.execute {
            runCatching {
                if (prefs()?.getBoolean("active", false) != true) return@runCatching
                val wm = WallpaperManager.getInstance(ctx)
                val backup = backupFile(ctx)
                if (backup.exists() && backup.length() > 0) {
                    FileInputStream(backup).use { wm.setStream(it, null, false, WallpaperManager.FLAG_LOCK) }
                } else {
                    wm.clear(WallpaperManager.FLAG_LOCK)
                }
                backup.delete()
            }
            prefs()?.edit()?.putBoolean("active", false)?.apply()
        }
    }

    // ---------- internals ----------

    private fun backupFile(ctx: Context) = File(ctx.filesDir, "lockscreen_backup.img")

    private fun backupOriginal(ctx: Context, wm: WallpaperManager) {
        val backup = backupFile(ctx)
        backup.delete()
        runCatching {
            wm.getWallpaperFile(WallpaperManager.FLAG_LOCK)?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { input ->
                    backup.outputStream().use { input.copyTo(it) }
                }
            }
        }
        if (backup.exists() && backup.length() == 0L) backup.delete()
    }

    private val audioExtensions = setOf("mp3", "m4a", "flac", "ogg", "opus", "aac", "wav", "wma")

    private fun extractCover(path: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val embedded = retriever.embeddedPicture
            if (embedded != null) {
                BitmapFactory.decodeByteArray(embedded, 0, embedded.size)
            } else if (path.substringAfterLast('.', "").lowercase() in audioExtensions) {
                null
            } else {
                // A video: use a frame about a tenth of the way in (the very first frame is often black).
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                val atUs = (durationMs / 10).coerceIn(1_000L, 60_000L) * 1000
                retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(-1)
            }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Dimmed, blurred copy of the cover as backdrop, with the sharp cover centred below the clock. */
    private fun compose(cover: Bitmap, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        val tinyW = 24
        val tinyH = (tinyW.toFloat() * h / w).toInt().coerceAtLeast(8)
        val tiny = Bitmap.createScaledBitmap(cover, tinyW, tinyH, true)
        canvas.drawBitmap(tiny, null, Rect(0, 0, w, h), paint)
        tiny.recycle()
        canvas.drawColor(0x99000000.toInt())

        val scale = minOf(w.toFloat() / cover.width, h * 0.5f / cover.height)
        val fw = (cover.width * scale).toInt()
        val fh = (cover.height * scale).toInt()
        val left = (w - fw) / 2
        val top = (h * 0.58f - fh / 2f).toInt()
        canvas.drawBitmap(cover, null, Rect(left, top, left + fw, top + fh), paint)
        return out
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
