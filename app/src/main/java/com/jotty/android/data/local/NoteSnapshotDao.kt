package com.jotty.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface NoteSnapshotDao {
    @Insert
    suspend fun insert(snapshot: NoteSnapshotEntity): Long

    @Query(
        """
        SELECT * FROM note_snapshots
        WHERE noteId = :noteId AND instanceId = :instanceId
        ORDER BY savedAtEpochMs DESC
        """,
    )
    suspend fun listForNote(
        noteId: String,
        instanceId: String,
    ): List<NoteSnapshotEntity>

    @Query(
        """
        SELECT * FROM note_snapshots
        WHERE noteId = :noteId AND instanceId = :instanceId
        ORDER BY savedAtEpochMs DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestForNote(
        noteId: String,
        instanceId: String,
    ): NoteSnapshotEntity?

    @Query("SELECT * FROM note_snapshots WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): NoteSnapshotEntity?

    @Query("DELETE FROM note_snapshots WHERE id = :id")
    suspend fun deleteById(id: Long)
}
