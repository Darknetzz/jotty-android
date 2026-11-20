package com.jotty.android.ui.checklists

import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.ItemRecurrence
import com.jotty.android.data.api.ItemRecurrenceInput
import com.jotty.android.data.local.applyOpToItems
import com.jotty.android.data.local.itemAtPath
import com.jotty.android.data.local.PendingItemOp
import com.jotty.android.data.local.toPendingItemOp
import com.jotty.android.data.local.toRichFieldsPatch
import com.jotty.android.util.recurrencePatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun recurrenceOp_setsAndStopsLocallyAndReplays() {
        val input = ItemRecurrenceInput(rrule = "FREQ=WEEKLY;INTERVAL=1", dtstart = "2026-01-05")
        val items = listOf(ChecklistItem(index = 0, text = "Bins"))

        val setOp = recurrencePatch(input).toPendingItemOp("0")
        val set = applyOpToItems(items, setOp)
        assertEquals("FREQ=WEEKLY;INTERVAL=1", set[0].recurrence?.rrule)
        assertEquals(mapOf("recurrence" to input), setOp.toRichFieldsPatch())

        val stopOp = recurrencePatch(null).toPendingItemOp("0")
        assertNull(applyOpToItems(set, stopOp)[0].recurrence)
        assertEquals(mapOf("recurrence" to null), stopOp.toRichFieldsPatch())
    }

    @Test
    fun richFieldOp_keepsExistingRecurrence() {
        val recurrence = ItemRecurrence(rrule = "FREQ=DAILY", dtstart = "2026-01-01")
        val items = listOf(ChecklistItem(index = 0, text = "Task", recurrence = recurrence))
        val updated = applyOpToItems(items, mapOf("score" to 2.0).toPendingItemOp("0"))
        assertEquals(recurrence, updated[0].recurrence)
    }
}
