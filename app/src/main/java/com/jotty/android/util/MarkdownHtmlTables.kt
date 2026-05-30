package com.jotty.android.util

/**
 * Jotty web often stores tables as HTML (user setting `tableSyntax: html`, the default).
 * [compose-markdown] / Markwon renders GFM pipe tables but not HTML `<table>` blocks, so cells
 * appear as a vertical stack of paragraphs. Convert HTML tables to GFM before display.
 */
private val regexDotIgnoreCase = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

fun convertHtmlTablesToGfm(markdown: String): String {
    if (!markdown.contains("<table", ignoreCase = true)) {
        return markdown
    }
    val tablePattern = Regex("""<table\b[^>]*>.*?</table>""", regexDotIgnoreCase)
    return tablePattern.replace(markdown) { match -> htmlTableToGfm(match.value) }
}

/**
 * Converts inline HTML color spans (common in web-authored notes) to a simpler
 * `<font color="...">...</font>` format that Markwon's HTML support can render.
 */
/**
 * Unwraps font-family spans from the Jotty web editor (TipTap) so Markwon sees plain text /
 * inner markdown instead of raw HTML that renders as broken tags (e.g. `pan style=...`).
 */
fun convertHtmlFontFamilySpans(markdown: String): String {
    if (!markdown.contains("font-family", ignoreCase = true)) {
        return markdown
    }
    val spanPattern =
        Regex(
            """<span\b[^>]*\bstyle\s*=\s*(['"])(?:(?!\1).)*font-family\s*:[^;'"]+(?:(?!\1).)*\1[^>]*>(.*?)</span>""",
            regexDotIgnoreCase,
        )
    var result = markdown
    var previous: String
    do {
        previous = result
        result = spanPattern.replace(result) { match -> match.groupValues[2] }
    } while (result != previous)
    return result
}

fun convertHtmlColorSpans(markdown: String): String {
    if (!markdown.contains("<span", ignoreCase = true)) {
        return markdown
    }
    val spanPattern =
        Regex("""<span\b([^>]*?)style\s*=\s*(['"])(.*?)\2([^>]*)>(.*?)</span>""", regexDotIgnoreCase)
    return spanPattern.replace(markdown) { match ->
        val styleValue = match.groupValues[3]
        val innerHtml = match.groupValues[5]
        val colorValue = extractCssColor(styleValue)?.let(::normalizeHtmlColor) ?: return@replace match.value
        """<font color="$colorValue">$innerHtml</font>"""
    }
}

private fun extractCssColor(styleValue: String): String? {
    val colorPattern = Regex("""(?:^|;)\s*color\s*:\s*([^;]+)""", RegexOption.IGNORE_CASE)
    return colorPattern.find(styleValue)?.groupValues?.getOrNull(1)?.trim()
}

private fun normalizeHtmlColor(rawColor: String): String {
    val color = rawColor.trim().removeSurrounding("\"").removeSurrounding("'")
    if (color.startsWith("#")) {
        return color
    }
    val rgbPattern = Regex("""rgba?\s*\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})(?:\s*,\s*[\d.]+\s*)?\)""", RegexOption.IGNORE_CASE)
    val rgbMatch = rgbPattern.matchEntire(color)
    if (rgbMatch != null) {
        val channels =
            (1..3).map { index ->
                rgbMatch.groupValues[index].toIntOrNull()?.coerceIn(0, 255) ?: 0
            }
        return "#%02x%02x%02x".format(channels[0], channels[1], channels[2])
    }
    return color
}

/**
 * Converts HTML `<img>` tags to Markdown image syntax.
 *
 * Jotty notes can contain HTML images (for example from pasted rich content).
 * `compose-markdown` does not reliably render those tags, but it does render
 * Markdown images.
 */
fun convertHtmlImagesToMarkdown(markdown: String): String {
    if (!markdown.contains("<img", ignoreCase = true) &&
        !markdown.contains("<figure", ignoreCase = true) &&
        !markdown.contains("<picture", ignoreCase = true)
    ) {
        return markdown
    }
    var result = markdown
    result = convertHtmlImageContainers(result, "figure")
    result = convertHtmlImageContainers(result, "picture")
    return convertStandaloneHtmlImgTags(result)
}

private fun convertHtmlImageContainers(
    markdown: String,
    tagName: String,
): String {
    val containerPattern =
        Regex("""<$tagName\b[^>]*>.*?</$tagName>""", regexDotIgnoreCase)
    return containerPattern.replace(markdown) { match ->
        val converted = convertSingleHtmlImageTag(match.value)
        converted ?: match.value
    }
}

private fun convertStandaloneHtmlImgTags(markdown: String): String {
    val imgPattern = Regex("""<img\b[^>]*?>""", RegexOption.IGNORE_CASE)
    return imgPattern.replace(markdown) { match ->
        convertSingleHtmlImageTag(match.value) ?: match.value
    }
}

private fun convertSingleHtmlImageTag(html: String): String? {
    val imgTagMatch = Regex("""<img\b[^>]*?>""", RegexOption.IGNORE_CASE).find(html)
    val imgTag = imgTagMatch?.value ?: return null
    val src = extractHtmlAttribute(imgTag, "src")?.trim().orEmpty()
    if (src.isBlank()) {
        return null
    }
    val alt = extractHtmlAttribute(imgTag, "alt")?.trim().orEmpty()
    return "![${escapeImageAltText(alt)}]($src)"
}

