package com.jotty.android.data.local

import com.jotty.android.data.encryption.NoteEncryption
import com.jotty.android.data.encryption.ParsedNoteContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stores the last few encrypted ciphertext copies per note on this device before the app
 * overwrites them during re-encrypt/save.
 */
class EncryptedNoteSnapshotRepository(
    database: JottyDatabase,
    private val instanceId: String,
) {
    private val dao = database.encryptedNoteSnapshotDao()

    suspend fun listForNote(noteId: String): List<EncryptedNoteSnapshot> =
        withContext(Dispatchers.IO) {
            dao.listForNote(noteId, instanceId).map { it.toSnapshot() }
        }

    suspend fun getById(id: Long): EncryptedNoteSnapshot? =
        withContext(Dispatchers.IO) {
            dao.getById(id)?.toSnapshot()
        }

    /**
     * Saves [content] when it is encrypted ciphertext and differs from the latest local backup.
     * No-op for plaintext or duplicate consecutive ciphertext.
     */
    suspend fun saveBeforeEncrypt(
        noteId: String,
        title: String,
        content: String,
    ) = withContext(Dispatchers.IO) {
        if (NoteEncryption.parse(content) !is ParsedNoteContent.Encrypted) return@withContext
        val key = encryptedContentKey(content)
        val latest = dao.getLatestForNote(noteId, instanceId)
        if (latest != null && encryptedContentKey(latest.content) == key) return@withContext
        dao.insert(
            EncryptedNoteSnapshotEntity(
                noteId = noteId,
                instanceId = instanceId,
                title = title,
                content = content,
                savedAtEpochMs = System.currentTimeMillis(),
            ),
        )
        val all = dao.listForNote(noteId, instanceId)
        if (all.size > MAX_SNAPSHOTS_PER_NOTE) {
            all.drop(MAX_SNAPSHOTS_PER_NOTE).forEach { dao.deleteById(it.id) }
        }
    }

    internal fun encryptedContentKey(content: String): String =
        when (val parsed = NoteEncryption.parse(content)) {
            is ParsedNoteContent.Encrypted -> parsed.encryptedBody
            else -> content
        }

    companion object {
        const val MAX_SNAPSHOTS_PER_NOTE = 5
    }
}
