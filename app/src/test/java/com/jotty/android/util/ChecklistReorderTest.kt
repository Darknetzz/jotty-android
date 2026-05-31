package com.jotty.android.util

import com.jotty.android.data.api.ChecklistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChecklistReorderTest {
    private val items =
        listOf(
            ChecklistItem(id = "a", index = 0, text = "First"),
            ChecklistItem(id = "b", index = 1, text = "Second"),
            ChecklistItem(id = "c", index = 2, text = "Third"),
        )

    @Test
    fun moveChecklistItemUpRequest_returnsBeforePosition() {
        val request = moveChecklistItemUpRequest(items, "b")

        assertEquals("b", request?.activeItemId)
        assertEquals("a", request?.overItemId)
        assertEquals("before", request?.position)
    }

    @Test
    fun moveChecklistItemDownRequest_returnsAfterPosition() {
        val request = moveChecklistItemDownRequest(items, "b")

        assertEquals("b", request?.activeItemId)
        assertEquals("c", request?.overItemId)
        assertEquals("after", request?.position)
    }

    @Test
    fun reorderChecklistItems_movesItemDown() {
        val reordered =
            reorderChecklistItems(
                items = items,
                activeItemId = "a",
                overItemId = "c",
                position = "before",
            )

        assertEquals(listOf("b", "a", "c"), reordered.map { it.id })
    }

    @Test
    fun moveChecklistItemUpRequest_onFirstItem_returnsNull() {
        assertNull(moveChecklistItemUpRequest(items, "a"))
    }
}
