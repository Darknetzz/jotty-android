package com.jotty.android.ui.notes

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownToolbarTest {
    @Test
    fun wrapSelection_wrapsSelectedText() {
        val value = TextFieldValue("hello world", TextRange(6, 11))
        val updated = wrapSelection(value, "**")
        assertEquals("hello **world**", updated.text)
    }

    @Test
    fun prefixLineWithAutoIndex_addsNumberPrefix() {
        val value = TextFieldValue("first line\nsecond line", TextRange(12))
        val updated = prefixLineWithAutoIndex(value)
        assertEquals("first line\n1. second line", updated.text)
    }

    @Test
    fun insertLink_insertsTemplate() {
        val value = TextFieldValue("See docs", TextRange(4, 8))
        val updated = insertLink(value)
        assertEquals("See [docs](url)", updated.text)
    }
}
