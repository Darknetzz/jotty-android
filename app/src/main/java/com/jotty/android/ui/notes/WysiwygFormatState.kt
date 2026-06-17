package com.jotty.android.ui.notes

import com.google.gson.Gson
import com.jotty.android.util.decodeJsonUnicodeEscapes

internal data class WysiwygFormatState(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikeThrough: Boolean = false,
    val unorderedList: Boolean = false,
    val orderedList: Boolean = false,
    val heading: Boolean = false,
    val blockquote: Boolean = false,
    val code: Boolean = false,
    val link: Boolean = false,
)

private val wysiwygFormatStateGson = Gson()

internal fun parseWysiwygFormatStateJson(json: String): WysiwygFormatState {
    return try {
        wysiwygFormatStateGson.fromJson(json, WysiwygFormatState::class.java) ?: WysiwygFormatState()
    } catch (_: Exception) {
        WysiwygFormatState()
    }
}

/** Unwraps a JSON string returned from [android.webkit.WebView.evaluateJavascript]. */
internal fun parseWebViewJsonResult(result: String?): String? {
    if (result.isNullOrBlank() || result == "null") return null
    val trimmed = result.trim()
    if (trimmed.length < 2 || trimmed.first() != '"') return trimmed
    return try {
        wysiwygFormatStateGson.fromJson(trimmed, String::class.java)
    } catch (_: Exception) {
        trimmed
            .removeSurrounding("\"")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .let(::decodeJsonUnicodeEscapes)
    }
}
