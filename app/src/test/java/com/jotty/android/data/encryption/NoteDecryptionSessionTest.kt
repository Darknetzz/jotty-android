package com.jotty.android.data.encryption

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class NoteDecryptionSessionTest {

    @After
    fun tearDown() {
        NoteDecryptionSession.clear()
    }

    @Test
    fun `get returns null for unknown note`() {
        assertNull(NoteDecryptionSession.get("unknown-id"))
    }

    @Test
    fun `put and get round-trip`() {
        NoteDecryptionSession.put("note-1", "Decrypted content")
        assertEquals("Decrypted content", NoteDecryptionSession.get("note-1"))
    }

    @Test
    fun `put overwrites existing entry`() {
        NoteDecryptionSession.put("note-1", "First")
        NoteDecryptionSession.put("note-1", "Second")
        assertEquals("Second", NoteDecryptionSession.get("note-1"))
    }

    @Test
    fun `remove deletes entry`() {
        NoteDecryptionSession.put("note-1", "Content")
        NoteDecryptionSession.remove("note-1")
        assertNull(NoteDecryptionSession.get("note-1"))
    }

    @Test
    fun `remove on missing key is safe`() {
        NoteDecryptionSession.remove("does-not-exist") // should not throw
    }

    @Test
    fun `clear removes all entries`() {
        NoteDecryptionSession.put("a", "1")
        NoteDecryptionSession.put("b", "2")
        NoteDecryptionSession.clear()
        assertNull(NoteDecryptionSession.get("a"))
        assertNull(NoteDecryptionSession.get("b"))
    }
}
