package com.jotty.android.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Default custom accent (indigo) when none is stored. */
const val DEFAULT_CUSTOM_ACCENT_HEX = "#6366F1"

/** Parses `#RRGGBB`, `RRGGBB`, or `0xAARRGGBB` into an opaque [Color]. */
fun parseThemeAccentHex(hex: String?): Color? {
    val raw = hex?.trim()?.removePrefix("#")?.removePrefix("0x")?.removePrefix("0X") ?: return null
    if (raw.length !in 6..8) return null
    return try {
        val value = raw.toLong(16)
        val rgb =
            when (raw.length) {
                8 -> value and 0xFFFFFF
                else -> value
            }
        Color((rgb.toInt() and 0xFFFFFF) or 0xFF000000.toInt())
    } catch (_: Exception) {
        null
    }
}

/** Normalizes user input to `#RRGGBB` or null when invalid. */
fun normalizeThemeAccentHex(hex: String?): String? {
    val color = parseThemeAccentHex(hex) ?: return null
    return "#%02X%02X%02X".format(
        (color.red * 255f).toInt().coerceIn(0, 255),
        (color.green * 255f).toInt().coerceIn(0, 255),
        (color.blue * 255f).toInt().coerceIn(0, 255),
    )
}

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

private fun contrastOn(background: Color): Color =
    if (background.luminance() > 0.55f) Color(0xFF0F172A) else Color.White

private fun Color.lighten(amount: Float): Color =
    Color(
        red = red + (1f - red) * amount,
        green = green + (1f - green) * amount,
        blue = blue + (1f - blue) * amount,
        alpha = alpha,
    )

private fun Color.darken(amount: Float): Color =
    Color(
        red = red * (1f - amount),
        green = green * (1f - amount),
        blue = blue * (1f - amount),
        alpha = alpha,
    )

/**
 * Builds a Material 3 [ColorScheme] from a user-chosen accent.
 * [tintedBackgrounds] applies a subtle accent wash to background/surface (like Forest/Ocean themes).
 */
fun customColorScheme(
    accent: Color,
    dark: Boolean,
    tintedBackgrounds: Boolean,
): ColorScheme {
    val onAccent = contrastOn(accent)
    return if (dark) {
        val primary = accent.lighten(0.25f)
        val background =
            if (tintedBackgrounds) {
                lerp(Color.Black, accent.darken(0.55f), 0.35f)
            } else {
                Color(0xFF0F172A)
            }
        val surface =
            if (tintedBackgrounds) {
                lerp(background, accent.darken(0.35f), 0.4f)
            } else {
                Color(0xFF1E293B)
            }
        darkColorScheme(
            primary = primary,
            onPrimary = contrastOn(primary),
            secondary = accent,
            onSecondary = onAccent,
            background = background,
            surface = surface,
            onBackground = Color.White,
            onSurface = Color.White,
        )
    } else {
        val background =
            if (tintedBackgrounds) {
                lerp(Color.White, accent.lighten(0.75f), 0.12f)
            } else {
                Color(0xFFF1F5F9)
            }
        val surface =
            if (tintedBackgrounds) {
                lerp(Color.White, accent.lighten(0.85f), 0.08f)
            } else {
                Color.White
            }
        val onSurface = lerp(Color(0xFF1E293B), accent.darken(0.65f), if (tintedBackgrounds) 0.35f else 0f)
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            secondary = accent,
            onSecondary = onAccent,
            background = background,
            surface = surface,
            onBackground = Color(0xFF0F172A),
            onSurface = onSurface,
        )
    }
}

fun resolveCustomAccentColor(hex: String?): Color =
    parseThemeAccentHex(hex) ?: parseThemeAccentHex(DEFAULT_CUSTOM_ACCENT_HEX)!!
