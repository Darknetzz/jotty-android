package com.jotty.android.util

/** A run of text inside a changed [DiffLine]; [changed] marks the part that differs from the paired line. */
data class DiffSegment(
    val text: String,
    val changed: Boolean,
)

/** One line in a unified text diff. */
data class DiffLine(
    val kind: DiffKind,
    val text: String,
    /**
     * Intra-line segments for an ADDED/REMOVED line paired with its counterpart
     * (e.g. one edited line). Null when the whole line is new/removed or unchanged.
     */
    val segments: List<DiffSegment>? = null,
)

enum class DiffKind {
    EQUAL,
    ADDED,
    REMOVED,
}

/** Above this many token comparisons, intra-line diff falls back to common prefix/suffix. */
private const val MAX_INLINE_LCS_CELLS = 250_000

/** Above this many line comparisons (after trimming common ends), the line diff skips LCS. */
private const val MAX_LINE_LCS_CELLS = 4_000_000

private const val BLOCK_TAGS = "div|p|li|ul|ol|h[1-6]|blockquote|pre|table|thead|tbody|tr"
private val htmlBlockClose = Regex("(</(?:$BLOCK_TAGS)>|<br\\s*/?>)", RegexOption.IGNORE_CASE)
private val htmlBlockOpen = Regex("(<(?:$BLOCK_TAGS)(?:\\s[^>]*)?>)", RegexOption.IGNORE_CASE)
private val inlineToken = Regex("<[^>]*>|\\w+|\\s+|.", RegexOption.DOT_MATCHES_ALL)
private const val BREAK = '\u0000'

/**
 * Splits [text] into display lines for diffing. Besides real newlines, HTML block boundaries
 * (`<div>`, `</div>`, `<br>`, `<p>`, …) start new lines so notes saved by the web editor as one
 * long HTML line produce readable hunks instead of a single changed line.
 */
internal fun diffDisplayLines(text: String): List<String> {
    val normalized = text.replace("\r\n", "\n")
    return normalized.split('\n').flatMap { line ->
        if (!line.contains('<')) {
            listOf(line)
        } else {
            val marked =
                line
                    .replace(htmlBlockOpen) { BREAK + it.value }
                    .replace(htmlBlockClose) { it.value + BREAK }
            marked.split(BREAK).filter { it.isNotEmpty() }.ifEmpty { listOf("") }
        }
    }
}

/**
 * Line-based LCS diff for pending-sync previews (no external dependency), with
 * word-level highlighting for removed/added lines that pair up as edits.
 */
fun computeLineDiff(
    oldText: String,
    newText: String,
): List<DiffLine> {
    val oldLines = diffDisplayLines(oldText)
    val newLines = diffDisplayLines(newText)
    if (oldLines == newLines) {
        return oldLines.map { DiffLine(DiffKind.EQUAL, it) }
    }

    // Trim common head/tail so LCS only runs on the changed middle.
    var head = 0
    while (head < oldLines.size && head < newLines.size && oldLines[head] == newLines[head]) head++
    var tail = 0
    while (tail < oldLines.size - head &&
        tail < newLines.size - head &&
        oldLines[oldLines.size - 1 - tail] == newLines[newLines.size - 1 - tail]
    ) {
        tail++
    }
    val oldMid = oldLines.subList(head, oldLines.size - tail)
    val newMid = newLines.subList(head, newLines.size - tail)

    val raw = ArrayList<DiffLine>(oldLines.size + newLines.size)
    oldLines.subList(0, head).forEach { raw.add(DiffLine(DiffKind.EQUAL, it)) }
    raw.addAll(diffMiddle(oldMid, newMid))
    oldLines.subList(oldLines.size - tail, oldLines.size).forEach { raw.add(DiffLine(DiffKind.EQUAL, it)) }
    return pairInlineChanges(raw)
}

private fun diffMiddle(
    oldLines: List<String>,
    newLines: List<String>,
): List<DiffLine> {
    if (oldLines.size.toLong() * newLines.size > MAX_LINE_LCS_CELLS) {
        return oldLines.map { DiffLine(DiffKind.REMOVED, it) } + newLines.map { DiffLine(DiffKind.ADDED, it) }
    }
    val lcs = longestCommonSubsequence(oldLines, newLines)
    val result = ArrayList<DiffLine>(oldLines.size + newLines.size)
    var i = 0
    var j = 0
    var k = 0
    while (i < oldLines.size || j < newLines.size) {
        when {
            k < lcs.size && i < oldLines.size && oldLines[i] == lcs[k] &&
                j < newLines.size && newLines[j] == lcs[k] -> {
                result.add(DiffLine(DiffKind.EQUAL, lcs[k]))
                i++
                j++
                k++
            }
            i < oldLines.size && (k >= lcs.size || oldLines[i] != lcs[k]) -> {
                result.add(DiffLine(DiffKind.REMOVED, oldLines[i]))
                i++
            }
            j < newLines.size && (k >= lcs.size || newLines[j] != lcs[k]) -> {
                result.add(DiffLine(DiffKind.ADDED, newLines[j]))
                j++
            }
            else -> {
                // Should not happen; advance conservatively.
                if (i < oldLines.size) {
                    result.add(DiffLine(DiffKind.REMOVED, oldLines[i]))
                    i++
                }
                if (j < newLines.size) {
                    result.add(DiffLine(DiffKind.ADDED, newLines[j]))
                    j++
                }
            }
        }
    }
    return result
}

