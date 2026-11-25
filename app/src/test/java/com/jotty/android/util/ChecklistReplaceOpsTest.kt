package com.jotty.android.util

import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.ItemRecurrence
import com.jotty.android.data.local.applyOpToItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistReplaceOpsTest {
    private val current =
        listOf(
            ChecklistItem(index = 0, text = "A"),
            ChecklistItem(index = 1, text = "B"),
            ChecklistItem(index = 2, text = "C"),
        )

    @Test
    fun deletesCurrentItemsHighestIndexFirst() {
        val ops = buildReplaceItemsOps(current, emptyList())

        assertEquals(listOf("2", "1", "0"), ops.map { it.path })
        assertTrue(ops.all { it.type == "DELETE" })
    }

    @Test
    fun addsTargetDepthFirstWithFieldsAndCompletion() {
        val target =
            listOf(
                ChecklistItem(
                    index = 0,
                    text = "Parent",
                    completed = true,
                    priority = "high",
                    recurrence = ItemRecurrence(rrule = "FREQ=DAILY", dtstart = "2026-01-01"),
                    children = listOf(ChecklistItem(index = 0, text = "Child", description = "note")),
                ),
            )

        val ops = buildReplaceItemsOps(emptyList(), target)

        assertEquals(listOf("ADD", "UPDATE", "ADD", "UPDATE", "CHECK"), ops.map { it.type })
        assertNull(ops[0].parentIndex)
        assertEquals(listOf("priority", "recurrence"), ops[1].patchKeys)
        assertEquals("0", ops[1].path)
        assertEquals("0", ops[2].parentIndex)
        assertEquals("0.0", ops[3].path)
        assertEquals(listOf("description"), ops[3].patchKeys)
        assertEquals("0", ops[4].path)
    }

    @Test
    fun applyingOpsLocallyYieldsTargetTree() {
        val target =
            listOf(
                ChecklistItem(index = 0, text = "X", completed = true),
                ChecklistItem(index = 1, text = "Y", children = listOf(ChecklistItem(index = 0, text = "Y1"))),
            )

        val result = buildReplaceItemsOps(current, target).fold(current) { items, op -> applyOpToItems(items, op) }

        assertEquals(listOf("X", "Y"), result.map { it.text })
        assertTrue(result[0].completed)
        assertEquals(listOf("Y1"), result[1].children.orEmpty().map { it.text })
    }
}
