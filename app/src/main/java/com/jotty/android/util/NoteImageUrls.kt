package com.jotty.android.util

import com.jotty.android.data.api.ApiClient

/**
 * Resolves note image URLs for Coil / Markdown rendering.
 * Jotty often stores relative paths such as `/api/image/...` that must be absolute against the server base URL.
 */
internal fun resolveNoteImageUrl(
    src: String,
    baseUrl: String?,
): String {
    val trimmed = src.trim()
    if (trimmed.isEmpty()) return trimmed
    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        return trimmed
    }
    if (baseUrl.isNullOrBlank()) return trimmed
    val normalized = ApiClient.normalizeBaseUrl(baseUrl)
    return when {
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("/") -> normalized.trimEnd('/') + trimmed
        else -> "${normalized.trimEnd('/')}/$trimmed"
    }
}

private val markdownImageRegex = Regex("""!\[([^\]]*)\]\(([^)]+)\)""")

internal fun resolveNoteImageUrlsInMarkdown(
    markdown: String,
    baseUrl: String?,
): String {
    if (baseUrl.isNullOrBlank() || !markdown.contains("](")) return markdown
    return markdownImageRegex.replace(markdown) { match ->
        val alt = match.groupValues[1]
        val resolved = resolveNoteImageUrl(match.groupValues[2], baseUrl)
        "![$alt]($resolved)"
    }
}
