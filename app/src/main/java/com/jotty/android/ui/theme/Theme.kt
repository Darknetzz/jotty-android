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

/**
 * Theme preference: null or "system" = follow system; "light", "dark", "amoled", "sepia".
 * [themePreference] is the stored value from settings.
 */
@Composable
fun JottyTheme(
    themePreference: String? = null,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val colorScheme = when (themePreference) {
        "light" -> LightColorScheme
        "dark" -> DarkColorScheme
        "amoled" -> AmoledColorScheme
        "sepia" -> SepiaColorScheme
        else -> if (systemDark) DarkColorScheme else LightColorScheme
    }
    val useDarkStatusBar = colorScheme != LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDarkStatusBar
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
