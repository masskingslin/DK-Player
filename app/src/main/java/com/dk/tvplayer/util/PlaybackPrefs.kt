package com.dk.tvplayer.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BackgroundMode(val label: String, val description: String) {
    STOP("Stop playback", "Pause the video when you switch to another app"),
    BACKGROUND("Play in background", "Keep the sound playing (audio only)"),
    PIP("Picture-in-picture", "Keep the video in a small window")
}

enum class HardwareAcceleration(val label: String, val description: String) {
    DISABLED("Disabled", "Better stability: software decoders are preferred"),
    DECODING("Decoding", "Hardware decoders; may improve performance"),
    FULL("Full", "Hardware decoders with asynchronous queueing; may improve performance further")
}

enum class VideoOrientation(val label: String, val androidValue: Int) {
    AUTO("Automatic (sensor)", 4),          // SCREEN_ORIENTATION_SENSOR
    LOCKED("Locked at start", 14),          // SCREEN_ORIENTATION_LOCKED
    LANDSCAPE("Landscape", 6),              // SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    PORTRAIT("Portrait", 7),                // SCREEN_ORIENTATION_SENSOR_PORTRAIT
    SYSTEM("Follow system setting", -1)     // SCREEN_ORIENTATION_UNSPECIFIED
}

enum class MeteredAction(val label: String) {
    NOTHING("Do nothing"),
    ASK("Ask before playing"),
    BLOCK("Don't play streams")
}

enum class ResumeAudio(val label: String) {
    ALWAYS("Always"),
    LONG("Only long audio (over 10 minutes)"),
    NEVER("Never")
}

enum class ReplayGainMode(val label: String) {
    TRACK("Track"),
    ALBUM("Album")
}

/** One persisted setting exposed as a StateFlow. */
class Pref<T>(
    private val key: String,
    default: T,
    private val read: (SharedPreferences, String, T) -> T,
    private val write: (SharedPreferences.Editor, String, T) -> Unit
) {
    private val default0 = default
    private val state = MutableStateFlow(default)
    val flow: StateFlow<T> = state.asStateFlow()
    val value: T get() = state.value

    internal fun load(p: SharedPreferences) { state.value = read(p, key, default0) }

    fun set(v: T) {
        state.value = v
        PlaybackPrefs.edit { write(it, key, v) }
    }
}

private fun boolPref(key: String, default: Boolean) =
    Pref(key, default, { p, k, d -> p.getBoolean(k, d) }, { e, k, v -> e.putBoolean(k, v) })

private fun intPref(key: String, default: Int) =
    Pref(key, default, { p, k, d -> p.getInt(k, d) }, { e, k, v -> e.putInt(k, v) })

private fun stringPref(key: String, default: String) =
    Pref(key, default, { p, k, d -> p.getString(k, d) ?: d }, { e, k, v -> e.putString(k, v) })

private inline fun <reified E : Enum<E>> enumPref(key: String, default: E) = Pref(
    key, default,
    { p, k, d -> runCatching { enumValueOf<E>(p.getString(k, d.name) ?: d.name) }.getOrDefault(d) },
    { e, k, v -> e.putString(k, v.name) }
)

private fun stringSetPref(key: String) = Pref<Set<String>>(
    key, emptySet(),
    { p, k, _ -> p.getStringSet(k, emptySet())?.toSet() ?: emptySet() },
    { e, k, v -> e.putStringSet(k, v) }
)

/**
 * Audio, video-behaviour, network, history and media-library preferences (SharedPreferences,
 * so the player can read them synchronously while it is being built).
 */
object PlaybackPrefs {
    private const val PREFS = "dk_playback_prefs"
    private var appContext: Context? = null

    // ---- Audio ----
    val resumeAfterCall = boolPref("resumeAfterCall", true)
    val stopOnSwipe = boolPref("stopOnSwipe", false)
    val digitalPassthrough = boolPref("digitalPassthrough", false)
    val preferredAudioLanguage = stringPref("audioLanguage", "")
    val resumeAudio = enumPref("resumeAudio", ResumeAudio.ALWAYS)
    val detectHeadset = boolPref("detectHeadset", true)
    val resumeOnHeadset = boolPref("resumeOnHeadset", false)
    val ignoreHeadsetButtons = boolPref("ignoreHeadsetButtons", false)

    // ---- Replay gain ----
    val replayGainEnabled = boolPref("rgEnabled", false)
    val replayGainMode = enumPref("rgMode", ReplayGainMode.TRACK)
    val replayPreampDb = intPref("rgPreamp", 89)
    val defaultReplayGainDb = intPref("rgDefault", 0)
    val peakProtection = boolPref("rgPeak", true)

    // ---- Video ----
    val backgroundMode = enumPref("backgroundMode", BackgroundMode.PIP)
    val hardwareAcceleration = enumPref("hwAccel", HardwareAcceleration.DECODING)
    val videoOrientation = enumPref("orientation", VideoOrientation.AUTO)

    // ---- Network ----
    val meteredAction = enumPref("metered", MeteredAction.NOTHING)

    // ---- History ----
    val savePlaybackHistory = boolPref("saveHistory", true)
    val videoQueueHistory = boolPref("videoQueueHistory", true)
    val audioQueueHistory = boolPref("audioQueueHistory", true)

    // ---- Media library ----
    val autoRescan = boolPref("autoRescan", true)
    val excludedFolders = stringSetPref("excludedFolders")

    private val all: List<Pref<*>> = listOf(
        resumeAfterCall, stopOnSwipe, digitalPassthrough, preferredAudioLanguage, resumeAudio,
        detectHeadset, resumeOnHeadset, ignoreHeadsetButtons,
        replayGainEnabled, replayGainMode, replayPreampDb, defaultReplayGainDb, peakProtection,
        backgroundMode, hardwareAcceleration, videoOrientation, meteredAction,
        savePlaybackHistory, videoQueueHistory, audioQueueHistory, autoRescan, excludedFolders
    )

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val p = prefs() ?: return
        all.forEach { it.load(p) }
    }

    internal fun edit(block: (SharedPreferences.Editor) -> Unit) {
        val e = prefs()?.edit() ?: return
        block(e)
        e.apply()
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
