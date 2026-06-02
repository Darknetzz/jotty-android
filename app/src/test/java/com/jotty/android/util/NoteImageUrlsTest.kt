package com.jotty.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteImageUrlsTest {
    @Test
    fun resolveNoteImageUrl_leavesAbsoluteUrlsUnchanged() {
        val url = "https://cdn.example.com/pic.png"
        assertEquals(url, resolveNoteImageUrl(url, "https://jotty.example.com"))
    }

    @Test
    fun resolveNoteImageUrl_resolvesRootRelativePath() {
        assertEquals(
            "https://jotty.example.com/api/image/1",
            resolveNoteImageUrl("/api/image/1", "https://jotty.example.com"),
        )
    }

    @Test
    fun resolveNoteImageUrl_resolvesProtocolRelativePath() {
        assertEquals(
            "https://jotty.example.com/static/x.png",
            resolveNoteImageUrl("//jotty.example.com/static/x.png", "https://jotty.example.com"),
        )
    }

    @Test
    fun resolveNoteImageUrl_resolvesBareRelativePath() {
        assertEquals(
            "https://jotty.example.com/api/files/a.png",
            resolveNoteImageUrl("api/files/a.png", "https://jotty.example.com/"),
        )
    }

    @Test
    fun resolveNoteImageUrlsInMarkdown_rewritesMarkdownImageLinks() {
        val input = "See ![diagram](/api/image/9) here."
        val expected = "See ![diagram](https://jotty.example.com/api/image/9) here."
        assertEquals(expected, resolveNoteImageUrlsInMarkdown(input, "https://jotty.example.com"))
    }
}
