package com.jotty.android.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CustomColorSchemeTest {
    @Test
    fun normalizeThemeAccentHex_acceptsHashPrefix() {
        assertEquals("#6366F1", normalizeThemeAccentHex("#6366F1"))
        assertEquals("#6366F1", normalizeThemeAccentHex("6366F1"))
    }

    @Test
    fun normalizeThemeAccentHex_rejectsInvalid() {
        assertNull(normalizeThemeAccentHex("not-a-color"))
        assertNull(normalizeThemeAccentHex("#GGG"))
    }

    @Test
    fun resolveCustomAccentColor_fallsBackToDefault() {
        val fallback = resolveCustomAccentColor("not-valid")
        val expected = parseThemeAccentHex(DEFAULT_CUSTOM_ACCENT_HEX)
        assertEquals(expected, fallback)
    }

    @Test
    fun customColorScheme_neutralModeUsesAccentForContainers() {
        val accent = parseThemeAccentHex("#B91C3C")!!
        val defaultPrimaryContainer = customColorScheme(accent, dark = true, tintedBackgrounds = false).primaryContainer
        val otherAccent = parseThemeAccentHex("#0E7490")!!
        val otherPrimaryContainer = customColorScheme(otherAccent, dark = true, tintedBackgrounds = false).primaryContainer
        assert(defaultPrimaryContainer != otherPrimaryContainer) {
            "Neutral custom theme should derive container colors from the accent"
        }
    }

    @Test
    fun customColorScheme_buildsForLightAndDark() {
        val accent = parseThemeAccentHex("#EA580C")!!
        assertNotNull(customColorScheme(accent, dark = false, tintedBackgrounds = false))
        assertNotNull(customColorScheme(accent, dark = true, tintedBackgrounds = true))
    }
}
