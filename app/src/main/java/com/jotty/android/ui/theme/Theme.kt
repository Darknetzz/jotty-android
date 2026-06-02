package com.jotty.android.ui.theme

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
import androidx.compose.ui.platform.LocalContext
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

private val DarkColorScheme =
    darkColorScheme(
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
private val AmoledColorScheme =
    darkColorScheme(
        primary = IndigoDark,
        onPrimary = Color.White,
        secondary = Indigo,
        onSecondary = Color.White,
        background = Color.Black,
        surface = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White,
    )

private val LightColorScheme =
    lightColorScheme(
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
private val SepiaColorScheme =
    lightColorScheme(
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

private val MidnightColorScheme =
    darkColorScheme(
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

private val RoseColorScheme =
    lightColorScheme(
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

private val OceanColorScheme =
    lightColorScheme(
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

private val ForestColorScheme =
    lightColorScheme(
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
private val RoseDarkColorScheme =
    darkColorScheme(
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
private val OceanDarkColorScheme =
    darkColorScheme(
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
private val ForestDarkColorScheme =
    darkColorScheme(
        primary = Color(0xFF4ADE80),
        onPrimary = Color(0xFF052E16),
        secondary = ForestPrimary,
        onSecondary = Color.White,
        background = ForestDarkBg,
        surface = ForestDarkSurface,
        onBackground = Color.White,
        onSurface = Color.White,
    )

// Warm sepia dark variant — keeps the warm tone instead of falling back to the neutral dark scheme.
private val SepiaDarkBg = Color(0xFF2A2620)
private val SepiaDarkSurface = Color(0xFF35302A)
private val SepiaDarkColorScheme =
    darkColorScheme(
        primary = IndigoDark,
        onPrimary = Color.White,
        secondary = Indigo,
        onSecondary = Color.White,
        background = SepiaDarkBg,
        surface = SepiaDarkSurface,
        onBackground = Color(0xFFEDE4D0),
        onSurface = Color(0xFFEDE4D0),
    )

// Midnight light variant — navy-tinted light scheme so "midnight" + light stays on-brand.
private val MidnightLightBg = Color(0xFFEEF3FA)
private val MidnightLightSurface = Color(0xFFFFFFFF)
private val MidnightLightColorScheme =
    lightColorScheme(
        primary = MidnightBlue,
        onPrimary = Color.White,
        secondary = MidnightBlueLight,
        onSecondary = Color.White,
        background = MidnightLightBg,
        surface = MidnightLightSurface,
        onBackground = MidnightBg,
        onSurface = Color(0xFF13243A),
    )

// Lavender — soft purple light theme
private val LavenderPrimary = Color(0xFF7C3AED)
private val LavenderBg = Color(0xFFF5F3FF)
private val LavenderSurface = Color(0xFFEDE9FE)
private val LavenderOnBg = Color(0xFF3B0764)
private val LavenderOnSurface = Color(0xFF2E1065)

private val LavenderColorScheme =
    lightColorScheme(
        primary = LavenderPrimary,
        onPrimary = Color.White,
        secondary = LavenderPrimary,
        onSecondary = Color.White,
        background = LavenderBg,
        surface = LavenderSurface,
        onBackground = LavenderOnBg,
        onSurface = LavenderOnSurface,
    )

private val LavenderDarkBg = Color(0xFF1E1033)
private val LavenderDarkSurface = Color(0xFF2E1065)
private val LavenderDarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFA78BFA),
        onPrimary = Color(0xFF1E1033),
        secondary = LavenderPrimary,
        onSecondary = Color.White,
        background = LavenderDarkBg,
        surface = LavenderDarkSurface,
        onBackground = Color.White,
        onSurface = Color.White,
    )

// Sunset — warm amber/orange light theme
private val SunsetPrimary = Color(0xFFEA580C)
private val SunsetBg = Color(0xFFFFF7ED)
private val SunsetSurface = Color(0xFFFFEDD5)
private val SunsetOnBg = Color(0xFF431407)
private val SunsetOnSurface = Color(0xFF2A1205)

private val SunsetColorScheme =
    lightColorScheme(
        primary = SunsetPrimary,
        onPrimary = Color.White,
        secondary = SunsetPrimary,
        onSecondary = Color.White,
        background = SunsetBg,
        surface = SunsetSurface,
        onBackground = SunsetOnBg,
        onSurface = SunsetOnSurface,
    )

private val SunsetDarkBg = Color(0xFF1C0F07)
private val SunsetDarkSurface = Color(0xFF431407)
private val SunsetDarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFFB923C),
        onPrimary = Color(0xFF1C0F07),
        secondary = SunsetPrimary,
        onSecondary = Color.White,
        background = SunsetDarkBg,
        surface = SunsetDarkSurface,
        onBackground = Color.White,
        onSurface = Color.White,
    )

// Graphite — neutral monochrome theme
private val GraphitePrimary = Color(0xFF52525B)
private val GraphiteBg = Color(0xFFFAFAFA)
private val GraphiteSurface = Color(0xFFF4F4F5)
private val GraphiteOnBg = Color(0xFF18181B)
private val GraphiteOnSurface = Color(0xFF27272A)

private val GraphiteColorScheme =
    lightColorScheme(
        primary = GraphitePrimary,
        onPrimary = Color.White,
        secondary = GraphitePrimary,
        onSecondary = Color.White,
        background = GraphiteBg,
        surface = GraphiteSurface,
        onBackground = GraphiteOnBg,
        onSurface = GraphiteOnSurface,
    )

private val GraphiteDarkBg = Color(0xFF09090B)
private val GraphiteDarkSurface = Color(0xFF18181B)
private val GraphiteDarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFA1A1AA),
        onPrimary = Color(0xFF09090B),
        secondary = GraphitePrimary,
        onSecondary = Color.White,
        background = GraphiteDarkBg,
        surface = GraphiteDarkSurface,
        onBackground = Color.White,
        onSurface = Color.White,
    )

/** True-black AMOLED light fallback: AMOLED is a dark concept, so light mode uses the neutral light scheme. */

/**
 * Resolves [themeMode] (null/"system", "light", "dark") and [themeColor] ("default", "amoled",
 * "dynamic", etc.) to a ColorScheme. E.g. Dark + Forest → Forest dark variant. "dynamic" uses
 * Material You wallpaper colors on Android 12+ and falls back to the default scheme below that.
 */
@Composable
fun JottyTheme(
    themeMode: String? = null,
    themeColor: String = "default",
    themeCustomAccentHex: String = DEFAULT_CUSTOM_ACCENT_HEX,
    themeCustomTintedBackgrounds: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark =
        when (themeMode) {
            "dark" -> true
            "light" -> false
            else -> systemDark
        }
    val context = LocalContext.current
    val colorScheme =
        when (themeColor) {
            "dynamic" ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                } else {
                    if (dark) DarkColorScheme else LightColorScheme
                }
            "amoled" -> if (dark) AmoledColorScheme else LightColorScheme
            "sepia" -> if (dark) SepiaDarkColorScheme else SepiaColorScheme
            "midnight" -> if (dark) MidnightColorScheme else MidnightLightColorScheme
            "rose" -> if (dark) RoseDarkColorScheme else RoseColorScheme
            "ocean" -> if (dark) OceanDarkColorScheme else OceanColorScheme
            "forest" -> if (dark) ForestDarkColorScheme else ForestColorScheme
            "lavender" -> if (dark) LavenderDarkColorScheme else LavenderColorScheme
            "sunset" -> if (dark) SunsetDarkColorScheme else SunsetColorScheme
            "graphite" -> if (dark) GraphiteDarkColorScheme else GraphiteColorScheme
            "custom" -> {
                val accent = resolveCustomAccentColor(themeCustomAccentHex)
                customColorScheme(accent, dark = dark, tintedBackgrounds = themeCustomTintedBackgrounds)
            }
            else -> if (dark) DarkColorScheme else LightColorScheme
        }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
