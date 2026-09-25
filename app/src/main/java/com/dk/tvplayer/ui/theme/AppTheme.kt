package com.dk.tvplayer.ui.theme

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk.tvplayer.data.local.AppThemeMode

/**
 * Consistent corner-radius scale for cards, dialogs, buttons and chips so the whole
 * app reads as one cohesive shape language instead of mixed ad-hoc RoundedCornerShape
 * values scattered across individual screens.
 */
object DkShapes {
    val extraSmall = RoundedCornerShape(6.dp)
    val small = RoundedCornerShape(10.dp)
    val medium = RoundedCornerShape(16.dp)
    val large = RoundedCornerShape(22.dp)
    val extraLarge = RoundedCornerShape(28.dp)
    val pill = RoundedCornerShape(50)
}

/** 8dp-baseline spacing scale shared across screens. */
object DkSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

/** Material3 [Shapes] built from the [DkShapes] scale, passed into the app's MaterialTheme. */
val dkShapes = Shapes(
    extraSmall = DkShapes.extraSmall,
    small = DkShapes.small,
    medium = DkShapes.medium,
    large = DkShapes.large,
    extraLarge = DkShapes.extraLarge
)

/**
 * Typography tuned for a media app: slightly heavier, tighter-tracked headline/title
 * styles for channel names and screen titles, a touch more line-height on body text
 * for the longer descriptions in EPG/settings, and bolder labels for buttons/chips.
 */
val dkTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(lineHeight = 22.sp),
        bodyMedium = base.bodyMedium.copy(lineHeight = 20.sp)
    )
}

/**
 * Shifts a color's hue/saturation/value in HSV space and returns a fully opaque
 * [Color]. The previous approach derived "lighter/darker" accents by lowering alpha
 * (e.g. `seed.copy(alpha = 0.8f)`), which makes a color's on-screen appearance depend
 * on whatever happens to be drawn behind it — the same "secondary" swatch renders
 * differently over the video player's black background than over a light card. Doing
 * the shift in HSV space instead keeps every generated tone fully opaque and
 * predictable everywhere it's used, and gives secondary/tertiary genuinely distinct
 * hues instead of a washed-out copy of primary.
 */
private fun shift(
    color: Color,
    hueDelta: Float = 0f,
    saturationScale: Float = 1f,
    valueScale: Float = 1f
): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(color.toArgb(), hsv)
    hsv[0] = ((hsv[0] + hueDelta) % 360f).let { if (it < 0f) it + 360f else it }
    hsv[1] = (hsv[1] * saturationScale).coerceIn(0f, 1f)
    hsv[2] = (hsv[2] * valueScale).coerceIn(0.05f, 1f)
    return Color(AndroidColor.HSVToColor(hsv))
}

/**
 * Builds a full ColorScheme from the user's chosen theme mode + accent seed color
 * (Settings > Theme customization). Secondary/tertiary are hue-shifted analogous
 * accents rather than alpha-faded copies of primary, so multi-color UI (progress
 * bars, chips, icon containers) reads as an intentional palette. AMOLED uses a
 * pure-black surface/background for OLED power savings; System follows the
 * device's day/night setting.
 */
@Composable
fun dkColorScheme(themeMode: AppThemeMode, seedColorArgb: Long): ColorScheme {
    val seed = Color(seedColorArgb)
    val useDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK, AppThemeMode.AMOLED -> true
    }

    val base = if (useDark) {
        darkColorScheme(
            primary = shift(seed, valueScale = 1.12f, saturationScale = 0.95f),
            onPrimary = Color.Black,
            primaryContainer = shift(seed, valueScale = 0.5f, saturationScale = 0.75f),
            secondary = shift(seed, hueDelta = -20f, saturationScale = 0.5f, valueScale = 0.85f),
            secondaryContainer = shift(seed, hueDelta = -20f, saturationScale = 0.35f, valueScale = 0.4f),
            tertiary = shift(seed, hueDelta = 32f, saturationScale = 0.55f, valueScale = 0.95f),
            tertiaryContainer = shift(seed, hueDelta = 32f, saturationScale = 0.4f, valueScale = 0.45f)
        )
    } else {
        lightColorScheme(
            primary = shift(seed, saturationScale = 1.05f, valueScale = 0.85f),
            primaryContainer = shift(seed, saturationScale = 0.25f, valueScale = 1f),
            secondary = shift(seed, hueDelta = -20f, saturationScale = 0.55f, valueScale = 0.8f),
            secondaryContainer = shift(seed, hueDelta = -20f, saturationScale = 0.2f, valueScale = 1f),
            tertiary = shift(seed, hueDelta = 32f, saturationScale = 0.6f, valueScale = 0.85f),
            tertiaryContainer = shift(seed, hueDelta = 32f, saturationScale = 0.25f, valueScale = 1f)
        )
    }

    return if (useDark && themeMode == AppThemeMode.AMOLED) {
        base.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF141414)
        )
    } else {
        base
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
