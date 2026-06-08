package com.jotty.android.util

import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KanbanGroupingTest {
    @Test
    fun defaultKanbanItemStatus_picksLowestOrder() {
        val statuses =
            listOf(
                TaskStatus(id = "done", label = "Done", order = 2),
                TaskStatus(id = "todo", label = "To Do", order = 0),
                TaskStatus(id = "wip", label = "WIP", order = 1),
            )
        assertEquals("todo", defaultKanbanItemStatus(statuses))
    }

    @Test
    fun moveKanbanCardInColumnRequest_movesDown() {
        val cards =
            listOf(
                KanbanCard(0, ChecklistItem(id = "a", index = 0, text = "A", status = "todo")),
                KanbanCard(1, ChecklistItem(id = "b", index = 1, text = "B", status = "todo")),
            )
        val request = moveKanbanCardInColumnRequest(cards, cardIndex = 0, up = false)
        assertEquals("a", request?.activeItemId)
        assertEquals("b", request?.overItemId)
        assertEquals("after", request?.position)
    }

    @Test
    fun kanbanCardReorderRequest_movesDown() {
        val cards =
            listOf(
                KanbanCard(0, ChecklistItem(id = "a", index = 0, text = "A", status = "todo")),
                KanbanCard(1, ChecklistItem(id = "b", index = 1, text = "B", status = "todo")),
                KanbanCard(2, ChecklistItem(id = "c", index = 2, text = "C", status = "todo")),
            )
        val request = kanbanCardReorderRequest(cards, fromIndex = 0, toIndex = 2)
        assertEquals("a", request?.activeItemId)
        assertEquals("c", request?.overItemId)
        assertEquals("after", request?.position)
    }
}
