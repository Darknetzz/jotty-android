package com.jotty.android.data.local

import com.jotty.android.data.encryption.NoteEncryption
import com.jotty.android.data.encryption.ParsedNoteContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stores the last few note copies per note on this device before the app overwrites them on save.
 */
class NoteSnapshotRepository(
    database: JottyDatabase,
    private val instanceId: String,
) {
    private val dao = database.noteSnapshotDao()

    suspend fun listForNote(noteId: String): List<NoteSnapshot> =
        withContext(Dispatchers.IO) {
            dao.listForNote(noteId, instanceId).map { it.toSnapshot() }
        }

    suspend fun getById(id: Long): NoteSnapshot? =
        withContext(Dispatchers.IO) {
            dao.getById(id)?.toSnapshot()
        }

    /**
     * Saves [content] when it differs from the latest local backup. No-op when [enabled] is false
     * or the consecutive content is unchanged.
     */
    suspend fun saveBeforeUpdate(
        noteId: String,
        title: String,
        content: String,
        enabled: Boolean = true,
    ) = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext
        val key = contentSnapshotKey(content)
        val latest = dao.getLatestForNote(noteId, instanceId)
        if (latest != null && contentSnapshotKey(latest.content) == key) return@withContext
        dao.insert(
            NoteSnapshotEntity(
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

    internal fun contentSnapshotKey(content: String): String =
        when (val parsed = NoteEncryption.parse(content)) {
            is ParsedNoteContent.Encrypted -> parsed.encryptedBody
            else -> content
        }

    companion object {
        const val MAX_SNAPSHOTS_PER_NOTE = 5
    }
}
