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
    fun `ListDateFormat fromKey defaults to date`() {
        assertEquals(ListDateFormat.DATE, ListDateFormat.fromKey(null))
        assertEquals(ListDateFormat.RELATIVE, ListDateFormat.fromKey("relative"))
    }

    @Test
    fun `stripInvisibleUnicode removes BOM and zero-width throughout string`() {
        assertEquals(
            "<span>text</span>",
            stripInvisibleUnicode("\uFEFF\u200B<span>text</span>\uFEFF"),
        )
        assertEquals(
            "line one\n\nline two",
            stripInvisibleUnicode("line one\n\n\uFEFF\u200Bline two"),
        )
    }

    @Test
    fun `stripInvisibleFromEdges removes BOM and zero-width from edges`() {
        assertEquals("My secretest secrets", stripInvisibleFromEdges("\uFEFF\uFEFFMy secretest secrets"))
        assertEquals("hello", stripInvisibleFromEdges("\u200Bhello\u200C"))
        assertEquals("mid", stripInvisibleFromEdges("\uFEFFmid\u2060"))
        assertEquals("unchanged", stripInvisibleFromEdges("unchanged"))
        assertEquals("", stripInvisibleFromEdges("\uFEFF\u200B"))
    }

    @Test
    fun `decodeJsonUnicodeEscapes repairs evaluateJavascript html artifacts`() {
        val corrupted = """\u003Ctable style="width: 100%;"\u003E\u003Ctd\u003Ecell\u003C/td\u003E"""
        assertEquals("""<table style="width: 100%;"><td>cell</td>""", decodeJsonUnicodeEscapes(corrupted))
    }

    @Test
    fun `decodeJsonUnicodeEscapes no-op without escapes`() {
        assertEquals("<table><td>ok</td></table>", decodeJsonUnicodeEscapes("<table><td>ok</td></table>"))
    }
}
