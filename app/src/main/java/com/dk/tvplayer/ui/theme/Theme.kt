package com.dk.tvplayer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode { SYSTEM, DARK, LIGHT, AMOLED }

val PurpleAccent = Color(0xFF7B61FF)
val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF121212)

@Composable
fun DkPlayerTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
    }

    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        themeMode == ThemeMode.AMOLED -> {
            darkColorScheme(
                primary = PurpleAccent,
                background = AmoledBackground,
                surface = AmoledSurface,
                surfaceVariant = Color(0xFF1E1E1E)
            )
        }
        isDark -> {
            darkColorScheme(
                primary = PurpleAccent,
                background = Color(0xFF18171C),
                surface = Color(0xFF222028),
                surfaceVariant = Color(0xFF2C2A34)
            )
        }
        else -> {
            lightColorScheme(
                primary = PurpleAccent,
                background = Color(0xFFF8F7FA),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFEBE9F0)
            )
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
