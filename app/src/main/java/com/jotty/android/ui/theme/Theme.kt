package com.jotty.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Indigo = Color(0xFF6366F1)
private val IndigoDark = Color(0xFF818CF8)
private val Slate900 = Color(0xFF0F172A)
private val Slate800 = Color(0xFF1E293B)
private val Slate100 = Color(0xFFF1F5F9)
private val Slate200 = Color(0xFFE2E8F0)

// Sepia / warm reading theme
private val SepiaBackground = Color(0xFFF4ECD8)
private val SepiaSurface = Color(0xFFFDF6E9)
private val SepiaOnBackground = Color(0xFF3E3A32)
private val SepiaOnSurface = Color(0xFF2D2A24)

private val DarkColorScheme = darkColorScheme(
    primary = IndigoDark,
    onPrimary = Color.White,
    secondary = Indigo,
    onSecondary = Color.White,
    background = Slate900,
    surface = Slate800,
    onBackground = Color.White,
    onSurface = Color.White,
)

/** True black dark scheme for OLED; same accents as dark theme. */
private val AmoledColorScheme = darkColorScheme(
    primary = IndigoDark,
    onPrimary = Color.White,
    secondary = Indigo,
    onSecondary = Color.White,
    background = Color.Black,
    surface = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    secondary = Indigo,
    onSecondary = Color.White,
    background = Slate100,
    surface = Color.White,
    onBackground = Slate900,
    onSurface = Slate800,
)

/** Warm sepia light scheme; same primary, softer background for reading. */
private val SepiaColorScheme = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    secondary = Indigo,
    onSecondary = Color.White,
    background = SepiaBackground,
    surface = SepiaSurface,
    onBackground = SepiaOnBackground,
    onSurface = SepiaOnSurface,
)

// Midnight — dark blue/navy theme
private val MidnightBlue = Color(0xFF1E3A5F)
private val MidnightBlueLight = Color(0xFF2E5077)
private val MidnightBg = Color(0xFF0D1929)
private val MidnightSurface = Color(0xFF132F4C)

private val MidnightColorScheme = darkColorScheme(
    primary = MidnightBlueLight,
    onPrimary = Color.White,
    secondary = MidnightBlue,
    onSecondary = Color.White,
    background = MidnightBg,
    surface = MidnightSurface,
    onBackground = Color.White,
    onSurface = Color.White,
)

// Rose — warm pink/rose light theme
private val RosePrimary = Color(0xFFB91C3C)
private val RoseBg = Color(0xFFFDF2F4)
private val RoseSurface = Color(0xFFFFF5F7)
private val RoseOnBg = Color(0xFF3F1419)
private val RoseOnSurface = Color(0xFF2D0F12)

private val RoseColorScheme = lightColorScheme(
    primary = RosePrimary,
    onPrimary = Color.White,
    secondary = RosePrimary,
    onSecondary = Color.White,
    background = RoseBg,
    surface = RoseSurface,
    onBackground = RoseOnBg,
    onSurface = RoseOnSurface,
)

// Ocean — cool blue-tinted light theme
private val OceanPrimary = Color(0xFF0E7490)
private val OceanBg = Color(0xFFF0F9FF)
private val OceanSurface = Color(0xFFE0F2FE)
private val OceanOnBg = Color(0xFF0C4A6E)
private val OceanOnSurface = Color(0xFF0A3D52)

private val OceanColorScheme = lightColorScheme(
    primary = OceanPrimary,
    onPrimary = Color.White,
    secondary = OceanPrimary,
    onSecondary = Color.White,
    background = OceanBg,
    surface = OceanSurface,
    onBackground = OceanOnBg,
    onSurface = OceanOnSurface,
)

// Forest — green-tinted light theme
private val ForestPrimary = Color(0xFF166534)
private val ForestBg = Color(0xFFF0FDF4)
private val ForestSurface = Color(0xFFDCFCE7)
private val ForestOnBg = Color(0xFF14532D)
private val ForestOnSurface = Color(0xFF052E16)

private val ForestColorScheme = lightColorScheme(
    primary = ForestPrimary,
    onPrimary = Color.White,
    secondary = ForestPrimary,
    onSecondary = Color.White,
    background = ForestBg,
    surface = ForestSurface,
    onBackground = ForestOnBg,
    onSurface = ForestOnSurface,
)

// Dark variants for color palettes (same primary, dark backgrounds)
private val RoseDarkBg = Color(0xFF1C0A0E)
private val RoseDarkSurface = Color(0xFF2D1519)
private val RoseDarkColorScheme = darkColorScheme(
    primary = Color(0xFFF87191),
    onPrimary = Color.White,
    secondary = RosePrimary,
    onSecondary = Color.White,
    background = RoseDarkBg,
    surface = RoseDarkSurface,
    onBackground = Color.White,
    onSurface = Color.White,
)

private val OceanDarkBg = Color(0xFF082F49)
private val OceanDarkSurface = Color(0xFF0C4A6E)
private val OceanDarkColorScheme = darkColorScheme(
    primary = Color(0xFF22D3EE),
    onPrimary = Color(0xFF082F49),
    secondary = OceanPrimary,
    onSecondary = Color.White,
    background = OceanDarkBg,
    surface = OceanDarkSurface,
    onBackground = Color.White,
    onSurface = Color.White,
)

private val ForestDarkBg = Color(0xFF052E16)
private val ForestDarkSurface = Color(0xFF14532D)
private val ForestDarkColorScheme = darkColorScheme(
    primary = Color(0xFF4ADE80),
    onPrimary = Color(0xFF052E16),
    secondary = ForestPrimary,
    onSecondary = Color.White,
    background = ForestDarkBg,
    surface = ForestDarkSurface,
    onBackground = Color.White,
    onSurface = Color.White,
)

/**
 * Resolves [themeMode] (null/"system", "light", "dark") and [themeColor] ("default", "amoled", etc.)
 * to a ColorScheme. E.g. Dark + Forest → Forest dark variant.
 */
@Composable
fun JottyTheme(
    themeMode: String? = null,
    themeColor: String = "default",
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    val colorScheme = when (themeColor) {
        "amoled" -> if (dark) AmoledColorScheme else LightColorScheme
        "sepia" -> if (dark) DarkColorScheme else SepiaColorScheme
        "midnight" -> if (dark) MidnightColorScheme else OceanColorScheme
        "rose" -> if (dark) RoseDarkColorScheme else RoseColorScheme
        "ocean" -> if (dark) OceanDarkColorScheme else OceanColorScheme
        "forest" -> if (dark) ForestDarkColorScheme else ForestColorScheme
        else -> if (dark) DarkColorScheme else LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
