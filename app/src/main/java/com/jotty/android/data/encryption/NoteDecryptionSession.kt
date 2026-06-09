package com.jotty.android.data.encryption

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cache of decrypted note content for the current app session.
 * Cleared when the process is killed. Used so reopening the same note doesn't ask for passphrase again.
 * Thread-safe via [ConcurrentHashMap].
 */
object NoteDecryptionSession {
    private val cache = ConcurrentHashMap<String, String>()
    private val _revision = MutableStateFlow(0)

    /** Bumps when any note is unlocked, locked, or the cache is cleared — for UI refresh. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun get(noteId: String): String? = cache[noteId]

    fun isUnlocked(noteId: String): Boolean = !get(noteId).isNullOrBlank()

    fun put(
        noteId: String,
        plainText: String,
    ) {
        cache[noteId] = plainText
        bumpRevision()
    }

    fun remove(noteId: String) {
        if (cache.remove(noteId) != null) {
            bumpRevision()
        }
    }

    fun clear() {
        if (cache.isNotEmpty()) {
            cache.clear()
            bumpRevision()
        }
    }

    private fun bumpRevision() {
        _revision.value++
    }
}
