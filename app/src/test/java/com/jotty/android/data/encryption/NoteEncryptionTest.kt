package com.jotty.android.data.encryption

import org.junit.Assert.*
import org.junit.Test

class NoteEncryptionTest {

    // ── parse: plain content ────────────────────────────────────────────────

    @Test
    fun `parse returns Plain for empty string`() {
        val result = NoteEncryption.parse("")
        assertTrue(result is ParsedNoteContent.Plain)
    }

    @Test
    fun `parse returns Plain for regular text`() {
        val result = NoteEncryption.parse("Hello world")
        assertTrue(result is ParsedNoteContent.Plain)
    }

    @Test
    fun `parse returns Plain for markdown content`() {
        val md = "# Title\n\nSome **bold** and *italic* text."
        val result = NoteEncryption.parse(md)
        assertTrue(result is ParsedNoteContent.Plain)
    }

    @Test
    fun `parse returns Plain for frontmatter without encrypted flag`() {
        val content = """
            |---
            |title: My Note
            |category: General
            |---
            |Some content here.
        """.trimMargin()
        val result = NoteEncryption.parse(content)
        assertTrue(result is ParsedNoteContent.Plain)
    }

    // ── parse: encrypted frontmatter ────────────────────────────────────────

    @Test
    fun `parse detects xchacha encrypted frontmatter`() {
        val content = """
            |---
            |uuid: abc-123
            |title: Secret Note
            |encrypted: true
            |encryptionMethod: xchacha
            |---
            |{"alg":"xchacha20","salt":"dGVzdA==","nonce":"dGVzdA==","data":"dGVzdA=="}
        """.trimMargin()
        val result = NoteEncryption.parse(content)
        assertTrue(result is ParsedNoteContent.Encrypted)
        val enc = result as ParsedNoteContent.Encrypted
        assertEquals("xchacha", enc.encryptionMethod)
        assertEquals("abc-123", enc.frontmatter.uuid)
        assertEquals("Secret Note", enc.frontmatter.title)
        assertTrue(enc.frontmatter.encrypted)
    }

    @Test
    fun `parse detects pgp encrypted frontmatter`() {
        val content = """
            |---
            |encrypted: true
            |encryptionMethod: pgp
            |---
            |-----BEGIN PGP MESSAGE-----
            |...
        """.trimMargin()
        val result = NoteEncryption.parse(content)
        assertTrue(result is ParsedNoteContent.Encrypted)
        assertEquals("pgp", (result as ParsedNoteContent.Encrypted).encryptionMethod)
    }

    @Test
    fun `parse defaults encryption method to xchacha when not specified`() {
        val content = """
            |---
            |encrypted: true
            |---
            |some body
        """.trimMargin()
        val result = NoteEncryption.parse(content)
        assertTrue(result is ParsedNoteContent.Encrypted)
        assertEquals("xchacha", (result as ParsedNoteContent.Encrypted).encryptionMethod)
    }

    @Test
    fun `parse handles encrypted yes value`() {
        val content = """
            |---
            |encrypted: yes
            |encryptionMethod: xchacha
            |---
            |body
        """.trimMargin()
        val result = NoteEncryption.parse(content)
        assertTrue(result is ParsedNoteContent.Encrypted)
    }

    // ── parse: body-only xchacha JSON ───────────────────────────────────────

    @Test
    fun `parse detects body-only xchacha JSON`() {
        val json = """{"alg":"xchacha20","salt":"abc","nonce":"def","data":"ghi"}"""
        val result = NoteEncryption.parse(json)
        assertTrue(result is ParsedNoteContent.Encrypted)
        assertEquals("xchacha", (result as ParsedNoteContent.Encrypted).encryptionMethod)
    }

    @Test
    fun `parse returns Plain when JSON has alg but missing fields`() {
        val json = """{"alg":"xchacha20","salt":"abc"}"""
        val result = NoteEncryption.parse(json)
        assertTrue(result is ParsedNoteContent.Plain)
    }

    // ── isEncrypted ─────────────────────────────────────────────────────────

    @Test
    fun `isEncrypted returns false for plain text`() {
        assertFalse(NoteEncryption.isEncrypted("Hello"))
    }

    @Test
    fun `isEncrypted returns true for encrypted frontmatter`() {
        val content = "---\nencrypted: true\nencryptionMethod: xchacha\n---\nbody"
        assertTrue(NoteEncryption.isEncrypted(content))
    }

    // ── BOM handling ────────────────────────────────────────────────────────

    @Test
    fun `parse handles BOM prefix`() {
        val content = "\uFEFF---\nencrypted: true\n---\nbody"
        val result = NoteEncryption.parse(content)
        assertTrue(result is ParsedNoteContent.Encrypted)
    }
}
