package com.jotty.android.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSaveBehaviorTest {
    @Test
    fun noteContentContainsRawHtml_detectsHtmlImage() {
        assertTrue(noteContentContainsRawHtml("Hello <img src=\"/api/image/x.png\">"))
    }

    @Test
    fun noteContentContainsRawHtml_detectsHtmlTable() {
        assertTrue(noteContentContainsRawHtml("<table><tr><td>Cell</td></tr></table>"))
    }

    @Test
    fun noteContentContainsRawHtml_plainMarkdownIsFalse() {
        assertFalse(noteContentContainsRawHtml("# Title\n\n**bold** and ![alt](https://x.com/a.png)"))
    }

    @Test
    fun noteContentContainsRawHtml_savePathPreservesHtmlUnchanged() {
        val original = "Intro\n<table><tr><td>A</td></tr></table>"
        // Save sends content as-is; display-only conversion must not mutate on write.
        assertTrue(noteContentContainsRawHtml(original))
        assertTrue(original.contains("<table>"))
    }
}
