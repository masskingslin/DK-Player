package com.dk.tvplayer.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How long list titles are shortened. START/MIDDLE keep the tail/ends of a file name visible. */
enum class TitleEllipsize(val label: String) {
    DEFAULT("Default"),
    START("Start"),
    MIDDLE("Middle"),
    END("End")
}

/** Shortens [name] to roughly [maxChars] for single-line list titles. */
fun ellipsizeTitle(name: String, mode: TitleEllipsize, maxChars: Int = 28): String {
    if (mode == TitleEllipsize.DEFAULT || mode == TitleEllipsize.END || name.length <= maxChars) return name
    return when (mode) {
        TitleEllipsize.START -> "…" + name.takeLast(maxChars - 1)
        TitleEllipsize.MIDDLE -> {
            val keep = maxChars - 1
            name.take(keep / 2 + keep % 2) + "…" + name.takeLast(keep / 2)
        }
        else -> name
    }
}

/**
 * Interface / Video preferences that aren't part of the DataStore-backed AppSettings.
 * SharedPreferences keeps them readable synchronously (e.g. while choosing the TV or phone UI).
 */
object UiPrefs {
    private const val PREFS = "dk_ui_prefs"
    private var appContext: Context? = null

    private val _titleEllipsize = MutableStateFlow(TitleEllipsize.DEFAULT)
    val titleEllipsize: StateFlow<TitleEllipsize> = _titleEllipsize.asStateFlow()

    private val _persistentIncognito = MutableStateFlow(true)
    /** Keep Incognito mode on across app restarts. */
    val persistentIncognito: StateFlow<Boolean> = _persistentIncognito.asStateFlow()

    private val _showSeenMarker = MutableStateFlow(true)
    /** Mark a local video as seen when it plays to the end, and show that marker. */
    val showSeenMarker: StateFlow<Boolean> = _showSeenMarker.asStateFlow()

    private val _forceTvInterface = MutableStateFlow(false)
    val forceTvInterface: StateFlow<Boolean> = _forceTvInterface.asStateFlow()

    private val _useCustomPipPopup = MutableStateFlow(true)
    val useCustomPipPopup: StateFlow<Boolean> = _useCustomPipPopup.asStateFlow()

    private val _restoreVideoFromBackground = MutableStateFlow(true)
    val restoreVideoFromBackground: StateFlow<Boolean> = _restoreVideoFromBackground.asStateFlow()

    private val _showMissingMedia = MutableStateFlow(true)
    /** Keep listing history items whose local file is no longer on the device. */
    val showMissingMedia: StateFlow<Boolean> = _showMissingMedia.asStateFlow()

    private val _showLastPlaylistTip = MutableStateFlow(true)
    /** On app start, offer to resume what was playing last. */
    val showLastPlaylistTip: StateFlow<Boolean> = _showLastPlaylistTip.asStateFlow()

    private val _mediaCoverOnLockscreen = MutableStateFlow(false)
    /** Replace the lock screen wallpaper with the playing media's cover (audio art or a video frame). */
    val mediaCoverOnLockscreen: StateFlow<Boolean> = _mediaCoverOnLockscreen.asStateFlow()

    private val _preferClone = MutableStateFlow(true)
    /** With a secondary display attached: true = mirror the phone, false = play video on it. */
    val preferClone: StateFlow<Boolean> = _preferClone.asStateFlow()

    private val _seekButtonsInNotification = MutableStateFlow(false)
    val seekButtonsInNotification: StateFlow<Boolean> = _seekButtonsInNotification.asStateFlow()

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val p = prefs() ?: return
        _titleEllipsize.value = runCatching { TitleEllipsize.valueOf(p.getString("ellipsize", "DEFAULT")!!) }
            .getOrDefault(TitleEllipsize.DEFAULT)
        _persistentIncognito.value = p.getBoolean("persistentIncognito", true)
        _showSeenMarker.value = p.getBoolean("seenMarker", true)
        _forceTvInterface.value = p.getBoolean("forceTv", false)
        _useCustomPipPopup.value = p.getBoolean("customPip", true)
        _restoreVideoFromBackground.value = p.getBoolean("restoreVideo", true)
        _seekButtonsInNotification.value = p.getBoolean("notifSeek", false)
        _showMissingMedia.value = p.getBoolean("showMissing", true)
        _showLastPlaylistTip.value = p.getBoolean("lastTip", true)
        _mediaCoverOnLockscreen.value = p.getBoolean("coverLockWallpaper", false)
        _preferClone.value = p.getBoolean("preferClone", true)
    }

    fun setTitleEllipsize(v: TitleEllipsize) { _titleEllipsize.value = v; putString("ellipsize", v.name) }
    fun setPersistentIncognito(v: Boolean) { _persistentIncognito.value = v; putBool("persistentIncognito", v) }
    fun setShowSeenMarker(v: Boolean) { _showSeenMarker.value = v; putBool("seenMarker", v) }
    fun setForceTvInterface(v: Boolean) { _forceTvInterface.value = v; putBool("forceTv", v) }
    fun setUseCustomPipPopup(v: Boolean) { _useCustomPipPopup.value = v; putBool("customPip", v) }
    fun setRestoreVideoFromBackground(v: Boolean) { _restoreVideoFromBackground.value = v; putBool("restoreVideo", v) }
    fun setSeekButtonsInNotification(v: Boolean) { _seekButtonsInNotification.value = v; putBool("notifSeek", v) }

    fun setShowMissingMedia(v: Boolean) { _showMissingMedia.value = v; putBool("showMissing", v) }
    fun setShowLastPlaylistTip(v: Boolean) { _showLastPlaylistTip.value = v; putBool("lastTip", v) }
    fun setMediaCoverOnLockscreen(v: Boolean) { _mediaCoverOnLockscreen.value = v; putBool("coverLockWallpaper", v) }
    fun setPreferClone(v: Boolean) { _preferClone.value = v; putBool("preferClone", v) }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun putBool(k: String, v: Boolean) { prefs()?.edit()?.putBoolean(k, v)?.apply() }
    private fun putString(k: String, v: String) { prefs()?.edit()?.putString(k, v)?.apply() }
}
