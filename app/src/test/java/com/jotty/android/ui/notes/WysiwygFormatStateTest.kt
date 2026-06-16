package com.jotty.android.ui.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WysiwygFormatStateTest {
    @Test
    fun parseWysiwygFormatStateJson_readsAllFlags() {
        val json =
            """
            {
              "bold": true,
              "italic": true,
              "underline": false,
              "strikeThrough": true,
              "unorderedList": false,
              "orderedList": true,
              "heading": true,
              "blockquote": false,
              "code": true,
              "link": false
            }
            """.trimIndent()

        val state = parseWysiwygFormatStateJson(json)

        assertTrue(state.bold)
        assertTrue(state.italic)
        assertFalse(state.underline)
        assertTrue(state.strikeThrough)
        assertFalse(state.unorderedList)
        assertTrue(state.orderedList)
        assertTrue(state.heading)
        assertFalse(state.blockquote)
        assertTrue(state.code)
        assertFalse(state.link)
    }

    @Test
    fun parseWysiwygFormatStateJson_invalidJson_returnsDefaults() {
        val state = parseWysiwygFormatStateJson("not-json")
        assertEquals(WysiwygFormatState(), state)
    }

    @Test
    fun parseWebViewJsonResult_unwrapsQuotedJson() {
        val raw = "\"{\\\"bold\\\":true,\\\"italic\\\":false}\""
        assertEquals("{\"bold\":true,\"italic\":false}", parseWebViewJsonResult(raw))
    }
}
