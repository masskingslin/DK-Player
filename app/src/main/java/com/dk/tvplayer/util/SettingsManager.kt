package com.dk.tvplayer.util

import android.content.Context
import android.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

@Serializable
data class ThemeConfig(
    val primaryColor: String = "#FF6200EE",
    val secondaryColor: String = "#FF03DAC6",
    val tertiaryColor: String = "#FF018786",
    val backgroundColor: String = "#FF121212",
    val surfaceColor: String = "#FF1F1F1F",
    val errorColor: String = "#FFCF6679",
    val isDarkMode: Boolean = true
)

@Serializable
data class PlaybackSettings(
    val defaultPlaybackSpeed: Float = 1.0f,
    val autoResume: Boolean = true,
    val rememberPlaybackPosition: Boolean = true,
    val defaultSubtitleLanguage: String? = null,
    val alwaysShowControls: Boolean = false,
    val controlsAutoHideDelay: Long = 3000
)

@Serializable
data class UISettings(
    val fontSize: Float = 1.0f,
    val channelGridColumns: Int = 3,
    val showThumbnails: Boolean = true,
    val animationsEnabled: Boolean = true,
    val themeConfig: ThemeConfig = ThemeConfig()
)

@Serializable
data class AppSettings(
    val playbackSettings: PlaybackSettings = PlaybackSettings(),
    val uiSettings: UISettings = UISettings(),
    val versionCode: Int = 1
)

class SettingsManager(private val context: Context) {
    private val dataStore = context.settingsDataStore

    suspend fun saveThemeConfig(theme: ThemeConfig) {
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("theme_config")] = Json.encodeToString(ThemeConfig.serializer(), theme)
        }
    }

    fun getThemeConfig(): Flow<ThemeConfig> = dataStore.data.map { preferences ->
        val json = preferences[stringPreferencesKey("theme_config")] ?: return@map ThemeConfig()
        try {
            Json.decodeFromString(ThemeConfig.serializer(), json)
        } catch (e: Exception) {
            ThemeConfig()
        }
    }

    suspend fun savePlaybackSettings(settings: PlaybackSettings) {
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("playback_settings")] = Json.encodeToString(PlaybackSettings.serializer(), settings)
        }
    }

    fun getPlaybackSettings(): Flow<PlaybackSettings> = dataStore.data.map { preferences ->
        val json = preferences[stringPreferencesKey("playback_settings")] ?: return@map PlaybackSettings()
        try {
            Json.decodeFromString(PlaybackSettings.serializer(), json)
        } catch (e: Exception) {
            PlaybackSettings()
        }
    }

    suspend fun saveUISettings(settings: UISettings) {
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("ui_settings")] = Json.encodeToString(UISettings.serializer(), settings)
        }
    }

    fun getUISettings(): Flow<UISettings> = dataStore.data.map { preferences ->
        val json = preferences[stringPreferencesKey("ui_settings")] ?: return@map UISettings()
        try {
            Json.decodeFromString(UISettings.serializer(), json)
        } catch (e: Exception) {
            UISettings()
        }
    }

    fun getAppSettings(): Flow<AppSettings> = dataStore.data.map { preferences ->
        val playbackSettingsJson = preferences[stringPreferencesKey("playback_settings")] ?: ""
        val uiSettingsJson = preferences[stringPreferencesKey("ui_settings")] ?: ""
        
        val playbackSettings = if (playbackSettingsJson.isNotEmpty()) {
            try {
                Json.decodeFromString(PlaybackSettings.serializer(), playbackSettingsJson)
            } catch (e: Exception) {
                PlaybackSettings()
            }
        } else PlaybackSettings()

        val uiSettings = if (uiSettingsJson.isNotEmpty()) {
            try {
                Json.decodeFromString(UISettings.serializer(), uiSettingsJson)
            } catch (e: Exception) {
                UISettings()
            }
        } else UISettings()

        AppSettings(
            playbackSettings = playbackSettings,
            uiSettings = uiSettings
        )
    }

    suspend fun resetToDefaults() {
        dataStore.edit { preferences ->
            preferences.clear()
            preferences[stringPreferencesKey("theme_config")] = Json.encodeToString(ThemeConfig.serializer(), ThemeConfig())
            preferences[stringPreferencesKey("playback_settings")] = Json.encodeToString(PlaybackSettings.serializer(), PlaybackSettings())
            preferences[stringPreferencesKey("ui_settings")] = Json.encodeToString(UISettings.serializer(), UISettings())
        }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        dataStore.edit { preferences ->
            preferences[floatPreferencesKey("playback_speed")] = speed
        }
    }

    fun getPlaybackSpeed(): Flow<Float> = dataStore.data.map { preferences ->
        preferences[floatPreferencesKey("playback_speed")] ?: 1.0f
    }

    suspend fun setGridColumns(columns: Int) {
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("grid_columns")] = columns
        }
    }

    fun getGridColumns(): Flow<Int> = dataStore.data.map { preferences ->
        preferences[intPreferencesKey("grid_columns")] ?: 3
    }

    suspend fun toggleAnimations(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[booleanPreferencesKey("animations_enabled")] = enabled
        }
    }

    fun getAnimationsEnabled(): Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[booleanPreferencesKey("animations_enabled")] ?: true
    }
}
