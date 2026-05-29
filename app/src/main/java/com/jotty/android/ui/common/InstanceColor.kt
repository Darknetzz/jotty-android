package com.jotty.android.ui.common

import androidx.compose.ui.graphics.Color
import com.jotty.android.data.preferences.JottyInstance

/**
 * Resolves an instance's optional [JottyInstance.colorHex] to an opaque [Color], or null when unset.
 * Shared by Setup, Settings, and the main header accent/switcher so the conversion lives in one place.
 */
fun JottyInstance.accentColor(): Color? =
    colorHex?.let { Color((it.toInt() and 0xFFFFFF) or 0xFF000000.toInt()) }
