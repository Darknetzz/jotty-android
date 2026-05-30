package com.jotty.android.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jotty.android.data.api.Note

/**
 * Room entity for storing notes locally.
 * Used for offline support and caching.
 */
@Entity(
    tableName = "notes",
    indices = [Index(value = ["instanceId"])],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val content: String,
    val createdAt: String,
    val updatedAt: String,
    val encrypted: Boolean?,
    val isDirty: Boolean = false,
    val isDeleted: Boolean = false,
    val instanceId: String,
    // True for notes created offline that have never been pushed to the server.
    // Used instead of the fragile createdAt == updatedAt heuristic to decide
    // whether syncNote() should call createNote or updateNote.
    @ColumnInfo(defaultValue = "0")
    val isLocalOnly: Boolean = false,
    // Category the note had at the last successful server sync, captured before a local
    // category change so syncNote() can send it as originalCategory (the server uses it to
    // move the note between category folders). Null when the category was never changed locally.
    @ColumnInfo(defaultValue = "NULL")
    val originalCategory: String? = null,
)

/**
 * Convert API Note to local NoteEntity
 */
fun Note.toEntity(
    instanceId: String,
    isDirty: Boolean = false,
): NoteEntity {
    return NoteEntity(
        id = id,
        title = title,
        category = category,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
        encrypted = encrypted,
        isDirty = isDirty,
        isDeleted = false,
        instanceId = instanceId,
    )
}

/**
 * Convert local NoteEntity to API Note
 */
fun NoteEntity.toNote(): Note {
    return Note(
        id = id,
        title = title,
        category = category,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
        encrypted = encrypted,
    )
}
