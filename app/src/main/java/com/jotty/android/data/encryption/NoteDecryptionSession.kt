package com.jotty.android.data.encryption

/**
 * In-memory cache of decrypted note content for the current app session.
 * Cleared when the process is killed. Used so reopening the same note doesn't ask for passphrase again.
 */
object NoteDecryptionSession {
    private val cache = mutableMapOf<String, String>()

    fun get(noteId: String): String? = cache[noteId]
    fun put(noteId: String, plainText: String) { cache[noteId] = plainText }
    fun remove(noteId: String) { cache.remove(noteId) }
    fun clear() { cache.clear() }
}
