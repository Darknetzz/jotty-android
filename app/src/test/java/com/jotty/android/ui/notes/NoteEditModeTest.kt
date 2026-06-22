package com.jotty.android.ui.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteEditModeTest {
    @Test
    fun resolveInitialNoteEditMode_respectsRichEditorToggle() {
        assertEquals(NoteEditMode.Markdown, resolveInitialNoteEditMode(richEditorEnabled = false, defaultModeKey = "visual"))
        assertEquals(NoteEditMode.Visual, resolveInitialNoteEditMode(richEditorEnabled = true, defaultModeKey = "visual"))
        assertEquals(NoteEditMode.Markdown, resolveInitialNoteEditMode(richEditorEnabled = true, defaultModeKey = "markdown"))
    }
}
