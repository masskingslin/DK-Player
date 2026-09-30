package com.dk.tvplayer.util

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.dk.tvplayer.MainActivity
import com.dk.tvplayer.R
import com.dk.tvplayer.data.local.LocalVideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val EXTRA_PLAY_PATH = "dk_play_path"
const val EXTRA_PLAY_TITLE = "dk_play_title"

/** Favourite local videos, kept in SharedPreferences (no database migration needed). */
object LocalVideoFavorites {
    private const val PREFS = "dk_local_video_favorites"
    private const val KEY = "paths"

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        _favorites.value = prefs(context).getStringSet(KEY, emptySet())?.toSet() ?: emptySet()
    }

    /** Returns true if [path] is now a favourite. */
    fun toggle(context: Context, path: String): Boolean {
        load(context)
        val current = _favorites.value
        val updated = if (path in current) current - path else current + path
        _favorites.value = updated
        prefs(context).edit().putStringSet(KEY, updated).apply()
        return path in updated
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

enum class RingtoneResult { SET, NEEDS_PERMISSION, FAILED }

object LocalVideoActions {

    /** Opens a subtitle search for the video's file name in the browser. */
    fun searchSubtitles(context: Context, video: LocalVideoItem) {
        val query = video.name.substringBeforeLast('.').replace('_', ' ').replace('.', ' ')
        val url = "https://www.opensubtitles.org/en/search/sublanguageid-all/moviename-${Uri.encode(query)}"
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Needs the "Modify system settings" special permission; sends the user to grant it
     *  first if missing (they then tap the action again). */
    fun setAsRingtone(context: Context, video: LocalVideoItem): RingtoneResult {
        if (!Settings.System.canWrite(context)) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return RingtoneResult.NEEDS_PERMISSION
        }
        return runCatching {
            val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video.id)
            RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, uri)
            RingtoneResult.SET
        }.getOrDefault(RingtoneResult.FAILED)
    }

    /** Asks the launcher to pin a home-screen shortcut that opens this video directly. */
    fun pinShortcut(context: Context, video: LocalVideoItem): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_PLAY_PATH, video.filePath)
            .putExtra(EXTRA_PLAY_TITLE, video.name)
        val info = ShortcutInfoCompat.Builder(context, "video_${video.id}")
            .setShortLabel(video.name.take(24))
            .setLongLabel(video.name)
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }
}
