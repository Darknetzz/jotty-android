package com.jotty.android.ui.checklists

import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.local.applyOpToItems
import com.jotty.android.data.local.itemAtPath
import com.jotty.android.data.local.PendingItemOp
import com.jotty.android.data.local.toPendingItemOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class KanbanItemActionsTest {
    @Test
    fun kanbanChildPath_appendsIndex() {
        assertEquals("0.1", kanbanChildPath("0", 1))
        assertEquals("2.0.3", kanbanChildPath("2.0", 3))
    }

    @Test
    fun itemAtPath_resolvesNestedSubtask() {
        val items =
            listOf(
                ChecklistItem(
                    index = 0,
                    text = "Parent",
                    children =
                        listOf(
                            ChecklistItem(index = 0, text = "Child"),
                        ),
                ),
            )
        val child = itemAtPath(items, "0.0")
        assertNotNull(child)
        assertEquals("Child", child?.text)
    }

    @Test
    fun applyOpToItems_updateDescription() {
        val items = listOf(ChecklistItem(index = 0, text = "Task"))
        val updated =
            applyOpToItems(
                items,
                PendingItemOp(type = "UPDATE", path = "0", description = "Notes", descriptionTouched = true),
            )
        assertEquals("Notes", updated[0].description)
    }

    @Test
    fun applyOpToItems_updateRichFields() {
        val items = listOf(ChecklistItem(index = 0, text = "Task", score = 1.0))
        val updated =
            applyOpToItems(
                items,
                mapOf("score" to null).toPendingItemOp("0"),
            )
        assertEquals(null, updated[0].score)
    }

    @Test
    fun toPendingItemOp_preservesPatchKeysForClear() {
        val op = mapOf<String, Any?>("score" to null).toPendingItemOp("0")
        assertTrue(op.patchKeys?.contains("score") == true)
        assertEquals(null, op.score)
    }
}
