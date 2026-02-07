package com.jotty.android.data.preferences

/**
 * A saved Jotty server connection (name, URL, API key).
 * [colorHex] optional (e.g. "0xFF6200EE") for list/icon tint; null = default.
 */
data class JottyInstance(
    val id: String,
    val name: String,
    val serverUrl: String,
    val apiKey: String,
    val colorHex: Long? = null,
)
