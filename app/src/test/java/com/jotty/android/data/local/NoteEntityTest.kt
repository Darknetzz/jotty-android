package com.jotty.android.data.local

import com.jotty.android.data.api.Note
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for NoteEntity conversions.
 */
class NoteEntityTest {

    @Test
    fun `toEntity converts Note to NoteEntity correctly`() {
        val note = Note(
            id = "test-id",
            title = "Test Note",
            category = "Test Category",
            content = "Test content",
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-01-01T00:00:00Z",
            encrypted = false
        )

        val entity = note.toEntity(
            instanceId = "instance-123",
            isDirty = true
        )

        assertEquals("test-id", entity.id)
        assertEquals("Test Note", entity.title)
        assertEquals("Test Category", entity.category)
        assertEquals("Test content", entity.content)
        assertEquals("2024-01-01T00:00:00Z", entity.createdAt)
        assertEquals("2024-01-01T00:00:00Z", entity.updatedAt)
        assertEquals(false, entity.encrypted)
        assertEquals(true, entity.isDirty)
        assertEquals(false, entity.isDeleted)
        assertEquals("instance-123", entity.instanceId)
    }

    @Test
    fun `toNote converts NoteEntity to Note correctly`() {
        val entity = NoteEntity(
            id = "test-id",
            title = "Test Note",
            category = "Test Category",
            content = "Test content",
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-01-01T00:00:00Z",
            encrypted = true,
            isDirty = true,
            isDeleted = false,
            instanceId = "instance-123"
        )

        val note = entity.toNote()

        assertEquals("test-id", note.id)
        assertEquals("Test Note", note.title)
        assertEquals("Test Category", note.category)
        assertEquals("Test content", note.content)
        assertEquals("2024-01-01T00:00:00Z", note.createdAt)
        assertEquals("2024-01-01T00:00:00Z", note.updatedAt)
        assertEquals(true, note.encrypted)
    }

    @Test
    fun `toEntity sets default values correctly`() {
        val note = Note(
            id = "test-id",
            title = "Test Note",
            category = "Uncategorized",
            content = "",
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-01-01T00:00:00Z",
            encrypted = null
        )

        val entity = note.toEntity(instanceId = "instance-123")

        assertEquals(false, entity.isDirty) // default when not specified
        assertEquals(false, entity.isDeleted)
        assertEquals(null, entity.encrypted)
    }

    @Test
    fun `round trip conversion preserves data`() {
        val originalNote = Note(
            id = "test-id",
            title = "Test Note",
            category = "Test Category",
            content = "Test content with special chars: 中文, emoji 🎉",
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-01-02T00:00:00Z",
            encrypted = true
        )

        val entity = originalNote.toEntity(instanceId = "instance-123")
        val convertedNote = entity.toNote()

        // Core note fields should be preserved
        assertEquals(originalNote.id, convertedNote.id)
        assertEquals(originalNote.title, convertedNote.title)
        assertEquals(originalNote.category, convertedNote.category)
        assertEquals(originalNote.content, convertedNote.content)
        assertEquals(originalNote.createdAt, convertedNote.createdAt)
        assertEquals(originalNote.updatedAt, convertedNote.updatedAt)
        assertEquals(originalNote.encrypted, convertedNote.encrypted)
    }

    @Test
    fun `creating local copy for conflict appends suffix to title`() {
        val originalEntity = NoteEntity(
            id = "original-id",
            title = "My Note",
            category = "Work",
            content = "Original content edited offline",
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-01-02T10:00:00Z",
            encrypted = false,
            isDirty = true,
            isDeleted = false,
            instanceId = "instance-123"
        )

        // Simulate creating a local copy for conflict resolution
        val localCopy = originalEntity.copy(
            id = "new-uuid-for-copy",
            title = "${originalEntity.title} (Local copy)",
            isDirty = false
        )

        assertEquals("new-uuid-for-copy", localCopy.id)
        assertEquals("My Note (Local copy)", localCopy.title)
        assertEquals("Original content edited offline", localCopy.content)
        assertEquals(false, localCopy.isDirty) // Copy should not be synced
        assertEquals(false, localCopy.isDeleted)
    }
}
