package com.jotty.android.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPayloadCodecTest {
    @Test
    fun noteRoundTrip() {
        val entity =
            NoteEntity(
                id = "n1",
                title = "Title",
                category = "Cat",
                content = "Body",
                createdAt = "t0",
                updatedAt = "t1",
                encrypted = false,
                instanceId = "inst",
            )
        val json = SyncPayloadCodec.encodeNote(entity)
        val decoded = SyncPayloadCodec.decodeNote(json)
        assertNotNull(decoded)
        assertEquals("n1", decoded!!.id)
        assertEquals("Title", decoded.title)
        assertEquals("Body", decoded.content)
    }

    @Test
    fun withFirstDirtyBaseline_preservesOriginalBaseline() {
        val clean =
            NoteEntity(
                id = "n1",
                title = "A",
                category = "C",
                content = "old",
                createdAt = "t0",
                updatedAt = "t0",
                encrypted = false,
                instanceId = "inst",
            )
        val first =
            clean.withFirstDirtyBaseline(
                clean.copy(content = "new1", updatedAt = "t1", isDirty = true),
            )
        assertTrue(first.isDirty)
        assertNotNull(first.syncBaselineJson)
        assertNotNull(first.dirtySinceEpochMs)
        val second =
            first.withFirstDirtyBaseline(
                first.copy(content = "new2", updatedAt = "t2"),
            )
        assertEquals(first.syncBaselineJson, second.syncBaselineJson)
        assertEquals(first.dirtySinceEpochMs, second.dirtySinceEpochMs)
        val baseline = SyncPayloadCodec.decodeNote(requireNotNull(second.syncBaselineJson))
        assertEquals("old", baseline!!.content)
    }

    @Test
    fun checklistEntityToPendingItem_countsOpsAndDelete() {
        val entity =
            ChecklistEntity(
                id = "c1",
                title = "List",
                category = "C",
                type = "simple",
                itemsJson = "[]",
                pendingOpsJson = """[{"type":"CHECK","path":"0"},{"type":"ADD","text":"x"}]""",
                createdAt = "t0",
                updatedAt = "t1",
                isDirty = true,
                isDeleted = true,
                instanceId = "inst",
                dirtySinceEpochMs = 123L,
            )
        val item = SyncPayloadCodec.checklistEntityToPendingItem(entity)
        assertEquals(SyncBackupKind.CHECKLIST, item.kind)
        assertTrue(item.isPendingDelete)
        assertEquals(2, item.pendingOpCount)
        assertEquals(123L, item.dirtySinceEpochMs)
    }
}
