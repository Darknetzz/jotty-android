package com.jotty.android.util

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
