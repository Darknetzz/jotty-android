package com.jotty.android.util

/**
 * Invisible/special Unicode that often render as "?" or cause layout issues.
 * BOM (U+FEFF) and zero-width characters are common when pasting from web/Word.
 */
private val INVISIBLE_EDGE_CHARS = setOf(
    '\uFEFF', // BOM (byte order mark)
    '\u200B', // zero-width space
    '\u200C', // zero-width non-joiner
    '\u200D', // zero-width joiner
    '\u2060', // word joiner
)

/**
 * Strips BOM and zero-width (and similar) characters from the start and end of [s].
 * Use when displaying or editing note title/content so they don't show as "?".
 */
fun stripInvisibleFromEdges(s: String): String {
    var start = 0
    var end = s.length
    while (start < end && s[start] in INVISIBLE_EDGE_CHARS) start++
    while (end > start && s[end - 1] in INVISIBLE_EDGE_CHARS) end--
    return if (start == 0 && end == s.length) s else s.substring(start, end)
}

/**
 * Formats an ISO-style date string (e.g. from API) for display.
 * Returns date part only (YYYY-MM-DD) when possible, otherwise first 10 chars.
 */
fun formatNoteDate(updatedAt: String): String = try {
    val iso = updatedAt.replace("Z", "+00:00")
    val i = iso.indexOf('T')
    if (i > 0) iso.substring(0, i) else updatedAt.take(10)
} catch (_: Exception) {
    updatedAt.take(10)
}
