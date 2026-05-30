package com.jotty.android.data.encryption

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class NotePassphraseSessionTest {
    @After
    fun tearDown() {
        NotePassphraseSession.clear()
    }

    @Test
    fun `get returns null for unknown note`() {
        assertNull(NotePassphraseSession.get("unknown-id"))
    }

    @Test
    fun `put and get round-trip`() {
        val pass = "secret-passphrase".toCharArray()
        NotePassphraseSession.put("note-1", pass)
        pass.fill('\u0000')
        val loaded = NotePassphraseSession.get("note-1")
        assertNotNull(loaded)
        assertArrayEquals("secret-passphrase".toCharArray(), loaded)
        loaded?.clearPassphrase()
    }

    @Test
    fun `get returns a copy`() {
        NotePassphraseSession.put("note-1", "first-passphrase".toCharArray())
        val first = NotePassphraseSession.get("note-1")
        first?.fill('x')
        val second = NotePassphraseSession.get("note-1")
        assertArrayEquals("first-passphrase".toCharArray(), second)
        first?.clearPassphrase()
        second?.clearPassphrase()
    }

    @Test
    fun `remove deletes entry`() {
        NotePassphraseSession.put("note-1", "pass".toCharArray())
        NotePassphraseSession.remove("note-1")
        assertNull(NotePassphraseSession.get("note-1"))
    }

    @Test
    fun `clear removes all entries`() {
        NotePassphraseSession.put("a", "1".toCharArray())
        NotePassphraseSession.put("b", "2".toCharArray())
        NotePassphraseSession.clear()
        assertNull(NotePassphraseSession.get("a"))
        assertNull(NotePassphraseSession.get("b"))
    }
}
