package com.jotty.android.util

import com.google.gson.Gson
import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.ItemStatusChange
import com.jotty.android.data.api.KanbanPriority
import com.jotty.android.data.api.hasAnyRichField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistItemRichFieldsTest {
    private val gson = Gson()

    @Test
    fun checklistItem_deserializesExpandedFields() {
        val json =
            """
            {
              "index": 0,
              "text": "Task",
              "description": "Notes",
              "priority": "high",
              "score": 5,
              "startDate": "2026-06-10",
              "targetDate": "2026-06-15",
              "estimatedTime": 2.5,
              "createdBy": "alice",
              "createdAt": "2026-06-01T10:00:00Z",
              "lastModifiedBy": "bob",
              "lastModifiedAt": "2026-06-02T11:00:00Z",
              "history": [{ "status": "in_progress", "timestamp": "2026-06-02T11:00:00Z", "user": "bob" }],
              "children": [{
                "index": 0,
                "text": "Sub",
                "priority": "low"
              }]
            }
            """.trimIndent()

        val item = gson.fromJson(json, ChecklistItem::class.java)
        assertEquals("Task", item.text)
        assertEquals("Notes", item.description)
        assertEquals(KanbanPriority.HIGH, item.priority)
        assertEquals(5.0, item.score)
        assertEquals("2026-06-10", item.startDate)
        assertEquals("2026-06-15", item.targetDate)
        assertEquals(2.5, item.estimatedTime)
        assertEquals("alice", item.createdBy)
        assertNotNull(item.history)
        assertEquals(1, item.history?.size)
        assertEquals("bob", item.history?.first()?.user)
        assertEquals(KanbanPriority.LOW, item.children?.single()?.priority)
        assertTrue(item.hasAnyRichField())
    }

    @Test
    fun hasAnyRichField_falseForPlainItem() {
        val item = ChecklistItem(index = 0, text = "Plain")
        assertFalse(item.hasAnyRichField())
    }
}

class ChecklistItemUpdateRichFieldsTest {
    @Test
    fun buildKanbanRichFieldsPatch_includesOnlyChangedFields() {
        val original =
            ChecklistItem(
                index = 0,
                text = "Task",
                description = "Old",
                priority = KanbanPriority.MEDIUM,
                score = 1.0,
            )
        val patch =
            buildKanbanRichFieldsPatch(
                original = original,
                description = "New",
                priority = KanbanPriority.HIGH,
                score = 1.0,
                startDate = "2026-06-10",
                targetDate = null,
                estimatedTime = null,
            )
        assertNotNull(patch)
        assertEquals("New", patch!!["description"])
        assertEquals(KanbanPriority.HIGH, patch["priority"])
        assertFalse(patch.containsKey("score"))
        assertEquals("2026-06-10", patch["startDate"])
    }

    @Test
    fun buildKanbanRichFieldsPatch_nullWhenUnchanged() {
        val original = ChecklistItem(index = 0, text = "Task", description = "Same")
        val patch =
            buildKanbanRichFieldsPatch(
                original = original,
                description = "Same",
                priority = null,
                score = null,
                startDate = null,
                targetDate = null,
                estimatedTime = null,
            )
        assertNull(patch)
    }

    @Test
    fun buildKanbanRichFieldsPatch_clearingScoreSendsNull() {
        val original = ChecklistItem(index = 0, text = "Task", score = 3.0)
        val patch =
            buildKanbanRichFieldsPatch(
                original = original,
                description = "",
                priority = null,
                score = null,
                startDate = null,
                targetDate = null,
                estimatedTime = null,
            )
        assertNotNull(patch)
        assertNull(patch!!["score"])
    }
}
