package com.jotty.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownHtmlTablesTest {
    @Test
    fun `convertHtmlFontFamilySpans unwraps jotty web font span`() {
        val html =
            """<span style="font-family: 'Andale Mono', monospace">**My secretest secrets**</span>"""
        val result = convertHtmlFontFamilySpans(html)
        assertEquals("**My secretest secrets**", result)
    }

    @Test
    fun `convertHtmlFontFamilySpans removes invisible chars before span when combined with strip`() {
        val html =
            """\uFEFF<span style="font-family: 'Andale Mono', monospace">text</span>"""
                .replace("\\uFEFF", "\uFEFF")
        val result = convertHtmlFontFamilySpans(stripInvisibleUnicode(html))
        assertEquals("text", result)
    }

    @Test
    fun `convertHtmlColorSpans converts span with hex color`() {
        val html = """Start <span style="color: #22aa55;">green text</span> end"""
        val result = convertHtmlColorSpans(html)
        assertEquals("""Start <font color="#22aa55">green text</font> end""", result)
    }

    @Test
    fun `convertHtmlColorSpans converts span with rgb color`() {
        val html = """<span style="font-weight:700; color: rgb(34, 170, 85);">text</span>"""
        val result = convertHtmlColorSpans(html)
        assertEquals("""<font color="#22aa55">text</font>""", result)
    }

    @Test
    fun `convertHtmlColorSpans leaves spans without color unchanged`() {
        val html = """before <span style="font-weight:700">text</span> after"""
        val result = convertHtmlColorSpans(html)
        assertEquals(html, result)
    }

    @Test
    fun `convertHtmlImagesToMarkdown leaves markdown without images unchanged`() {
        val md = "Hello ![world](https://example.com/x.png)"
        assertEquals(md, convertHtmlImagesToMarkdown(md))
    }

    @Test
    fun `convertHtmlImagesToMarkdown converts html image to markdown`() {
        val html = """Before <img src="/api/image/123" alt="Sample image" /> after"""
        val result = convertHtmlImagesToMarkdown(html)
        assertEquals("Before ![Sample image](/api/image/123) after", result)
    }

    @Test
    fun `convertHtmlImagesToMarkdown keeps tag when src is missing`() {
        val html = """Text <img alt="No src" /> tail"""
        val result = convertHtmlImagesToMarkdown(html)
        assertEquals(html, result)
    }

    @Test
    fun `convertHtmlImagesToMarkdown converts figure with image`() {
        val html =
            """
            Intro
            <figure class="image">
              <img src="https://example.com/figure.png" alt="Figure image">
              <figcaption>Caption</figcaption>
            </figure>
            Outro
            """.trimIndent()
        val result = convertHtmlImagesToMarkdown(html)
        assertTrue(result.contains("![Figure image](https://example.com/figure.png)"))
        assertFalse(result.contains("<figure"))
    }

    @Test
    fun `convertHtmlImagesToMarkdown converts picture with source and image`() {
        val html =
            """
            <picture>
              <source srcset="https://example.com/image.webp" type="image/webp" />
              <img src='https://example.com/image.jpg' alt='Picture image'>
            </picture>
            """.trimIndent()
        val result = convertHtmlImagesToMarkdown(html)
        assertEquals("![Picture image](https://example.com/image.jpg)", result)
    }

    @Test
    fun `convertHtmlImagesToMarkdown supports unquoted src attribute`() {
        val html = """<img src=/api/image/456 alt="Inline image">"""
        val result = convertHtmlImagesToMarkdown(html)
        assertEquals("![Inline image](/api/image/456)", result)
    }

    @Test
    fun `convertHtmlTablesToGfm leaves markdown without tables unchanged`() {
        val md =
            """
            | A | B |
            | - | - |
            | 1 | 2 |
            """.trimIndent()
        assertEquals(md, convertHtmlTablesToGfm(md))
    }

    @Test
    fun `convertHtmlTablesToGfm converts jotty-style html table`() {
        val html =
            """
            ## Cheating shit players
            <table>
              <tbody>
                <tr>
                  <th><p>Date</p></th>
                  <th><p>Name</p></th>
                  <th><p>Description</p></th>
                </tr>
                <tr>
                  <td><p>2026-03-13</p></td>
                  <td><p><a href="https://example.com">Player</a></p></td>
                  <td><p>Inferno, superobvious</p></td>
                </tr>
              </tbody>
            </table>
            """.trimIndent()
        val result = convertHtmlTablesToGfm(html)
        assertTrue(result.contains("| Date | Name | Description |"))
        assertTrue(result.contains("| --- | --- | --- |"))
        assertTrue(result.contains("| 2026-03-13 | [Player](https://example.com) | Inferno, superobvious |"))
        assertFalse(result.contains("<table"))
    }

    @Test
    fun `convertHtmlTablesToGfm handles nested paragraph in cell`() {
        val html =
            """
            <table><tr><td><p>Cell with <strong>bold</strong></p></td></tr></table>
            """.trimIndent()
        val result = convertHtmlTablesToGfm(html)
        assertTrue(result.contains("**bold**"))
        assertFalse(result.contains("<table"))
    }

    @Test
    fun `prepareNoteContentForDisplay preserves html body on save path`() {
        val raw = "<table><tr><td>A</td></tr></table>"
        assertTrue(noteContentContainsRawHtml(raw))
        val displayed = prepareNoteContentForDisplay(raw, "https://jotty.example.com")
        assertFalse(displayed.contains("<table"))
    }
}
