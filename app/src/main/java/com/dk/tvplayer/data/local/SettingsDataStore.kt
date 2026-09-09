package com.dk.tvplayer.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "dk_player_settings")

enum class SortOption(val label: String) {
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    RECENTLY_ADDED("Recently Added"),
    FAVORITES_FIRST("Favorites First")
}

enum class AppThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

/**
 * Per-app language options. The empty tag for SYSTEM means "follow the device
 * language" — passed to AppCompatDelegate.setApplicationLocales() as an empty
 * LocaleListCompat, which clears any app-specific override.
 *
 * Note: this switches the *system-level* locale used for date/number formatting and
 * any string resources that exist. Most of this app's UI text is currently hardcoded
 * English directly in Compose code rather than pulled from strings.xml, so changing
 * the language here won't retranslate that text — that would need a separate pass to
 * extract every UI string into per-locale string resources.
 */
enum class AppLanguage(val label: String, val localeTag: String) {
    SYSTEM_DEFAULT("System Default", ""),
    ENGLISH("English", "en"),
    SPANISH("Español", "es"),
    FRENCH("Français", "fr"),
    GERMAN("Deutsch", "de"),
    PORTUGUESE("Português", "pt"),
    ITALIAN("Italiano", "it"),
    ARABIC("العربية", "ar"),
    HINDI("हिन्दी", "hi"),
    INDONESIAN("Bahasa Indonesia", "in")
}

/** Caps decoded/selected video track resolution. Null width/height means no cap. */
enum class VideoResolutionCap(val label: String, val width: Int, val height: Int) {
    BEST_AVAILABLE("Best available", Int.MAX_VALUE, Int.MAX_VALUE),
    R_1080P("1080p", 1920, 1080),
    R_720P("720p", 1280, 720),
    R_480P("480p", 854, 480)
}

data class AppSettings(
    val hwAcceleration: Boolean = true,
    val backgroundAudioPlayback: Boolean = false,
    val autoResumePlayback: Boolean = true,
    val sortOption: SortOption = SortOption.NAME_ASC,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val themeSeedColor: Long = 0xFFB39DDB, // soft lavender, matches existing dark UI
    val defaultPlaybackSpeed: Float = 1.0f,
    // Fast seek trades exact-frame accuracy for much quicker seeking by snapping to the
    // nearest keyframe instead of decoding to the exact requested position.
    val fastSeekEnabled: Boolean = false,
    // When enabled, the app tries to switch the display's refresh rate to match the
    // currently playing video's frame rate (e.g. 24p film on a 24Hz-capable display).
    val matchDisplayFrameRate: Boolean = false,
    val maxVideoResolution: VideoResolutionCap = VideoResolutionCap.BEST_AVAILABLE,
    val videoThumbnailsEnabled: Boolean = true,
    // When enabled, playback history/resume positions are never read or written.
    val incognitoMode: Boolean = false,
    val subtitleTextSize: SubtitleTextSize = SubtitleTextSize.MEDIUM,
    val subtitleColor: SubtitleColorPreset = SubtitleColorPreset.WHITE,
    val showListHeaders: Boolean = true,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM_DEFAULT
)

enum class SubtitleTextSize(val label: String, val sp: Float) {
    SMALL("Small", 14f),
    MEDIUM("Medium", 18f),
    LARGE("Large", 24f),
    EXTRA_LARGE("Extra Large", 30f)
}

enum class SubtitleColorPreset(val label: String, val colorArgb: Int) {
    WHITE("White", 0xFFFFFFFF.toInt()),
    YELLOW("Yellow", 0xFFFFEB3B.toInt()),
    CYAN("Cyan", 0xFF18FFFF.toInt()),
    GREEN("Green", 0xFF69F0AE.toInt())
}

/**
 * Central persisted-settings store. Backs both the Settings screen toggles/theme
 * picker and the Export/Import settings feature.
 */
class SettingsDataStore(private val context: Context) {

