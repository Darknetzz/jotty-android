package com.jotty.android.data.preferences

/**
 * A saved Jotty server connection (name, URL, API key).
 */
data class JottyInstance(
    val id: String,
    val name: String,
    val serverUrl: String,
    val apiKey: String,
)
