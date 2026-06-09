package com.jotty.android.ui.checklists

import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.ChecklistItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ChecklistProgressTest {
    @Test
    fun checklistProgressCounts_includesNestedSubtasks() {
        val checklist =
            Checklist(
                id = "1",
                title = "Test",
                items =
                    listOf(
                        ChecklistItem(
                            index = 0,
                            text = "Parent",
                            completed = false,
                            children =
                                listOf(
                                    ChecklistItem(index = 0, text = "Done child", completed = true),
                                    ChecklistItem(index = 1, text = "Open child", completed = false),
                                ),
                        ),
                        ChecklistItem(index = 1, text = "Done top", completed = true),
                    ),
                createdAt = "",
                updatedAt = "",
            )
        val counts = checklistProgressCounts(checklist)
        assertEquals(4, counts.total)
        assertEquals(2, counts.completed)
    }

    @Test
    fun checklistProgressCounts_treatsStatusCompletedAsDone() {
        val checklist =
            Checklist(
                id = "1",
                title = "Task",
                type = "task",
                items =
                    listOf(
                        ChecklistItem(index = 0, text = "Via status", completed = false, status = "completed"),
                    ),
                createdAt = "",
                updatedAt = "",
            )
        assertEquals(1, checklistProgressCounts(checklist).completed)
    }
}
