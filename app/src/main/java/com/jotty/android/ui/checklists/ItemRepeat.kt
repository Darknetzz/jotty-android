package com.jotty.android.ui.checklists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.data.api.ItemRecurrence
import com.jotty.android.data.api.ItemRecurrenceInput
import com.jotty.android.util.RepeatFrequency
import com.jotty.android.util.RepeatRule
import com.jotty.android.util.recurrenceDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Short description of [recurrence] such as "Every 2 weeks on Mon, Thu", or null when it has no readable rule. */
@Composable
fun repeatSummary(recurrence: ItemRecurrence?): String? {
    val rule = RepeatRule.parse(recurrence?.rrule) ?: return null
    val base =
        when (rule.frequency) {
            RepeatFrequency.DAILY -> pluralStringResource(R.plurals.repeat_every_days, rule.interval, rule.interval)
            RepeatFrequency.WEEKLY -> pluralStringResource(R.plurals.repeat_every_weeks, rule.interval, rule.interval)
            RepeatFrequency.MONTHLY -> pluralStringResource(R.plurals.repeat_every_months, rule.interval, rule.interval)
            RepeatFrequency.YEARLY -> pluralStringResource(R.plurals.repeat_every_years, rule.interval, rule.interval)
        }
    if (rule.frequency != RepeatFrequency.WEEKLY || rule.byDay.isEmpty()) return base
    val days = rule.byDay.sorted().joinToString(", ") { it.shortName() }
    return stringResource(R.string.repeat_summary_on_days, base, days)
}

/** Small "repeats" line for item rows and cards: icon, rule summary and next due date. */
@Composable
fun RepeatBadge(
    recurrence: ItemRecurrence?,
    modifier: Modifier = Modifier,
) {
    val summary = repeatSummary(recurrence) ?: return
    val next =
        recurrenceDate(recurrence?.nextDue)?.let {
            stringResource(R.string.repeat_next_due, it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
        }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Default.Repeat,
            contentDescription = stringResource(R.string.cd_repeats),
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = listOfNotNull(summary, next).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Kanban detail row: current rule (or "Doesn't repeat"); tap to edit. */
@Composable
fun RepeatSettingRow(
    recurrence: ItemRecurrence?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 4.dp),
    ) {
        Text(stringResource(R.string.repeat_title), style = MaterialTheme.typography.titleSmall)
        if (repeatSummary(recurrence) != null) {
            RepeatBadge(recurrence = recurrence, modifier = Modifier.padding(top = 4.dp))
        } else {
            Text(
                text = stringResource(R.string.repeat_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Edits an item's repeat rule. [onSave] gets the new rule, or null for "Stop repeating".
 * Dates the user doesn't change keep the server's original timestamp.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepeatItemDialog(
    initial: ItemRecurrence?,
    onDismiss: () -> Unit,
    onSave: (ItemRecurrenceInput?) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val initialRule = remember(initial) { RepeatRule.parse(initial?.rrule) }
    val initialStart = remember(initial) { recurrenceDate(initial?.dtstart, zone) }
    val initialEnd = remember(initial) { recurrenceDate(initial?.until, zone) }
    var frequency by remember { mutableStateOf(initialRule?.frequency ?: RepeatFrequency.WEEKLY) }
    var intervalText by remember { mutableStateOf((initialRule?.interval ?: 1).toString()) }
    var byDay by remember { mutableStateOf(initialRule?.byDay ?: emptySet()) }
    var startText by remember { mutableStateOf((initialStart ?: LocalDate.now(zone)).toString()) }
    var endText by remember { mutableStateOf(initialEnd?.toString().orEmpty()) }

    val interval = intervalText.trim().toIntOrNull()?.takeIf { it > 0 }
    val start = runCatching { LocalDate.parse(startText) }.getOrNull()
    val end = endText.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val endBeforeStart = start != null && end != null && end.isBefore(start)
    val canSave = interval != null && start != null && (endText.isBlank() || end != null) && !endBeforeStart

    fun buildInput(): ItemRecurrenceInput? {
        if (interval == null || start == null) return null
        val rule =
            RepeatRule(
                frequency = frequency,
                interval = interval,
                byDay = if (frequency == RepeatFrequency.WEEKLY) byDay else emptySet(),
                otherParts = initialRule?.otherParts.orEmpty(),
            )
        val dtstart =
            initial?.dtstart?.takeIf { start == initialStart }
                ?: start.atStartOfDay(zone).toOffsetDateTime().toString()
        val until =
            end?.let {
                initial?.until?.takeIf { end == initialEnd }
                    ?: it.atTime(23, 59, 59).atZone(zone).toOffsetDateTime().toString()
            }
        return ItemRecurrenceInput(rrule = rule.toRrule(), dtstart = dtstart, until = until)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.repeat_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RepeatFrequency.entries.forEach { option ->
                        FilterChip(
                            selected = frequency == option,
                            onClick = { frequency = option },
                            label = { Text(option.label()) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.repeat_every))
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { text -> intervalText = text.filter(Char::isDigit).take(3) },
                        modifier =
                            Modifier
                                .padding(horizontal = 8.dp)
                                .width(72.dp),
                        singleLine = true,
                        isError = interval == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Text(frequency.unitLabel(interval ?: 1))
                }
                if (frequency == RepeatFrequency.WEEKLY) {
                    Text(stringResource(R.string.repeat_on_days), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = day in byDay,
                                onClick = { byDay = if (day in byDay) byDay - day else byDay + day },
                                label = { Text(day.shortName()) },
                            )
                        }
                    }
                }
                KanbanDateField(
                    label = stringResource(R.string.repeat_starts),
                    value = startText,
                    onValueChange = { startText = it },
                    enabled = true,
                )
                KanbanDateField(
                    label = stringResource(R.string.repeat_ends),
                    value = endText,
                    onValueChange = { endText = it },
                    enabled = true,
                )
                if (endBeforeStart) {
                    Text(
                        text = stringResource(R.string.repeat_ends_before_start),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                initialRule?.otherParts?.takeIf { it.isNotEmpty() }?.let { parts ->
                    Text(
                        text = stringResource(R.string.repeat_custom_rule, parts.joinToString(";")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.repeat_requires_server),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = { buildInput()?.let(onSave) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { onSave(null) }) {
                        Text(stringResource(R.string.repeat_stop), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun RepeatFrequency.label(): String =
    stringResource(
        when (this) {
            RepeatFrequency.DAILY -> R.string.repeat_daily
            RepeatFrequency.WEEKLY -> R.string.repeat_weekly
            RepeatFrequency.MONTHLY -> R.string.repeat_monthly
            RepeatFrequency.YEARLY -> R.string.repeat_yearly
        },
    )

@Composable
private fun RepeatFrequency.unitLabel(count: Int): String =
    pluralStringResource(
        when (this) {
            RepeatFrequency.DAILY -> R.plurals.repeat_unit_days
            RepeatFrequency.WEEKLY -> R.plurals.repeat_unit_weeks
            RepeatFrequency.MONTHLY -> R.plurals.repeat_unit_months
            RepeatFrequency.YEARLY -> R.plurals.repeat_unit_years
        },
        count,
    )

private fun DayOfWeek.shortName(): String = getDisplayName(TextStyle.SHORT, Locale.getDefault())
