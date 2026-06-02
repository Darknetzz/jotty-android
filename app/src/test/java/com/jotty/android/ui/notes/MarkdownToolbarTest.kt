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
    fun toggleWrapSelection_removesMarkersOnSecondTap() {
        val value = TextFieldValue("hello **world**", TextRange(6, 15))
        val updated = toggleWrapSelection(value, "**")
        assertEquals("hello world", updated.text)
        assertEquals(TextRange(6, 11), updated.selection)
    }

    @Test
    fun toggleWrapSelection_removesEmptyMarkerPair() {
        val value = TextFieldValue("hello ****", TextRange(8))
        val updated = toggleWrapSelection(value, "**")
        assertEquals("hello ", updated.text)
        assertEquals(TextRange(6), updated.selection)
    }

    @Test
    fun prefixLineWithAutoIndex_addsNumberPrefix() {
        val value = TextFieldValue("first line\nsecond line", TextRange(12))
        val updated = prefixLineWithAutoIndex(value)
        assertEquals("first line\n1. second line", updated.text)
    }

    @Test
    fun toggleBulletLine_removesPrefixOnSecondTap() {
        val value = TextFieldValue("- item one", TextRange(3))
        val updated = toggleBulletLine(value)
        assertEquals("item one", updated.text)
    }

    @Test
    fun toggleTaskLine_removesPrefixOnSecondTap() {
        val value = TextFieldValue("- [ ] task", TextRange(4))
        val updated = toggleTaskLine(value)
        assertEquals("task", updated.text)
    }

    @Test
    fun continueMarkdownBlockOnEnter_continuesBulletList() {
        val previous = TextFieldValue("- first", TextRange(7))
        val updated = TextFieldValue("- first\n", TextRange(8))
        val result = continueMarkdownBlockOnEnter(previous, updated)
        assertEquals("- first\n- ", result.text)
        assertEquals(TextRange(10), result.selection)
    }

    @Test
    fun continueMarkdownBlockOnEnter_continuesTaskList() {
        val previous = TextFieldValue("- [ ] task", TextRange(10))
        val updated = TextFieldValue("- [ ] task\n", TextRange(11))
        val result = continueMarkdownBlockOnEnter(previous, updated)
        assertEquals("- [ ] task\n- [ ] ", result.text)
        assertEquals(TextRange(17), result.selection)
    }

    @Test
    fun continueMarkdownBlockOnEnter_continuesNumberedList() {
        val previous = TextFieldValue("1. first", TextRange(8))
        val updated = TextFieldValue("1. first\n", TextRange(9))
        val result = continueMarkdownBlockOnEnter(previous, updated)
        assertEquals("1. first\n2. ", result.text)
        assertEquals(TextRange(12), result.selection)
    }

    @Test
    fun continueMarkdownBlockOnEnter_exitsEmptyBulletItem() {
        val previous = TextFieldValue("- ", TextRange(2))
        val updated = TextFieldValue("- \n", TextRange(3))
        val result = continueMarkdownBlockOnEnter(previous, updated)
        assertEquals("\n", result.text)
        assertEquals(TextRange(1), result.selection)
    }

    @Test
    fun continueMarkdownBlockOnEnter_exitsEmptyTaskItem() {
        val previous = TextFieldValue("- [ ] ", TextRange(6))
        val updated = TextFieldValue("- [ ] \n", TextRange(7))
        val result = continueMarkdownBlockOnEnter(previous, updated)
        assertEquals("\n", result.text)
        assertEquals(TextRange(1), result.selection)
    }

    @Test
    fun insertLink_insertsTemplate() {
        val value = TextFieldValue("See docs", TextRange(4, 8))
        val updated = insertLink(value)
        assertEquals("See [docs](url)", updated.text)
    }

    @Test
    fun toggleLink_removesLinkOnSecondTap() {
        val value = TextFieldValue("[docs](url)", TextRange(0, 12))
        val updated = toggleLink(value)
        assertEquals("docs", updated.text)
        assertEquals(TextRange(0, 4), updated.selection)
    }
}
