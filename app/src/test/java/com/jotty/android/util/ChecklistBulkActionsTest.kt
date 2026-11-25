package com.jotty.android.util

import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.local.PendingItemOp
import com.jotty.android.data.local.applyOpToItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistBulkActionsTest {
    private val items =
        listOf(
            ChecklistItem(index = 0, text = "Milk", completed = true),
            ChecklistItem(
                index = 1,
                text = "Fruit",
                children =
                    listOf(
                        ChecklistItem(index = 0, text = "Apples", completed = true),
                        ChecklistItem(index = 1, text = "Pears"),
                    ),
            ),
            ChecklistItem(index = 2, text = "Bread"),
            ChecklistItem(index = 3, text = "Eggs", status = "completed"),
        )

    private fun List<PendingItemOp>.describe() = map { "${it.type} ${it.path}" }

    @Test
    fun checkAll_targetsOpenItemsAtAnyDepthParentFirst() {
        assertEquals(
            listOf("CHECK 1", "CHECK 1.1", "CHECK 2"),
            checklistBulkOps(items, ChecklistBulkAction.CheckAll).describe(),
        )
    }

    @Test
    fun uncheckAll_targetsCompletedItemsIncludingStatusCompleted() {
        assertEquals(
            listOf("UNCHECK 0", "UNCHECK 1.0", "UNCHECK 3"),
            checklistBulkOps(items, ChecklistBulkAction.UncheckAll).describe(),
        )
    }

    @Test
    fun clear_deletesTopLevelItemsHighestIndexFirst() {
        assertEquals(
            listOf("DELETE 2", "DELETE 1"),
            checklistBulkOps(items, ChecklistBulkAction.ClearToDo).describe(),
        )
        assertEquals(
            listOf("DELETE 3", "DELETE 0"),
            checklistBulkOps(items, ChecklistBulkAction.ClearCompleted).describe(),
        )
    }

    @Test
    fun clearCompleted_appliedInOrder_keepsOpenItems() {
        val result =
            checklistBulkOps(items, ChecklistBulkAction.ClearCompleted)
                .fold(items) { acc, op -> applyOpToItems(acc, op) }
        assertEquals(listOf("Fruit", "Bread"), result.map { it.text })
    }

    @Test
    fun checkAll_appliedInOrder_completesEverything() {
        val result =
            checklistBulkOps(items, ChecklistBulkAction.CheckAll)
                .fold(items) { acc, op -> applyOpToItems(acc, op) }
        assertTrue(checklistBulkOps(result, ChecklistBulkAction.CheckAll).isEmpty())
    }

    @Test
    fun emptySection_producesNoOps() {
        val allDone = listOf(ChecklistItem(index = 0, text = "Done", completed = true))
        assertTrue(checklistBulkOps(allDone, ChecklistBulkAction.CheckAll).isEmpty())
        assertTrue(checklistBulkOps(allDone, ChecklistBulkAction.ClearToDo).isEmpty())
    }
}
