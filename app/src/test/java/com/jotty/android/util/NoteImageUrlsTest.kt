package com.jotty.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun resolveNoteImageUrl_resolvesRootRelativePathAgainstSubpathBase() {
        assertEquals(
            "https://example.com/api/image/user/photo.png",
            resolveNoteImageUrl("/api/image/user/photo.png", "https://example.com/jotty"),
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
    fun resolveNoteImageUrl_unwrapsAngleBracketMarkdownUrl() {
        assertEquals(
            "https://jotty.example.com/api/image/a.png",
            resolveNoteImageUrl("<https://jotty.example.com/api/image/a.png>", "https://jotty.example.com"),
        )
    }

    @Test
    fun resolveNoteImageUrl_stripsMarkdownTitleFromUrl() {
        assertEquals(
            "https://jotty.example.com/api/image/a.png",
            resolveNoteImageUrl("https://jotty.example.com/api/image/a.png \"caption\"", "https://jotty.example.com"),
        )
    }

    @Test
    fun rewriteJottyMediaHostToInstance_rewritesLanHostToConfiguredInstance() {
        assertEquals(
            "http://jotty.local:3000/api/image/me/photo.png",
            rewriteJottyMediaHostToInstance(
                "http://192.168.1.50:3000/api/image/me/photo.png",
                "http://jotty.local:3000",
            ),
        )
    }

    @Test
    fun rewriteJottyMediaHostToInstance_leavesExternalUrlsUnchanged() {
        val url = "https://cdn.example.com/assets/pic.png"
        assertEquals(url, rewriteJottyMediaHostToInstance(url, "https://jotty.example.com"))
    }

    @Test
    fun resolveNoteImageUrlsInMarkdown_rewritesMarkdownImageLinks() {
        val input = "See ![diagram](/api/image/9) here."
        val expected = "See ![diagram](https://jotty.example.com/api/image/9) here."
        assertEquals(expected, resolveNoteImageUrlsInMarkdown(input, "https://jotty.example.com"))
    }

    @Test
    fun resolveNoteImageUrlsInHtml_rewritesImgSrc() {
        val html = """Text <img src="/api/image/alice/photo.png" alt="x" /> end"""
        val expected = """Text <img src="https://jotty.example.com/api/image/alice/photo.png" alt="x" /> end"""
        assertEquals(expected, resolveNoteImageUrlsInHtml(html, "https://jotty.example.com"))
    }

    @Test
    fun prepareNoteContentForDisplay_resolvesHtmlImageBeforeMarkdownConversion() {
        val html = """<img src="/api/image/bob/cat.png" alt="Cat">"""
        val result = prepareNoteContentForDisplay(html, "https://notes.example.com")
        assertEquals("![Cat](https://notes.example.com/api/image/bob/cat.png)", result)
    }

    @Test
    fun noteContainsJottyMediaUrls_detectsMarkdownAndHtml() {
        assertTrue(noteContainsJottyMediaUrls("![x](/api/image/1)"))
        assertTrue(noteContainsJottyMediaUrls("""<img src="/api/image/a.png">"""))
        assertTrue(noteContainsJottyMediaUrls("file at /api/file/doc.pdf"))
        assertFalse(noteContainsJottyMediaUrls("![x](https://cdn.example.com/p.png)"))
        assertFalse(noteContainsJottyMediaUrls("plain text"))
    }
}
