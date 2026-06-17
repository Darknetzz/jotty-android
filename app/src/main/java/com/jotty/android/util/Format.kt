package com.jotty.android.util

/**
 * Invisible/special Unicode that often render as "?" or cause layout issues.
 * BOM (U+FEFF) and zero-width characters are common when pasting from web/Word.
 */
private val INVISIBLE_UNICODE_CHARS =
    setOf(
        // BOM (byte order mark)
        '\uFEFF',
        // zero-width space
        '\u200B',
        // zero-width non-joiner
        '\u200C',
        // zero-width joiner
        '\u200D',
        // word joiner
        '\u2060',
    )

/**
 * Removes BOM and zero-width characters anywhere in [s].
 * These often appear inside web-authored HTML (e.g. before `<span>`) and break markdown/HTML
 * rendering as "" while round-tripping back to the server on save.
 */
fun stripInvisibleUnicode(s: String): String {
    if (s.isEmpty() || s.none { it in INVISIBLE_UNICODE_CHARS }) {
        return s
    }
    return buildString(s.length) {
        for (ch in s) {
            if (ch !in INVISIBLE_UNICODE_CHARS) {
                append(ch)
            }
        }
    }
}

/**
 * Strips BOM and zero-width (and similar) characters from the start and end of [s].
 * Use when displaying or editing note title/content so they don't show as "?".
 */
fun stripInvisibleFromEdges(s: String): String {
    var start = 0
    var end = s.length
    while (start < end && s[start] in INVISIBLE_UNICODE_CHARS) start++
    while (end > start && s[end - 1] in INVISIBLE_UNICODE_CHARS) end--
    return if (start == 0 && end == s.length) s else s.substring(start, end)
}

/**
 * Decodes literal `\\uXXXX` sequences left in note text when [android.webkit.WebView.evaluateJavascript]
 * results were not fully JSON-unescaped (e.g. HTML saved as `\u003Ctable` instead of `<table>`).
 * No-op when the string contains no such escapes.
 */
fun decodeJsonUnicodeEscapes(s: String): String {
    if (!s.contains("\\u")) return s
    return JSON_UNICODE_ESCAPE.replace(s) { match ->
        match.groupValues[1].toInt(16).toChar().toString()
    }
}

private val JSON_UNICODE_ESCAPE = Regex("""\\u([0-9a-fA-F]{4})""")

/**
 * Formats an ISO-style date string (e.g. from API) for display.
 * Returns date part only (YYYY-MM-DD) when possible, otherwise first 10 chars.
 */
fun formatNoteDate(updatedAt: String): String =
    try {
        val iso = updatedAt.replace("Z", "+00:00")
        val i = iso.indexOf('T')
        if (i > 0) iso.substring(0, i) else updatedAt.take(10)
    } catch (_: Exception) {
        updatedAt.take(10)
    }
