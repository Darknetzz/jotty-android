package com.jotty.android.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncConflictsTest {
    private fun note(
        id: String,
        content: String,
        isDirty: Boolean = false,
        isDeleted: Boolean = false,
    ) = NoteEntity(
        id = id,
        title = "T",
        category = "C",
        content = content,
        createdAt = "c",
        updatedAt = "u",
        encrypted = null,
        isDirty = isDirty,
        isDeleted = isDeleted,
        instanceId = "i",
    )

    @Test
    fun copiesOnlyRowsWithLocalEditsThatConflict() {
        val localBefore =
            listOf(
                note("same", "x", isDirty = true),
                note("differs", "mine", isDirty = true),
                note("clean", "old"),
                note("deleted", "mine", isDirty = true, isDeleted = true),
            ).associateBy { it.id }
        val server = listOf(note("same", "x"), note("differs", "theirs"), note("clean", "new"), note("deleted", "theirs"))

        val copies =
            conflictCopiesAfterSync(
                serverRows = server,
                serverId = { it.id },
                localBefore = localBefore,
                hadLocalEdits = { it.isDirty && !it.isDeleted },
                hasConflict = ::noteHasConflict,
                toCopy = { it.toConflictCopy() },
            )

        assertEquals(1, copies.size)
        val copy = copies.single()
        assertEquals("differs", copy.conflictOfId)
        assertEquals("mine", copy.content)
        assertEquals("T$LOCAL_COPY_SUFFIX", copy.title)
        assertTrue(copy.isConflictCopy)
        assertFalse(copy.isDirty)
    }

    @Test
    fun withoutLocalCopySuffix_stripsOnlyTheSuffix() {
        assertEquals("Plan", "Plan$LOCAL_COPY_SUFFIX".withoutLocalCopySuffix())
        assertEquals("Plan", "Plan".withoutLocalCopySuffix())
    }
}
