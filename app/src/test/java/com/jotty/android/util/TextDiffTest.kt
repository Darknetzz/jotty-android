package com.jotty.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextDiffTest {
    @Test
    fun computeLineDiff_identical() {
        val lines = computeLineDiff("a\nb", "a\nb")
        assertEquals(listOf(DiffKind.EQUAL, DiffKind.EQUAL), lines.map { it.kind })
    }

    @Test
    fun computeLineDiff_additionAndRemoval() {
        val lines = computeLineDiff("keep\nold", "keep\nnew")
        assertTrue(lines.any { it.kind == DiffKind.REMOVED && it.text == "old" })
        assertTrue(lines.any { it.kind == DiffKind.ADDED && it.text == "new" })
        assertTrue(lines.any { it.kind == DiffKind.EQUAL && it.text == "keep" })
    }

    @Test
    fun longestCommonSubsequence_basic() {
        assertEquals(
            listOf("a", "c"),
            longestCommonSubsequence(listOf("a", "b", "c"), listOf("a", "c", "d")),
        )
    }
}
