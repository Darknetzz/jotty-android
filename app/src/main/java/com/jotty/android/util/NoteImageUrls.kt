package com.jotty.android.util

import com.jotty.android.data.api.ApiClient
import java.net.URI

/**
 * Resolves note image URLs for Coil / Markdown rendering.
 * Jotty stores paths such as `/api/image/{user}/{file}` (root-relative) or absolute URLs
 * from another host the user used in the web app.
 */
internal fun resolveNoteImageUrl(
    src: String,
    baseUrl: String?,
): String {
    val trimmed = unwrapMarkdownUrlSrc(src.trim())
    if (trimmed.isEmpty()) return trimmed
    if (trimmed.startsWith("data:", ignoreCase = true)) return trimmed
    if (isAbsoluteHttpUrl(trimmed)) {
        return rewriteJottyMediaHostToInstance(trimmed, baseUrl)
    }
    if (baseUrl.isNullOrBlank()) return trimmed
    val normalized = ApiClient.normalizeBaseUrl(baseUrl)
    return try {
        URI(normalized).resolve(trimmed).toString()
    } catch (_: Exception) {
        "${normalized.trimEnd('/')}/$trimmed"
    }
}

internal fun isJottyMediaPath(path: String?): Boolean {
    if (path.isNullOrBlank()) return false
    return path.startsWith("/api/image/") ||
        path.startsWith("/api/file/") ||
        path.startsWith("/api/video/")
}

/**
 * Rewrites Jotty media URLs to the configured instance origin while preserving path/query.
 * Notes authored in the web UI may reference a LAN IP or hostname that differs from the app instance URL.
 */
internal fun rewriteJottyMediaHostToInstance(
    url: String,
    baseUrl: String?,
): String {
    if (baseUrl.isNullOrBlank()) return url
    return try {
        val parsed = URI(url)
        if (!isJottyMediaPath(parsed.path)) return url
        val base = URI(ApiClient.normalizeBaseUrl(baseUrl))
        URI(
            base.scheme,
            base.userInfo,
            base.host,
            base.port,
            parsed.path,
            parsed.query,
            parsed.fragment,
        ).toString()
    } catch (_: Exception) {
        url
    }
}

private fun isAbsoluteHttpUrl(url: String): Boolean =
    url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

private fun unwrapMarkdownUrlSrc(src: String): String {
    val withoutTitle = src.trim().substringBefore(' ').trim()
    if (withoutTitle.startsWith("<") && withoutTitle.endsWith(">")) {
        return withoutTitle.substring(1, withoutTitle.length - 1).trim()
    }
    return withoutTitle
}

private val markdownImageRegex = Regex("""!\[([^\]]*)\]\(([^)]+)\)""")

private val htmlImgSrcRegex =
    Regex(
        """(<img\b[^>]*\bsrc\s*=\s*)(['"])(.*?)\2""",
        RegexOption.IGNORE_CASE,
    )

internal fun resolveNoteImageUrlsInHtml(
    html: String,
    baseUrl: String?,
): String {
    if (baseUrl.isNullOrBlank() || !html.contains("<img", ignoreCase = true)) return html
    return htmlImgSrcRegex.replace(html) { match ->
        val prefix = match.groupValues[1]
        val quote = match.groupValues[2]
        val resolved = resolveNoteImageUrl(match.groupValues[3], baseUrl)
        "$prefix$quote$resolved$quote"
    }
}

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

/** True when [content] references Jotty-hosted media (e.g. `/api/image/...`). */
internal fun noteContainsJottyMediaUrls(content: String): Boolean {
    if (content.isBlank()) return false
    return content.contains("/api/image/", ignoreCase = true) ||
        content.contains("/api/file/", ignoreCase = true) ||
        content.contains("/api/video/", ignoreCase = true)
}

/** Prepares note body markdown/HTML for [NoteView] / Markwon rendering. */
internal fun prepareNoteContentForDisplay(
    content: String,
    jottyServerUrl: String?,
): String {
    val stripped = stripInvisibleUnicode(content)
    val withFonts = convertHtmlFontFamilySpans(stripped)
    val withColors = convertHtmlColorSpans(withFonts)
    val withResolvedHtmlSrc = resolveNoteImageUrlsInHtml(withColors, jottyServerUrl)
    val htmlAsMarkdown = convertHtmlImagesToMarkdown(withResolvedHtmlSrc)
    val withStructure = convertHtmlStructuralElementsToMarkdown(htmlAsMarkdown)
    val withTables = convertHtmlTablesToGfm(withStructure)
    val separated = separateMarkdownHeadingsFromTables(withTables)
    return resolveNoteImageUrlsInMarkdown(separated, jottyServerUrl)
}
