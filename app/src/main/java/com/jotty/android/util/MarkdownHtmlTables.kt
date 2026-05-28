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
 * Converts HTML `<img>` tags to Markdown image syntax.
 *
 * Jotty notes can contain HTML images (for example from pasted rich content).
 * `compose-markdown` does not reliably render those tags, but it does render
 * Markdown images.
 */
fun convertHtmlImagesToMarkdown(markdown: String): String {
    if (!markdown.contains("<img", ignoreCase = true)) {
        return markdown
    }
    val imgPattern = Regex("""<img\b[^>]*?>""", RegexOption.IGNORE_CASE)
    return imgPattern.replace(markdown) { match ->
        val tag = match.value
        val src = extractHtmlAttribute(tag, "src")?.trim().orEmpty()
        if (src.isBlank()) {
            return@replace match.value
        }
        val alt = extractHtmlAttribute(tag, "alt")?.trim().orEmpty()
        "![${escapeImageAltText(alt)}]($src)"
    }
}

private fun extractHtmlAttribute(
    tag: String,
    attributeName: String,
): String? {
    val attributePattern =
        Regex("""\b$attributeName\s*=\s*(['"])(.*?)\1""", regexDotIgnoreCase)
    return attributePattern.find(tag)?.groupValues?.getOrNull(2)
}

private fun escapeImageAltText(alt: String): String = alt.replace("[", "\\[").replace("]", "\\]")

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
    var text = html.trim()
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

private fun decodeBasicHtmlEntities(text: String): String =
    text
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
