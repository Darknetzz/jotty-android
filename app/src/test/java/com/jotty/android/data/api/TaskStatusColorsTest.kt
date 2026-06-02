package com.jotty.android.data.api

import com.jotty.android.ui.checklists.parseHexColorOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TaskStatusColorsTest {
    @Test
    fun effectiveColorHex_usesServerColorWhenPresent() {
        val status = TaskStatus(id = "todo", label = "To Do", order = 0, color = "#ef4444")
        assertEquals("#ef4444", status.effectiveColorHex())
    }

    @Test
    fun effectiveColorHex_fallsBackForKnownIds() {
        assertEquals("#6b7280", TaskStatus(id = "todo", label = "To Do", order = 0).effectiveColorHex())
        assertEquals("#3b82f6", TaskStatus(id = "in_progress", label = "In Progress", order = 1).effectiveColorHex())
        assertEquals("#10b981", TaskStatus(id = "completed", label = "Done", order = 2).effectiveColorHex())
    }

    @Test
    fun effectiveColorHex_cyclesPaletteForUnknownIds() {
        val custom = TaskStatus(id = "review", label = "Review", order = 3)
        assertEquals("#f59e0b", custom.effectiveColorHex())
    }

    @Test
    fun parseHexColorOrNull_acceptsWithOrWithoutHash() {
        assertNotNull(parseHexColorOrNull("#3b82f6"))
        assertNotNull(parseHexColorOrNull("3b82f6"))
        assertNull(parseHexColorOrNull("not-a-color"))
    }
}
