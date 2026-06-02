package com.jotty.android.ui.notes

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

private val headingPrefixRegex = Regex("""^#{1,6}\s+""")
private val bulletPrefixRegex = Regex("""^(\s*)([-*+]\s+(?!\[[ xX]\]\s+))(.*)$""")
private val numberedPrefixRegex = Regex("""^(\s*)(\d+)\.\s+(.*)$""")
private val taskPrefixRegex = Regex("""^(\s*)([-*+]\s+\[[ xX]\]\s+)(.*)$""")
private val quotePrefixRegex = Regex("""^(\s*)(>\s+)(.*)$""")
private val markdownLinkRegex = Regex("""^\[[^\]]*\]\([^)]*\)$""")

/** Line bounds for the cursor position in [text]. */
internal fun lineBounds(
    text: String,
    cursor: Int,
): Pair<Int, Int> {
    val safeCursor = cursor.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', (safeCursor - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', startIndex = safeCursor).let { if (it < 0) text.length else it }
    return lineStart to lineEnd
}

/** Inserts [prefix] at the start of the line containing the cursor. */
fun prefixLine(
    value: TextFieldValue,
    prefix: String,
): TextFieldValue {
    val text = value.text
    val cursor = value.selection.min
    val (lineStart, _) = lineBounds(text, cursor)
    val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
    return value.copy(text = newText, selection = TextRange(cursor + prefix.length))
}

/** Removes [prefixLength] characters from the start of the line containing the cursor. */
internal fun removeLinePrefix(
    value: TextFieldValue,
    prefixLength: Int,
): TextFieldValue {
    val text = value.text
    val cursor = value.selection.min
    val (lineStart, _) = lineBounds(text, cursor)
    val removeLen = prefixLength.coerceAtMost(text.length - lineStart)
    val newText = text.substring(0, lineStart) + text.substring(lineStart + removeLen)
    val newCursor = (cursor - removeLen).coerceAtLeast(lineStart)
    return value.copy(text = newText, selection = TextRange(newCursor))
}

/** Adds or removes a line prefix; second tap on the same line removes it. */
internal fun togglePrefixLine(
    value: TextFieldValue,
    addPrefix: String,
    existingPrefix: Regex,
): TextFieldValue {
    val text = value.text
    val cursor = value.selection.min
    val (lineStart, lineEnd) = lineBounds(text, cursor)
    val line = text.substring(lineStart, lineEnd)
    val match = existingPrefix.find(line)
    if (match != null) {
        return removeLinePrefix(value, match.linePrefixLength())
    }
    return prefixLine(value, addPrefix)
}

fun toggleHeadingLine(value: TextFieldValue): TextFieldValue =
    togglePrefixLine(value, "# ", headingPrefixRegex)

fun toggleBulletLine(value: TextFieldValue): TextFieldValue =
    togglePrefixLine(value, "- ", bulletPrefixRegex)

fun toggleQuoteLine(value: TextFieldValue): TextFieldValue =
    togglePrefixLine(value, "> ", quotePrefixRegex)

fun toggleTaskLine(value: TextFieldValue): TextFieldValue =
    togglePrefixLine(value, "- [ ] ", taskPrefixRegex)

fun toggleNumberedLine(value: TextFieldValue): TextFieldValue {
    val text = value.text
    val cursor = value.selection.min
    val (lineStart, lineEnd) = lineBounds(text, cursor)
    val line = text.substring(lineStart, lineEnd)
    val match = numberedPrefixRegex.find(line)
    if (match != null) {
        return removeLinePrefix(value, match.linePrefixLength())
    }
    return prefixLineWithAutoIndex(value)
}

fun prefixLineWithAutoIndex(value: TextFieldValue): TextFieldValue {
    val text = value.text
    val cursor = value.selection.min
    val (lineStart, lineEnd) = lineBounds(text, cursor)
    val currentLine = text.substring(lineStart, lineEnd)
    val currentIndex = Regex("""^\s*(\d+)\.\s+""").find(currentLine)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
    val prefix = "$currentIndex. "
    val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
    return value.copy(text = newText, selection = TextRange(cursor + prefix.length))
}

/**
 * Wraps the current selection (or cursor) in [marker] on both sides. With an empty selection the
 * cursor is placed between the markers so the user can type inside.
 */
fun wrapSelection(
    value: TextFieldValue,
    marker: String,
): TextFieldValue {
    val sel = value.selection
    val start = sel.min
    val end = sel.max
    val text = value.text
    val selected = text.substring(start, end)
    val newText = text.substring(0, start) + marker + selected + marker + text.substring(end)
    val cursor = if (start == end) start + marker.length else end + marker.length * 2
    return value.copy(text = newText, selection = TextRange(cursor))
}

