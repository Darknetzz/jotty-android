package com.jotty.android.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local ciphertext backup taken immediately before the app replaces an encrypted note on the server.
 * Lets users restore the previous encrypted copy if a save produces unreadable ciphertext.
 */
@Entity(
    tableName = "encrypted_note_snapshots",
    indices = [Index("noteId"), Index("instanceId")],
)
data class EncryptedNoteSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: String,
    val instanceId: String,
    val title: String,
    /** Encrypted note content as stored (body-only JSON or frontmatter-wrapped). */
    val content: String,
    val savedAtEpochMs: Long,
)

data class EncryptedNoteSnapshot(
    val id: Long,
    val noteId: String,
    val title: String,
    val content: String,
    val savedAtEpochMs: Long,
)

fun EncryptedNoteSnapshotEntity.toSnapshot(): EncryptedNoteSnapshot =
    EncryptedNoteSnapshot(
        id = id,
        noteId = noteId,
        title = title,
        content = content,
        savedAtEpochMs = savedAtEpochMs,
    )
