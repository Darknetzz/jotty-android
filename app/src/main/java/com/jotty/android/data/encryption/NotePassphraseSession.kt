package com.jotty.android.data.encryption

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cache of passphrases used to decrypt notes in the current app session.
 * Cleared when the process is killed. Used to re-encrypt edited notes without asking again.
 * Thread-safe via [ConcurrentHashMap].
 */
object NotePassphraseSession {
    private val cache = ConcurrentHashMap<String, CharArray>()

    fun has(noteId: String): Boolean = cache.containsKey(noteId)

    /** Returns a copy of the stored passphrase, or null if none. Caller must [clearPassphrase] when done. */
    fun get(noteId: String): CharArray? = cache[noteId]?.copyOf()

    fun put(
        noteId: String,
        passChars: CharArray,
    ) {
        cache.remove(noteId)?.clearPassphrase()
        cache[noteId] = passChars.copyOf()
    }

    fun remove(noteId: String) {
        cache.remove(noteId)?.clearPassphrase()
    }

    fun clear() {
        cache.values.forEach { it.clearPassphrase() }
        cache.clear()
    }
}
