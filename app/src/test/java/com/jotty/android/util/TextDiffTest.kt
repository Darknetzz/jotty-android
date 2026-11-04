package com.jotty.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun computeLineDiff_htmlSingleLineAppend_onlyShowsNewBlocks() {
        val server = "Hov1 svensk<div><div>Viva La Bam</div><div>Boyka</div></div>"
        val local = server + "<div>Snowfall</div><div>House of Cards</div>"
        val lines = computeLineDiff(server, local)
        assertEquals(
            listOf("<div>Snowfall</div>", "<div>House of Cards</div>"),
            lines.filter { it.kind == DiffKind.ADDED }.map { it.text },
        )
        assertTrue(lines.none { it.kind == DiffKind.REMOVED })
    }

    @Test
    fun diffDisplayLines_splitsHtmlBlocks() {
        assertEquals(
            listOf("a", "<div>b</div>", "<p class=\"x\">c</p>", "d<br>", "e"),
            diffDisplayLines("a<div>b</div><p class=\"x\">c</p>d<br>e"),
        )
        assertEquals(listOf("plain", "", "text"), diffDisplayLines("plain\n\ntext"))
    }

    @Test
    fun computeLineDiff_editedLine_hasWordSegments() {
        val lines = computeLineDiff("buy milk and eggs", "buy oat milk and eggs")
        val added = lines.single { it.kind == DiffKind.ADDED }
        val removed = lines.single { it.kind == DiffKind.REMOVED }
        assertNotNull(added.segments)
        val addedSegs = added.segments.orEmpty()
        assertEquals(listOf("oat"), addedSegs.filter { it.changed }.map { it.text.trim() })
        assertTrue(removed.segments.orEmpty().none { it.changed })
        assertEquals(added.text, addedSegs.joinToString("") { it.text })
    }

    @Test
    fun computeInlineDiff_unrelatedLines_returnsNull() {
        assertNull(computeInlineDiff("completely different", "nothing shared here"))
    }
}
