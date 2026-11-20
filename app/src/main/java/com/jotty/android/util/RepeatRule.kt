package com.jotty.android.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** `FREQ` values Jotty 1.30+ accepts for item recurrence. */
enum class RepeatFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * Editable view of an RFC 5545 RRULE as Jotty accepts it (FREQ, INTERVAL, BYDAY on weekly rules).
 * Other parts the app doesn't edit (COUNT, WKST, UNTIL inside the rule) are kept in [otherParts]
 * so re-saving doesn't drop them.
 */
data class RepeatRule(
    val frequency: RepeatFrequency,
    val interval: Int = 1,
    val byDay: Set<DayOfWeek> = emptySet(),
    val otherParts: List<String> = emptyList(),
) {
    fun toRrule(): String =
        buildList {
            add("FREQ=${frequency.name}")
            add("INTERVAL=${interval.coerceAtLeast(1)}")
            if (frequency == RepeatFrequency.WEEKLY && byDay.isNotEmpty()) {
                add("BYDAY=" + byDay.sorted().joinToString(",") { RRULE_DAYS.getValue(it) })
            }
            addAll(otherParts)
        }.joinToString(";")

    companion object {
        private val RRULE_DAYS =
            mapOf(
                DayOfWeek.MONDAY to "MO",
                DayOfWeek.TUESDAY to "TU",
                DayOfWeek.WEDNESDAY to "WE",
                DayOfWeek.THURSDAY to "TH",
                DayOfWeek.FRIDAY to "FR",
                DayOfWeek.SATURDAY to "SA",
                DayOfWeek.SUNDAY to "SU",
            )
        private val DAYS_BY_CODE = RRULE_DAYS.entries.associate { (day, code) -> code to day }

        /** Parses [rrule] (with or without `RRULE:`), or null when FREQ is missing/unsupported. */
        fun parse(rrule: String?): RepeatRule? {
            val body = rrule?.trim()?.removePrefix("RRULE:")?.removePrefix("rrule:") ?: return null
            var frequency: RepeatFrequency? = null
            var interval = 1
            val byDay = mutableSetOf<DayOfWeek>()
            val other = mutableListOf<String>()
            for (part in body.split(';').map { it.trim() }.filter { it.isNotEmpty() }) {
                val key = part.substringBefore('=').uppercase()
                val value = part.substringAfter('=', "").trim()
                when (key) {
                    "FREQ" -> frequency = RepeatFrequency.entries.find { it.name == value.uppercase() }
                    "INTERVAL" -> interval = value.toIntOrNull()?.takeIf { it > 0 } ?: 1
                    "BYDAY" ->
                        value.split(',').forEach { code ->
                            DAYS_BY_CODE[code.trim().uppercase()]?.let(byDay::add)
                        }
                    else -> other += part
                }
            }
            return frequency?.let { RepeatRule(it, interval, byDay, other) }
        }
    }
}

/**
 * Local calendar date for a Jotty recurrence timestamp (ISO date or date-time), or null when unparsable.
 */
fun recurrenceDate(
    raw: String?,
    zone: ZoneId = ZoneId.systemDefault(),
): LocalDate? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return try {
        OffsetDateTime.parse(text).atZoneSameInstant(zone).toLocalDate()
    } catch (_: DateTimeParseException) {
        try {
            Instant.parse(text).atZone(zone).toLocalDate()
        } catch (_: DateTimeParseException) {
            try {
                LocalDate.parse(text.take(10))
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}
