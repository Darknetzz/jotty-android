package com.jotty.android.data.preferences

/**
 * A saved Jotty server connection (name, URL, API key).
 * [colorHex] optional (e.g. "0xFF6200EE") for list/icon tint; null = default.
 * [customHeaders] optional extra HTTP headers sent with every request (e.g. for reverse-proxy auth).
 */
data class JottyInstance(
    val id: String,
    val name: String,
    val serverUrl: String,
    val apiKey: String,
    val colorHex: Long? = null,
    val customHeaders: Map<String, String> = emptyMap(),
)
