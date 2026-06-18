package com.jotty.android.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local note backup taken immediately before the app replaces a note on the server.
 * Lets users restore a prior copy if a save goes wrong (encrypted or plaintext).
 */
@Entity(
    tableName = "note_snapshots",
    indices = [Index("noteId"), Index("instanceId")],
)
data class NoteSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: String,
    val instanceId: String,
    val title: String,
    val content: String,
    val savedAtEpochMs: Long,
)

data class NoteSnapshot(
    val id: Long,
    val noteId: String,
    val title: String,
    val content: String,
    val savedAtEpochMs: Long,
)

fun NoteSnapshotEntity.toSnapshot(): NoteSnapshot =
    NoteSnapshot(
        id = id,
        noteId = noteId,
        title = title,
        content = content,
        savedAtEpochMs = savedAtEpochMs,
    )
