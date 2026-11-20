package com.jotty.android.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.ItemRecurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChecklistItemReplayTest {
    @Test
    fun recurrenceSurvivesLocalJsonRoundTrip() {
        val json =
            """{"index":0,"text":"Bins","recurrence":{"rrule":"FREQ=WEEKLY;INTERVAL=1",""" +
                """"dtstart":"2026-01-05T09:00:00.000Z","nextDue":"2026-11-23T09:00:00.000Z"}}"""
        val gson = Gson()
        val item = gson.fromJson(json, ChecklistItem::class.java)
        val back = gson.fromJson(gson.toJson(item), ChecklistItem::class.java)
        assertEquals("FREQ=WEEKLY;INTERVAL=1", back.recurrence?.rrule)
        assertEquals("2026-11-23T09:00:00.000Z", back.recurrence?.nextDue)
    }

    @Test
    fun toInputDropsServerComputedFields() {
        val input =
            ItemRecurrence(
                rrule = " FREQ=DAILY ",
                dtstart = "2026-01-01",
                until = " ",
                nextDue = "2026-02-01",
                lastCompleted = "2026-01-31",
            ).toInput()
        assertEquals("FREQ=DAILY", input?.rrule)
        assertEquals("2026-01-01", input?.dtstart)
        assertNull(input?.until)
        assertEquals("""{"rrule":"FREQ=DAILY","dtstart":"2026-01-01"}""", GsonBuilder().disableHtmlEscaping().create().toJson(input))
    }

    @Test
    fun toInputNullWhenIncomplete() {
        assertNull(ItemRecurrence(rrule = "FREQ=DAILY").toInput())
        assertNull(ItemRecurrence(rrule = " ", dtstart = "2026-01-01").toInput())
    }
}
