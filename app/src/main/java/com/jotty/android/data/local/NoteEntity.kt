package com.jotty.android.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.jotty.android.data.api.Note

/**
 * Room entity for storing notes locally.
 * Used for offline support and caching.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val content: String,
    val createdAt: String,
    val updatedAt: String,
    val encrypted: Boolean?,
    val isDirty: Boolean = false, // True if locally modified and needs sync
    val isDeleted: Boolean = false, // True if marked for deletion
    val instanceId: String, // Which Jotty instance this note belongs to
)

/**
 * Convert API Note to local NoteEntity
 */
fun Note.toEntity(instanceId: String, isDirty: Boolean = false): NoteEntity {
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
        instanceId = instanceId
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
        encrypted = encrypted
    )
}