private fun extractHtmlAttribute(
    tag: String,
    attributeName: String,
): String? {
    val quotedPattern =
        Regex("""\b$attributeName\s*=\s*(['"])(.*?)\1""", regexDotIgnoreCase)
    quotedPattern.find(tag)?.groupValues?.getOrNull(2)?.let { return it }
    val unquotedPattern = Regex("""\b$attributeName\s*=\s*([^\s"'<>`]+)""", RegexOption.IGNORE_CASE)
    return unquotedPattern.find(tag)?.groupValues?.getOrNull(1)
}

private fun escapeImageAltText(alt: String): String =
    alt
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("\n", " ")
        .trim()

private val rowPattern = Regex("""<tr\b[^>]*>(.*?)</tr>""", regexDotIgnoreCase)
private val cellPattern = Regex("""<t([hd])\b[^>]*>(.*?)</t\1>""", regexDotIgnoreCase)

private fun htmlTableToGfm(html: String): String {
    val rows =
        rowPattern.findAll(html).map { rowMatch ->
            val cells =
                cellPattern.findAll(rowMatch.groupValues[1]).map { cellMatch ->
                    val isHeader = cellMatch.groupValues[1].equals("h", ignoreCase = true)
                    htmlInlineToMarkdown(cellMatch.groupValues[2]) to isHeader
                }.toList()
            cells
        }.filter { it.isNotEmpty() }
            .toList()
    if (rows.isEmpty()) {
        return html
    }

    val headerRowIndex = rows.indexOfFirst { row -> row.any { it.second } }.takeIf { it >= 0 } ?: 0
    val headerCells = rows[headerRowIndex].map { it.first }
    val columnCount = headerCells.size.coerceAtLeast(rows.maxOfOrNull { it.size } ?: 0)
    if (columnCount == 0) {
        return html
    }

    val normalizedHeader = padRow(headerCells, columnCount)
    val dataRows =
        rows.withIndex()
            .filter { (index, _) -> index != headerRowIndex }
            .map { (_, row) -> padRow(row.map { it.first }, columnCount) }

    return buildString {
        appendLine("| ${normalizedHeader.joinToString(" | ")} |")
        appendLine("| ${List(columnCount) { "---" }.joinToString(" | ")} |")
        for (row in dataRows) {
            appendLine("| ${row.joinToString(" | ")} |")
        }
    }.trimEnd()
}

private fun padRow(
    cells: List<String>,
    columnCount: Int,
): List<String> =
    buildList(columnCount) {
        for (i in 0 until columnCount) {
            add(escapeTableCell(cells.getOrElse(i) { "" }))
        }
    }

private fun escapeTableCell(text: String): String = text.replace("|", "\\|").replace("\n", " ").trim()

private val anchorPattern =
    Regex("""<a\s+[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""", regexDotIgnoreCase)
private val tagPattern = Regex("<[^>]+>")

private fun htmlInlineToMarkdown(html: String): String {
    var text = decodeBasicHtmlEntities(html.trim())
    text = text.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), " ")
    var iterations = 0
    while (iterations < 8) {
        val next =
            text
                .replace(anchorPattern) { m ->
                    val label = htmlInlineToMarkdown(m.groupValues[2]).ifBlank { m.groupValues[1] }
                    "[$label](${m.groupValues[1]})"
                }
                .replace(Regex("""<(strong|b)\b[^>]*>(.*?)</\1>""", regexDotIgnoreCase), "**$2**")
                .replace(Regex("""<(em|i)\b[^>]*>(.*?)</\1>""", regexDotIgnoreCase), "*$2*")
                .replace(Regex("""<code\b[^>]*>(.*?)</code>""", regexDotIgnoreCase), "`$1`")
                .replace(Regex("""<p\b[^>]*>""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""</p>""", RegexOption.IGNORE_CASE), " ")
                .replace(tagPattern, "")
        if (next == text) break
        text = next
        iterations++
    }
    return decodeBasicHtmlEntities(text).replace(Regex("\\s+"), " ").trim()
}

private val decimalEntityPattern = Regex("""&#(\d{1,7});""")
private val hexEntityPattern = Regex("""&#x([0-9a-fA-F]{1,6});""")

private fun decodeBasicHtmlEntities(text: String): String {
    val withNumeric =
        text
            .replace(decimalEntityPattern) { match ->
                val codePoint = match.groupValues[1].toIntOrNull() ?: return@replace match.value
                codePointToString(codePoint) ?: match.value
            }.replace(hexEntityPattern) { match ->
                val codePoint = match.groupValues[1].toIntOrNull(16) ?: return@replace match.value
                codePointToString(codePoint) ?: match.value
            }
    return withNumeric
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
}

private fun codePointToString(codePoint: Int): String? {
    if (codePoint < 0 || codePoint > 0x10FFFF) return null
    if (codePoint in 0xD800..0xDFFF) return null
    return String(Character.toChars(codePoint))
}
