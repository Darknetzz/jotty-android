package com.jotty.android.ui.notes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WysiwygEditorDocumentTest {
    @Test
    fun `buildWysiwygEditorDocument embeds note html in page`() {
        val doc =
            buildWysiwygEditorDocument(
                bodyHtml = "<h1>test</h1><table><tr><td>Cell</td></tr></table>",
                backgroundColor = 0xFFFFFFFF.toInt(),
                textColor = 0xFF000000.toInt(),
                borderColor = 0xFFCCCCCC.toInt(),
            )
        assertTrue(doc.contains("INITIAL_CONTENT"))
        assertTrue(doc.contains("<h1>test</h1>"))
        assertTrue(doc.contains("<table>"))
        assertTrue(doc.contains("contenteditable"))
        assertTrue(doc.contains("setEditorTheme"))
    }

    @Test
    fun `buildWysiwygEditorDocument uses placeholder for blank body`() {
        val doc =
            buildWysiwygEditorDocument(
                bodyHtml = "",
                backgroundColor = 0xFFFFFFFF.toInt(),
                textColor = 0xFF000000.toInt(),
                borderColor = 0xFFCCCCCC.toInt(),
            )
        assertTrue(doc.contains("<p><br></p>"))
        assertFalse(doc.contains("INITIAL_CONTENT = \"\""))
    }
}
