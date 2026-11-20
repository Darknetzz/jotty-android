package com.jotty.android.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepeatRuleTest {
    @Test
    fun parsesWeeklyRuleWithDays() {
        val rule = RepeatRule.parse("FREQ=WEEKLY;INTERVAL=2;BYDAY=TH,MO")
        assertEquals(RepeatFrequency.WEEKLY, rule?.frequency)
        assertEquals(2, rule?.interval)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), rule?.byDay)
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,TH", rule?.toRrule())
    }

    @Test
    fun parsesLowercaseAndPrefix() {
        val rule = RepeatRule.parse("RRULE:freq=monthly")
        assertEquals(RepeatFrequency.MONTHLY, rule?.frequency)
        assertEquals(1, rule?.interval)
    }

    @Test
    fun keepsPartsItDoesNotEdit() {
        val rule = RepeatRule.parse("FREQ=DAILY;COUNT=5;WKST=MO")
        assertEquals(listOf("COUNT=5", "WKST=MO"), rule?.otherParts)
        assertEquals("FREQ=DAILY;INTERVAL=1;COUNT=5;WKST=MO", rule?.toRrule())
    }

    @Test
    fun dropsByDayForNonWeekly() {
        assertEquals(
            "FREQ=YEARLY;INTERVAL=1",
            RepeatRule(RepeatFrequency.YEARLY, byDay = setOf(DayOfWeek.MONDAY)).toRrule(),
        )
    }

    @Test
    fun rejectsMissingOrUnsupportedFrequency() {
        assertNull(RepeatRule.parse(null))
        assertNull(RepeatRule.parse("INTERVAL=2"))
        assertNull(RepeatRule.parse("FREQ=HOURLY"))
    }

    @Test
    fun recurrenceDateHandlesDateTimesAndDates() {
        assertEquals(LocalDate.of(2026, 1, 5), recurrenceDate("2026-01-05T09:00:00.000Z", ZoneOffset.UTC))
        assertEquals(LocalDate.of(2026, 1, 4), recurrenceDate("2026-01-05T02:00:00Z", ZoneOffset.ofHours(-5)))
        assertEquals(LocalDate.of(2026, 1, 5), recurrenceDate("2026-01-05T00:00:00-05:00", ZoneOffset.ofHours(-5)))
        assertEquals(LocalDate.of(2026, 3, 3), recurrenceDate("2026-03-03", ZoneOffset.UTC))
        assertNull(recurrenceDate("soon", ZoneOffset.UTC))
    }
}
