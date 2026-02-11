package com.jotty.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for notes.
 * Provides methods to interact with the local notes database.
 */
@Dao
interface NoteDao {
    /**
     * Get all notes for a specific instance that are not marked as deleted.
     */
    @Query("SELECT * FROM notes WHERE instanceId = :instanceId AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun getAllNotesFlow(instanceId: String): Flow<List<NoteEntity>>

    /**
     * Get all notes for a specific instance that are not marked as deleted (suspend).
     */
    @Query("SELECT * FROM notes WHERE instanceId = :instanceId AND isDeleted = 0 ORDER BY updatedAt DESC")
    suspend fun getAllNotes(instanceId: String): List<NoteEntity>

    /**
     * Get a specific note by ID.
     */
    @Query("SELECT * FROM notes WHERE id = :noteId AND isDeleted = 0")
    suspend fun getNoteById(noteId: String): NoteEntity?

    /**
     * Get notes that need to be synced (dirty or deleted).
     */
    @Query("SELECT * FROM notes WHERE instanceId = :instanceId AND (isDirty = 1 OR isDeleted = 1)")
    suspend fun getDirtyNotes(instanceId: String): List<NoteEntity>

    /**
     * Insert or update a note.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    /**
     * Insert multiple notes (used for bulk sync from server).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    /**
     * Update an existing note.
     */
    @Update
    suspend fun updateNote(note: NoteEntity)

    /**
     * Mark a note as deleted (soft delete for sync).
     */
    @Query("UPDATE notes SET isDeleted = 1, isDirty = 1 WHERE id = :noteId")
    suspend fun markAsDeleted(noteId: String)

    /**
     * Permanently delete a note from the database.
     */
    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNote(noteId: String)

    /**
     * Delete all notes for a specific instance (e.g., when disconnecting).
     */
    @Query("DELETE FROM notes WHERE instanceId = :instanceId")
    suspend fun deleteAllNotes(instanceId: String)

    /**
     * Clear the dirty flag for a note after successful sync.
     */
    @Query("UPDATE notes SET isDirty = 0 WHERE id = :noteId")
    suspend fun clearDirtyFlag(noteId: String)

    /**
     * Search notes by title or content.
     */
    @Query("SELECT * FROM notes WHERE instanceId = :instanceId AND isDeleted = 0 AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') ORDER BY updatedAt DESC")
    suspend fun searchNotes(instanceId: String, query: String): List<NoteEntity>

    /**
     * Filter notes by category.
     */
    @Query("SELECT * FROM notes WHERE instanceId = :instanceId AND isDeleted = 0 AND category = :category ORDER BY updatedAt DESC")
    suspend fun getNotesByCategory(instanceId: String, category: String): List<NoteEntity>
}
