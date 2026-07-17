package com.jotty.android.util

/** One line in a unified text diff. */
data class DiffLine(
    val kind: DiffKind,
    val text: String,
)

enum class DiffKind {
    EQUAL,
    ADDED,
    REMOVED,
}

/**
 * Simple line-based LCS diff for pending-sync previews (no external dependency).
 */
fun computeLineDiff(
    oldText: String,
    newText: String,
): List<DiffLine> {
    val oldLines = oldText.replace("\r\n", "\n").split('\n')
    val newLines = newText.replace("\r\n", "\n").split('\n')
    if (oldLines == newLines) {
        return oldLines.map { DiffLine(DiffKind.EQUAL, it) }
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

internal fun longestCommonSubsequence(
    a: List<String>,
    b: List<String>,
): List<String> {
    val n = a.size
    val m = b.size
    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in 1..n) {
        for (j in 1..m) {
            dp[i][j] =
                if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1] + 1
                } else {
                    maxOf(dp[i - 1][j], dp[i][j - 1])
                }
        }
    }
    val out = ArrayList<String>()
    var i = n
    var j = m
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
