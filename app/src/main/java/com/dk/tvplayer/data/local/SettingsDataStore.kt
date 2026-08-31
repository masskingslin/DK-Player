package com.dk.tvplayer.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

data class AppSettings(
    val hwAcceleration: Boolean = true,
    val backgroundAudioPlayback: Boolean = false,
    val autoResumePlayback: Boolean = true,
    val sortOption: SortOption = SortOption.NAME_ASC,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val themeSeedColor: Long = 0xFFB39DDB, // soft lavender, matches existing dark UI
    val defaultPlaybackSpeed: Float = 1.0f
)

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
            defaultPlaybackSpeed = prefs[Keys.PLAYBACK_SPEED]?.toFloatOrNull() ?: 1.0f
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
        }
    }
}
