package com.jotty.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `formatNoteDate returns date part for ISO string`() {
        assertEquals("2026-02-08", formatNoteDate("2026-02-08T12:30:00Z"))
        assertEquals("2026-02-08", formatNoteDate("2026-02-08T00:00:00+00:00"))
    }

    @Test
    fun `formatNoteDate returns first 10 chars when no T`() {
        assertEquals("2026-02-08", formatNoteDate("2026-02-08"))
        assertEquals("2026-02-0", formatNoteDate("2026-02-0"))
    }

    @Test
    fun `formatNoteDate handles short or empty input`() {
        assertEquals("", formatNoteDate(""))
        assertEquals("ab", formatNoteDate("ab"))
    }

    @Test
    fun `stripInvisibleFromEdges removes BOM and zero-width from edges`() {
        assertEquals("My secretest secrets", stripInvisibleFromEdges("\uFEFF\uFEFFMy secretest secrets"))
        assertEquals("hello", stripInvisibleFromEdges("\u200Bhello\u200C"))
        assertEquals("mid", stripInvisibleFromEdges("\uFEFFmid\u2060"))
        assertEquals("unchanged", stripInvisibleFromEdges("unchanged"))
        assertEquals("", stripInvisibleFromEdges("\uFEFF\u200B"))
    }
}