/** Adds formatting markers or removes them when already present (second toolbar tap). */
fun toggleWrapSelection(
    value: TextFieldValue,
    marker: String,
): TextFieldValue {
    val sel = value.selection
    val start = sel.min
    val end = sel.max
    val text = value.text

    if (start != end) {
        val selected = text.substring(start, end)
        if (selected.length >= marker.length * 2 &&
            selected.startsWith(marker) &&
            selected.endsWith(marker)
        ) {
            val inner = selected.substring(marker.length, selected.length - marker.length)
            val newText = text.substring(0, start) + inner + text.substring(end)
            return value.copy(text = newText, selection = TextRange(start, start + inner.length))
        }
    } else if (start >= marker.length && start + marker.length <= text.length) {
        if (text.substring(start - marker.length, start) == marker &&
            text.substring(start, start + marker.length) == marker
        ) {
            val newText = text.substring(0, start - marker.length) + text.substring(start + marker.length)
            return value.copy(text = newText, selection = TextRange(start - marker.length))
        }
    }

    return wrapSelection(value, marker)
}

/** Inserts a Markdown link template, selecting the placeholder text so it can be overwritten. */
fun insertLink(value: TextFieldValue): TextFieldValue {
    val sel = value.selection
    val text = value.text
    val label = if (sel.min != sel.max) text.substring(sel.min, sel.max) else "text"
    val snippet = "[$label](url)"
    val newText = text.substring(0, sel.min) + snippet + text.substring(sel.max)
    val urlStart = sel.min + snippet.indexOf("url")
    return value.copy(text = newText, selection = TextRange(urlStart, urlStart + 3))
}

/** Removes a link template when the selection is already a Markdown link; otherwise inserts one. */
fun toggleLink(value: TextFieldValue): TextFieldValue {
    val sel = value.selection
    val text = value.text
    if (sel.min != sel.max) {
        val selected = text.substring(sel.min, sel.max)
        if (markdownLinkRegex.matches(selected)) {
            val label = Regex("""^\[([^\]]*)\]\([^)]*\)$""").find(selected)?.groupValues?.getOrNull(1).orEmpty()
            val newText = text.substring(0, sel.min) + label + text.substring(sel.max)
            return value.copy(text = newText, selection = TextRange(sel.min, sel.min + label.length))
        }
    }
    return insertLink(value)
}

/**
 * When the user presses Enter in a list, task, or quote line, continue the prefix on the new line.
 * An empty list item removes the prefix instead (exit the list).
 */
fun continueMarkdownBlockOnEnter(
    previous: TextFieldValue,
    updated: TextFieldValue,
): TextFieldValue {
    if (updated.text.length != previous.text.length + 1) return updated

    val cursor = updated.selection.min
    if (cursor <= 0 || updated.text[cursor - 1] != '\n') return updated

    val prevText = previous.text
    val prevCursor = previous.selection.min.coerceIn(0, prevText.length)
    val (lineStart, lineEnd) = lineBounds(prevText, prevCursor)
    val line = prevText.substring(lineStart, lineEnd)

    taskPrefixRegex.find(line)?.let { match ->
        return handleListEnter(previous, updated, match, "- [ ] ")
    }
    bulletPrefixRegex.find(line)?.let { match ->
        return handleListEnter(previous, updated, match, "- ")
    }
    numberedPrefixRegex.find(line)?.let { match ->
        val indent = match.groupValues[1]
        val number = match.groupValues[2].toIntOrNull() ?: 1
        val content = match.groupValues[3]
        if (content.isBlank()) {
            return exitListItem(previous, match.linePrefixLength())
        }
        val prefix = "$indent${number + 1}. "
        return insertAfterNewline(updated, cursor, prefix)
    }
    quotePrefixRegex.find(line)?.let { match ->
        val indent = match.groupValues[1]
        val content = match.groupValues[3]
        if (content.isBlank()) {
            return exitListItem(previous, match.linePrefixLength())
        }
        return insertAfterNewline(updated, cursor, "$indent> ")
    }

    return updated
}

private fun handleListEnter(
    previous: TextFieldValue,
    updated: TextFieldValue,
    match: MatchResult,
    marker: String,
): TextFieldValue {
    val indent = match.groupValues[1]
    val content = match.groupValues[3]
    if (content.isBlank()) {
        return exitListItem(previous, match.linePrefixLength())
    }
    return insertAfterNewline(updated, updated.selection.min, "$indent$marker")
}

private fun MatchResult.linePrefixLength(): Int {
    if (groupValues.size <= 1) return range.last + 1
    return groups[groupValues.lastIndex]?.range?.first ?: (range.last + 1)
}

private fun exitListItem(
    previous: TextFieldValue,
    prefixLength: Int,
): TextFieldValue {
    val withoutPrefix = removeLinePrefix(previous, prefixLength)
    val cursor = withoutPrefix.selection.min
    return withoutPrefix.copy(
        text = withoutPrefix.text.substring(0, cursor) + "\n" + withoutPrefix.text.substring(cursor),
        selection = TextRange(cursor + 1),
    )
}

private fun insertAfterNewline(
    updated: TextFieldValue,
    cursor: Int,
    prefix: String,
): TextFieldValue {
    val newText = updated.text.substring(0, cursor) + prefix + updated.text.substring(cursor)
    return updated.copy(text = newText, selection = TextRange(cursor + prefix.length))
}

/** Applies smart list/quote continuation when the content field gains a newline. */
fun applyMarkdownContentChange(
    previous: TextFieldValue,
    updated: TextFieldValue,
): TextFieldValue = continueMarkdownBlockOnEnter(previous, updated)
