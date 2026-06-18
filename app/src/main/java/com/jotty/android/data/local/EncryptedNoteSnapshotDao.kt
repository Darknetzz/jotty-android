package com.jotty.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface EncryptedNoteSnapshotDao {
    @Insert
    suspend fun insert(snapshot: EncryptedNoteSnapshotEntity): Long

    @Query(
        """
        SELECT * FROM encrypted_note_snapshots
        WHERE noteId = :noteId AND instanceId = :instanceId
        ORDER BY savedAtEpochMs DESC
        """,
    )
    suspend fun listForNote(
        noteId: String,
        instanceId: String,
    ): List<EncryptedNoteSnapshotEntity>

    @Query(
        """
        SELECT * FROM encrypted_note_snapshots
        WHERE noteId = :noteId AND instanceId = :instanceId
        ORDER BY savedAtEpochMs DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestForNote(
        noteId: String,
        instanceId: String,
    ): EncryptedNoteSnapshotEntity?

    @Query("SELECT * FROM encrypted_note_snapshots WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): EncryptedNoteSnapshotEntity?

    @Query("DELETE FROM encrypted_note_snapshots WHERE id = :id")
    suspend fun deleteById(id: Long)
}