    private object Keys {
        val HW_ACCEL = booleanPreferencesKey("hw_acceleration")
        val BACKGROUND_AUDIO = booleanPreferencesKey("background_audio_playback")
        val AUTO_RESUME = booleanPreferencesKey("auto_resume_playback")
        val SORT_OPTION = stringPreferencesKey("sort_option")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_SEED = stringPreferencesKey("theme_seed_color")
        val PLAYBACK_SPEED = stringPreferencesKey("default_playback_speed")
        val FAST_SEEK = booleanPreferencesKey("fast_seek_enabled")
        val MATCH_DISPLAY_FRAME_RATE = booleanPreferencesKey("match_display_frame_rate")
        val MAX_VIDEO_RESOLUTION = stringPreferencesKey("max_video_resolution")
        val VIDEO_THUMBNAILS = booleanPreferencesKey("video_thumbnails_enabled")
        val INCOGNITO_MODE = booleanPreferencesKey("incognito_mode")
        val SUBTITLE_TEXT_SIZE = stringPreferencesKey("subtitle_text_size")
        val SUBTITLE_COLOR = stringPreferencesKey("subtitle_color")
        val SHOW_LIST_HEADERS = booleanPreferencesKey("show_list_headers")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            hwAcceleration = prefs[Keys.HW_ACCEL] ?: true,
            backgroundAudioPlayback = prefs[Keys.BACKGROUND_AUDIO] ?: false,
            autoResumePlayback = prefs[Keys.AUTO_RESUME] ?: true,
            sortOption = prefs[Keys.SORT_OPTION]?.let { runCatching { SortOption.valueOf(it) }.getOrNull() }
                ?: SortOption.NAME_ASC,
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                ?: AppThemeMode.DARK,
            themeSeedColor = prefs[Keys.THEME_SEED]?.toLongOrNull() ?: 0xFFB39DDB,
            defaultPlaybackSpeed = prefs[Keys.PLAYBACK_SPEED]?.toFloatOrNull() ?: 1.0f,
            fastSeekEnabled = prefs[Keys.FAST_SEEK] ?: false,
            matchDisplayFrameRate = prefs[Keys.MATCH_DISPLAY_FRAME_RATE] ?: false,
            maxVideoResolution = prefs[Keys.MAX_VIDEO_RESOLUTION]
                ?.let { runCatching { VideoResolutionCap.valueOf(it) }.getOrNull() }
                ?: VideoResolutionCap.BEST_AVAILABLE,
            videoThumbnailsEnabled = prefs[Keys.VIDEO_THUMBNAILS] ?: true,
            incognitoMode = prefs[Keys.INCOGNITO_MODE] ?: false,
            subtitleTextSize = prefs[Keys.SUBTITLE_TEXT_SIZE]
                ?.let { runCatching { SubtitleTextSize.valueOf(it) }.getOrNull() }
                ?: SubtitleTextSize.MEDIUM,
            subtitleColor = prefs[Keys.SUBTITLE_COLOR]
                ?.let { runCatching { SubtitleColorPreset.valueOf(it) }.getOrNull() }
                ?: SubtitleColorPreset.WHITE,
            showListHeaders = prefs[Keys.SHOW_LIST_HEADERS] ?: true,
            appLanguage = prefs[Keys.APP_LANGUAGE]
                ?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: AppLanguage.SYSTEM_DEFAULT
        )
    }

    suspend fun setHwAcceleration(value: Boolean) = context.dataStore.edit { it[Keys.HW_ACCEL] = value }
    suspend fun setBackgroundAudio(value: Boolean) = context.dataStore.edit { it[Keys.BACKGROUND_AUDIO] = value }
    suspend fun setAutoResume(value: Boolean) = context.dataStore.edit { it[Keys.AUTO_RESUME] = value }
    suspend fun setSortOption(value: SortOption) = context.dataStore.edit { it[Keys.SORT_OPTION] = value.name }
    suspend fun setThemeMode(value: AppThemeMode) = context.dataStore.edit { it[Keys.THEME_MODE] = value.name }
    suspend fun setThemeSeedColor(value: Long) = context.dataStore.edit { it[Keys.THEME_SEED] = value.toString() }
    suspend fun setDefaultPlaybackSpeed(value: Float) =
        context.dataStore.edit { it[Keys.PLAYBACK_SPEED] = value.toString() }
    suspend fun setFastSeekEnabled(value: Boolean) = context.dataStore.edit { it[Keys.FAST_SEEK] = value }
    suspend fun setMatchDisplayFrameRate(value: Boolean) =
        context.dataStore.edit { it[Keys.MATCH_DISPLAY_FRAME_RATE] = value }
    suspend fun setMaxVideoResolution(value: VideoResolutionCap) =
        context.dataStore.edit { it[Keys.MAX_VIDEO_RESOLUTION] = value.name }
    suspend fun setVideoThumbnailsEnabled(value: Boolean) =
        context.dataStore.edit { it[Keys.VIDEO_THUMBNAILS] = value }
    suspend fun setIncognitoMode(value: Boolean) = context.dataStore.edit { it[Keys.INCOGNITO_MODE] = value }
    suspend fun setSubtitleTextSize(value: SubtitleTextSize) =
        context.dataStore.edit { it[Keys.SUBTITLE_TEXT_SIZE] = value.name }
    suspend fun setSubtitleColor(value: SubtitleColorPreset) =
        context.dataStore.edit { it[Keys.SUBTITLE_COLOR] = value.name }
    suspend fun setShowListHeaders(value: Boolean) =
        context.dataStore.edit { it[Keys.SHOW_LIST_HEADERS] = value }
    suspend fun setAppLanguage(value: AppLanguage) =
        context.dataStore.edit { it[Keys.APP_LANGUAGE] = value.name }

    /** Bulk apply — used when importing a settings backup file. */
    suspend fun applyAll(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HW_ACCEL] = settings.hwAcceleration
            prefs[Keys.BACKGROUND_AUDIO] = settings.backgroundAudioPlayback
            prefs[Keys.AUTO_RESUME] = settings.autoResumePlayback
            prefs[Keys.SORT_OPTION] = settings.sortOption.name
            prefs[Keys.THEME_MODE] = settings.themeMode.name
            prefs[Keys.THEME_SEED] = settings.themeSeedColor.toString()
            prefs[Keys.PLAYBACK_SPEED] = settings.defaultPlaybackSpeed.toString()
            prefs[Keys.FAST_SEEK] = settings.fastSeekEnabled
            prefs[Keys.MATCH_DISPLAY_FRAME_RATE] = settings.matchDisplayFrameRate
            prefs[Keys.MAX_VIDEO_RESOLUTION] = settings.maxVideoResolution.name
            prefs[Keys.VIDEO_THUMBNAILS] = settings.videoThumbnailsEnabled
            prefs[Keys.INCOGNITO_MODE] = settings.incognitoMode
            prefs[Keys.SUBTITLE_TEXT_SIZE] = settings.subtitleTextSize.name
            prefs[Keys.SUBTITLE_COLOR] = settings.subtitleColor.name
            prefs[Keys.SHOW_LIST_HEADERS] = settings.showListHeaders
            prefs[Keys.APP_LANGUAGE] = settings.appLanguage.name
        }
    }
}