/**
 * For each hunk of consecutive REMOVED lines followed by ADDED lines, pairs them
 * positionally and attaches word-level [DiffLine.segments] when the pair shares content.
 */
private fun pairInlineChanges(lines: List<DiffLine>): List<DiffLine> {
    val out = ArrayList<DiffLine>(lines.size)
    var idx = 0
    while (idx < lines.size) {
        if (lines[idx].kind != DiffKind.REMOVED) {
            out.add(lines[idx])
            idx++
            continue
        }
        val removedStart = idx
        while (idx < lines.size && lines[idx].kind == DiffKind.REMOVED) idx++
        val addedStart = idx
        while (idx < lines.size && lines[idx].kind == DiffKind.ADDED) idx++
        val removed = lines.subList(removedStart, addedStart)
        val added = lines.subList(addedStart, idx)
        val pairs = minOf(removed.size, added.size)
        val removedOut = removed.toMutableList()
        val addedOut = added.toMutableList()
        for (p in 0 until pairs) {
            val inline = computeInlineDiff(removed[p].text, added[p].text) ?: continue
            removedOut[p] = removed[p].copy(segments = inline.first)
            addedOut[p] = added[p].copy(segments = inline.second)
        }
        out.addAll(removedOut)
        out.addAll(addedOut)
    }
    return out
}

/**
 * Word-level diff of two lines. Returns (old segments, new segments), or null when the lines share
 * too little to be worth highlighting (then the whole line is shown as changed).
 */
internal fun computeInlineDiff(
    oldLine: String,
    newLine: String,
): Pair<List<DiffSegment>, List<DiffSegment>>? {
    val a = inlineToken.findAll(oldLine).map { it.value }.toList()
    val b = inlineToken.findAll(newLine).map { it.value }.toList()
    if (a.isEmpty() || b.isEmpty()) return null

    val keepA: BooleanArray
    val keepB: BooleanArray
    if (a.size.toLong() * b.size > MAX_INLINE_LCS_CELLS) {
        keepA = BooleanArray(a.size)
        keepB = BooleanArray(b.size)
        var pre = 0
        while (pre < a.size && pre < b.size && a[pre] == b[pre]) {
            keepA[pre] = true
            keepB[pre] = true
            pre++
        }
        var suf = 0
        while (suf < a.size - pre && suf < b.size - pre && a[a.size - 1 - suf] == b[b.size - 1 - suf]) {
            keepA[a.size - 1 - suf] = true
            keepB[b.size - 1 - suf] = true
            suf++
        }
    } else {
        val marks = lcsMarks(a, b)
        keepA = marks.first
        keepB = marks.second
    }

    val commonChars = a.indices.filter { keepA[it] }.sumOf { a[it].trim().length }
    val shorter = minOf(oldLine.trim().length, newLine.trim().length)
    if (shorter == 0 || commonChars * 2 < shorter) return null

    return toSegments(a, keepA) to toSegments(b, keepB)
}

private fun toSegments(
    tokens: List<String>,
    keep: BooleanArray,
): List<DiffSegment> {
    val out = ArrayList<DiffSegment>()
    val sb = StringBuilder()
    var currentChanged: Boolean? = null
    tokens.forEachIndexed { i, tok ->
        val changed = !keep[i]
        if (currentChanged != null && currentChanged != changed) {
            out.add(DiffSegment(sb.toString(), currentChanged == true))
            sb.clear()
        }
        currentChanged = changed
        sb.append(tok)
    }
    if (sb.isNotEmpty()) out.add(DiffSegment(sb.toString(), currentChanged == true))
    return out
}

private fun lcsMarks(
    a: List<String>,
    b: List<String>,
): Pair<BooleanArray, BooleanArray> {
    val dp = lcsTable(a, b)
    val keepA = BooleanArray(a.size)
    val keepB = BooleanArray(b.size)
    var i = a.size
    var j = b.size
    while (i > 0 && j > 0) {
        when {
            a[i - 1] == b[j - 1] -> {
                keepA[i - 1] = true
                keepB[j - 1] = true
                i--
                j--
            }
            dp[i - 1][j] >= dp[i][j - 1] -> i--
            else -> j--
        }
    }
    return keepA to keepB
}

private fun lcsTable(
    a: List<String>,
    b: List<String>,
): Array<IntArray> {
    val dp = Array(a.size + 1) { IntArray(b.size + 1) }
    for (i in 1..a.size) {
        for (j in 1..b.size) {
            dp[i][j] =
                if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1] + 1
                } else {
                    maxOf(dp[i - 1][j], dp[i][j - 1])
                }
        }
    }
    return dp
}

internal fun longestCommonSubsequence(
    a: List<String>,
    b: List<String>,
): List<String> {
    val dp = lcsTable(a, b)
    val out = ArrayList<String>()
    var i = a.size
    var j = b.size
    while (i > 0 && j > 0) {
        when {
            a[i - 1] == b[j - 1] -> {
                out.add(a[i - 1])
                i--
                j--
            }
            dp[i - 1][j] >= dp[i][j - 1] -> i--
            else -> j--
        }
    }
    out.reverse()
    return out
}
