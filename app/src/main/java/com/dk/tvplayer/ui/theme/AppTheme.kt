package com.dk.tvplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.dk.tvplayer.data.local.AppThemeMode

/**
 * Builds a ColorScheme from the user's chosen theme mode + accent seed color
 * (Settings > Theme customization). AMOLED uses a pure-black surface/background
 * for OLED power savings; System follows the device's day/night setting.
 */
@Composable
fun dkColorScheme(themeMode: AppThemeMode, seedColorArgb: Long): ColorScheme {
    val seed = Color(seedColorArgb)
    val useDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK, AppThemeMode.AMOLED -> true
    }

    return if (useDark) {
        val base = darkColorScheme(
            primary = seed,
            secondary = seed.copy(alpha = 0.8f),
            tertiary = seed.copy(alpha = 0.6f)
        )
        if (themeMode == AppThemeMode.AMOLED) {
            base.copy(background = Color.Black, surface = Color.Black)
        } else {
            base
        }
    } else {
        lightColorScheme(
            primary = seed,
            secondary = seed.copy(alpha = 0.8f),
            tertiary = seed.copy(alpha = 0.6f)
        )
    }
}

/** Preset accent colors offered in the theme picker. */
val ThemeSeedPresets: List<Pair<String, Long>> = listOf(
    "Lavender" to 0xFFB39DDB,
    "Teal" to 0xFF4DB6AC,
    "Amber" to 0xFFFFB74D,
    "Rose" to 0xFFF06292,
    "Sky" to 0xFF4FC3F7,
    "Lime" to 0xFFAED581
)
