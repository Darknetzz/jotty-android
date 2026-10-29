package com.jotty.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JottyItemLinksTest {
    @Test
    fun rewritesExplicitNoteAndChecklistPaths() {
        val input =
            """
            See [A](/note/aaa-111) and [B](/checklist/bbb-222) plus legacy [C](/jotty/ccc-333).
            """.trimIndent()
        val out = rewriteJottyItemLinksForDisplay(input, emptyMap())
        assertTrue(out.contains("[A](jotty-item://note/aaa-111)"))
        assertTrue(out.contains("[B](jotty-item://checklist/bbb-222)"))
        assertTrue(out.contains("[C](jotty-item://note/ccc-333)"))
    }

    @Test
    fun rewritesResolvedWikilinkWithAlias() {
        val map =
            mapOf(
                "project plan" to JottyItemRef(JottyItemRef.Type.NOTE, "note-1"),
            )
        val out = rewriteJottyItemLinksForDisplay("Go to [[Project Plan|Plans]] now.", map)
        assertEquals("Go to [Plans](jotty-item://note/note-1) now.", out)
    }

    @Test
    fun unresolvedWikilinkBecomesPlainText() {
        val out = rewriteJottyItemLinksForDisplay("See [[Missing Note]] here.", emptyMap())
        assertEquals("See Missing Note here.", out)
    }

    @Test
    fun leavesCodeFencesAndInlineCodeIntact() {
        val input =
            """
            Before [[Real Note]]
            ```
            [[Code Wiki]]
            [x](/note/inside-fence)
            ```
            Inline `[[also code]]` and [y](/note/live).
            """.trimIndent()
        val map = mapOf("real note" to JottyItemRef(JottyItemRef.Type.NOTE, "n1"))
        val out = rewriteJottyItemLinksForDisplay(input, map)
        assertTrue(out.contains("[Real Note](jotty-item://note/n1)"))
        assertTrue(out.contains("[[Code Wiki]]"))
        assertTrue(out.contains("[x](/note/inside-fence)"))
        assertTrue(out.contains("`[[also code]]`"))
        assertTrue(out.contains("[y](jotty-item://note/live)"))
    }

    @Test
    fun parsesJottyItemUri() {
        assertEquals(
            JottyItemRef(JottyItemRef.Type.NOTE, "abc"),
            parseJottyItemUri("jotty-item://note/abc"),
        )
        assertEquals(
            JottyItemRef(JottyItemRef.Type.CHECKLIST, "xyz"),
            parseJottyItemUri("jotty-item://checklist/xyz"),
        )
        assertNull(parseJottyItemUri("https://example.com"))
    }

    @Test
    fun uniqueLocalTitleMapsWhenRelationsEmpty() {
        val map =
            buildWikilinkTitleMap(
                relationLinks = emptyList(),
                localNotes = listOf("Alpha" to "id-a", "Beta" to "id-b"),
            )
        assertEquals(JottyItemRef(JottyItemRef.Type.NOTE, "id-a"), map["alpha"])
    }

    @Test
    fun ambiguousLocalTitleIsSkipped() {
        val map =
            buildWikilinkTitleMap(
                relationLinks = emptyList(),
                localNotes = listOf("Dup" to "id-1", "Dup" to "id-2"),
            )
        assertNull(map["dup"])
    }
}
