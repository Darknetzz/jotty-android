package com.jotty.android.ui.checklists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jotty.android.R
import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.ItemStatusChange
import com.jotty.android.data.api.KanbanPriority
import com.jotty.android.data.api.TaskStatus
import com.jotty.android.data.api.effectiveColorHex
import com.jotty.android.ui.common.ConfirmDeleteDialog
import com.jotty.android.ui.common.DeleteDropdownMenuItem
import androidx.compose.foundation.layout.Box
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KanbanItemDetailScreen(
    item: ChecklistItem,
    itemPath: String,
    taskStatuses: List<TaskStatus>,
    statusMoveEnabled: Boolean,
    actions: KanbanItemActions,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
    showChecklistEmojis: Boolean = true,
    richFieldsSupported: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    var titleText by remember(itemPath, item.text) { mutableStateOf(item.text) }
    var descriptionText by remember(itemPath, item.description) { mutableStateOf(item.description.orEmpty()) }
    var localDescription by remember(itemPath) { mutableStateOf(item.description.orEmpty()) }
    var prioritySelection by remember(itemPath, item.priority) { mutableStateOf(item.priority) }
    var scoreText by remember(itemPath, item.score) { mutableStateOf(item.score?.toString().orEmpty()) }
    var startDateText by remember(itemPath, item.startDate) { mutableStateOf(item.startDate.orEmpty()) }
    var targetDateText by remember(itemPath, item.targetDate) { mutableStateOf(item.targetDate.orEmpty()) }
    var estimatedTimeText by remember(itemPath, item.estimatedTime) {
        mutableStateOf(item.estimatedTime?.toString().orEmpty())
    }
    var subtasks by remember(itemPath, item.children) { mutableStateOf(item.children.orEmpty()) }
    var currentStatusId by remember(itemPath, item.status) { mutableStateOf(item.status) }
    var metadataItem by remember(itemPath) { mutableStateOf(item) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    fun detailsChanged(): Boolean {
        val descChanged = descriptionText.trim() != item.description.orEmpty().trim()
        if (!richFieldsSupported) return descChanged
        val priorityChanged = prioritySelection != item.priority?.takeIf { it.isNotBlank() }
        val scoreChanged = parseOptionalDouble(scoreText) != item.score
        val startChanged = startDateText.trim().ifBlank { null } != item.startDate?.trim()?.ifBlank { null }
        val targetChanged = targetDateText.trim().ifBlank { null } != item.targetDate?.trim()?.ifBlank { null }
        val estimatedChanged = parseOptionalDouble(estimatedTimeText) != item.estimatedTime
        return descChanged || priorityChanged || scoreChanged || startChanged || targetChanged || estimatedChanged
    }

    fun hasUnsavedChanges(): Boolean {
        val titleChanged = titleText.trim() != item.text.trim() && titleText.trim().isNotEmpty()
        return titleChanged || detailsChanged()
    }

    fun applyRefreshedItem(refreshed: ChecklistItem) {
        titleText = refreshed.text
        val desc = refreshed.description.orEmpty()
        descriptionText = desc
        if (desc.isNotEmpty()) localDescription = desc
        prioritySelection = refreshed.priority
        scoreText = refreshed.score?.toString().orEmpty()
        startDateText = refreshed.startDate.orEmpty()
        targetDateText = refreshed.targetDate.orEmpty()
        estimatedTimeText = refreshed.estimatedTime?.toString().orEmpty()
        subtasks = refreshed.children.orEmpty()
        currentStatusId = refreshed.status
        metadataItem = refreshed
    }

    fun dismissWithOptionalSave() {
        if (!hasUnsavedChanges()) {
            onDismiss()
            return
        }
        showDiscardConfirm = true
    }

    fun savePendingAndDismiss() {
        scope.launch {
            saving = true
            val titleChanged = titleText.trim() != item.text.trim() && titleText.trim().isNotEmpty()
            if (titleChanged) {
                actions.updateTitle(titleText)
                    .onFailure {
                        saving = false
                        onError()
                        return@launch
                    }
            }
            if (detailsChanged()) {
                if (richFieldsSupported) {
                    val score = parseOptionalDouble(scoreText)
                    val estimated = parseOptionalDouble(estimatedTimeText)
                    if ((scoreText.isNotBlank() && score == null) || (estimatedTimeText.isNotBlank() && estimated == null)) {
                        saving = false
                        onError()
                        return@launch
                    }
                    localDescription = descriptionText
                    actions.updateTaskDetails(
                        description = descriptionText,
                        priority = prioritySelection,
                        score = score,
                        startDate = startDateText.trim().ifBlank { null },
                        targetDate = targetDateText.trim().ifBlank { null },
                        estimatedTime = estimated,
                    ).onFailure {
                        saving = false
                        onError()
                        return@launch
                    }
                } else {
                    localDescription = descriptionText
                    actions.updateDescription(descriptionText)
                        .onFailure {
                            saving = false
                            onError()
                            return@launch
                        }
                }
            }
            saving = false
            showDiscardConfirm = false
            onDismiss()
        }
    }

    LaunchedEffect(item) {
        applyRefreshedItem(item)
    }

    fun runAction(block: suspend () -> Result<Unit>) {
        if (saving) return
        scope.launch {
            saving = true
            block()
                .onSuccess {
                    actions.currentItem()?.let { applyRefreshedItem(it) }
                }
                .onFailure { onError() }
            saving = false
        }
    }

    fun saveTaskDetails() {
        if (richFieldsSupported) {
            val score = parseOptionalDouble(scoreText)
            val estimated = parseOptionalDouble(estimatedTimeText)
            if ((scoreText.isNotBlank() && score == null) || (estimatedTimeText.isNotBlank() && estimated == null)) {
                onError()
                return
            }
            runAction {
                localDescription = descriptionText
                actions.updateTaskDetails(
                    description = descriptionText,
                    priority = prioritySelection,
                    score = score,
                    startDate = startDateText.trim().ifBlank { null },
                    targetDate = targetDateText.trim().ifBlank { null },
                    estimatedTime = estimated,
                )
            }
        } else {
            runAction {
                localDescription = descriptionText
                actions.updateDescription(descriptionText)
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            message =
                stringResource(
                    R.string.delete_task_named_confirm,
                    titleText.ifBlank { stringResource(R.string.untitled) },
                ),
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                scope.launch {
                    actions.deleteItem()
                        .onSuccess {
                            onDeleted()
                            onDismiss()
                        }
                        .onFailure { onError() }
                }
            },
        )
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(stringResource(R.string.kanban_unsaved_changes_title)) },
            text = { Text(stringResource(R.string.kanban_unsaved_changes_message)) },
            confirmButton = {
                TextButton(onClick = { savePendingAndDismiss() }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        onDismiss()
                    },
                ) {
                    Text(stringResource(R.string.discard))
                }
            },
        )
    }

    Dialog(
        onDismissRequest = { dismissWithOptionalSave() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = titleText,
                            onValueChange = { titleText = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.untitled)) },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { dismissWithOptionalSave() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            enabled = !saving && titleText.trim().isNotEmpty(),
                            onClick = {
                                runAction { actions.updateTitle(titleText) }
                            },
                        ) {
                            Text(stringResource(R.string.save))
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DeleteDropdownMenuItem(
                                    onClick = {
                                        menuExpanded = false
                                        showDeleteConfirm = true
                                    },
                                )
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!richFieldsSupported) {
                    if (item.description.isNullOrBlank() && localDescription.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.kanban_item_description_saved_local),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (item.description.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.kanban_item_description_server_limit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.kanban_item_description)) },
                    placeholder = { Text(stringResource(R.string.kanban_item_description_placeholder)) },
                    minLines = 3,
                )
                if (richFieldsSupported) {
                    KanbanItemRichFieldsSection(
                        prioritySelection = prioritySelection,
                        onPriorityChange = { prioritySelection = it },
                        scoreText = scoreText,
                        onScoreTextChange = { scoreText = it },
                        startDateText = startDateText,
                        onStartDateTextChange = { startDateText = it },
                        targetDateText = targetDateText,
                        onTargetDateTextChange = { targetDateText = it },
                        estimatedTimeText = estimatedTimeText,
                        onEstimatedTimeTextChange = { estimatedTimeText = it },
                        enabled = !saving,
                    )
                    KanbanItemMetadataSection(item = metadataItem, taskStatuses = taskStatuses)
                } else {
                    TextButton(
                        enabled = !saving,
                        onClick = { saveTaskDetails() },
                    ) {
                        Text(stringResource(R.string.kanban_item_save_description))
                    }
                }

                Text(
                    text = stringResource(R.string.kanban_item_status),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (!statusMoveEnabled) {
                    Text(
                        text = stringResource(R.string.kanban_move_online_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    taskStatuses.forEach { status ->
                        FilterChip(
                            selected = status.id.equals(currentStatusId, ignoreCase = true),
                            onClick = {
                                if (!statusMoveEnabled) return@FilterChip
                                runAction { actions.moveToStatus(status.id) }
                            },
                            enabled = statusMoveEnabled && !saving,
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    KanbanStatusDot(colorHex = status.effectiveColorHex())
                                    Text(status.label)
                                }
                            },
                        )
                    }
                }

                KanbanSubtasksSection(
                    parentPath = itemPath,
                    subtasks = subtasks,
                    onCheck = { path -> runAction { actions.checkSubtask(path) } },
                    onUncheck = { path -> runAction { actions.uncheckSubtask(path) } },
                    onUpdateText = { path, text -> runAction { actions.updateSubtaskText(path, text) } },
                    onDelete = { path -> runAction { actions.deleteSubtask(path) } },
                    onAdd = { text -> runAction { actions.addSubtask(text) } },
                    showChecklistEmojis = showChecklistEmojis,
                )

                if (!richFieldsSupported) {
                    KanbanItemBlockedFieldsSection()
                }

                if (richFieldsSupported) {
                    TextButton(
                        enabled = !saving && detailsChanged(),
                        onClick = { saveTaskDetails() },
                    ) {
                        Text(stringResource(R.string.kanban_item_save_details))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = !saving,
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        Text(
                            text = stringResource(R.string.delete),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KanbanItemRichFieldsSection(
    prioritySelection: String?,
    onPriorityChange: (String?) -> Unit,
    scoreText: String,
    onScoreTextChange: (String) -> Unit,
    startDateText: String,
    onStartDateTextChange: (String) -> Unit,
    targetDateText: String,
    onTargetDateTextChange: (String) -> Unit,
    estimatedTimeText: String,
    onEstimatedTimeTextChange: (String) -> Unit,
    enabled: Boolean,
) {
    Text(
        text = stringResource(R.string.kanban_item_priority),
        style = MaterialTheme.typography.titleSmall,
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KanbanPriority.ALL.forEach { priority ->
            FilterChip(
                selected = prioritySelection.equals(priority, ignoreCase = true),
                onClick = { onPriorityChange(priority) },
                enabled = enabled,
                label = { Text(priorityLabel(priority)) },
            )
        }
    }
    OutlinedTextField(
        value = scoreText,
        onValueChange = onScoreTextChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(stringResource(R.string.kanban_item_score)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
    )
    KanbanDateField(
        label = stringResource(R.string.kanban_item_start_date),
        value = startDateText,
        onValueChange = onStartDateTextChange,
        enabled = enabled,
    )
    KanbanDateField(
        label = stringResource(R.string.kanban_item_target_date),
        value = targetDateText,
        onValueChange = onTargetDateTextChange,
        enabled = enabled,
    )
    OutlinedTextField(
        value = estimatedTimeText,
        onValueChange = onEstimatedTimeTextChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(stringResource(R.string.kanban_item_estimated_time)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
    )
}

@Composable
private fun KanbanItemMetadataSection(
    item: ChecklistItem,
    taskStatuses: List<TaskStatus>,
) {
    Text(
        text = stringResource(R.string.kanban_item_metadata),
        style = MaterialTheme.typography.titleSmall,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item.createdBy?.takeIf { it.isNotBlank() }?.let { user ->
            Text(
                text = stringResource(R.string.kanban_item_created_by, user),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        formatMetadataTimestamp(item.createdAt)?.let { formatted ->
            Text(
                text = stringResource(R.string.kanban_item_created_at, formatted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item.lastModifiedBy?.takeIf { it.isNotBlank() }?.let { user ->
            Text(
                text = stringResource(R.string.kanban_item_modified_by, user),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        formatMetadataTimestamp(item.lastModifiedAt)?.let { formatted ->
            Text(
                text = stringResource(R.string.kanban_item_modified_at, formatted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val history = item.history.orEmpty()
        if (history.isEmpty()) {
            Text(
                text = stringResource(R.string.kanban_item_history_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            history.forEach { entry ->
                Text(
                    text = formatHistoryEntry(entry, taskStatuses),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KanbanDateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { showPicker = true },
        enabled = enabled,
        label = { Text(label) },
        placeholder = { Text(stringResource(R.string.kanban_item_pick_date)) },
        trailingIcon = {
            if (value.isNotBlank() && enabled) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.kanban_item_clear_date))
                }
            }
        },
    )
    if (showPicker) {
        val state =
            rememberDatePickerState(
                initialSelectedDateMillis = isoDateToMillis(value),
            )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            onValueChange(millisToIsoDate(millis))
                        }
                        showPicker = false
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun KanbanItemBlockedFieldsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KanbanBlockedFieldRow(label = stringResource(R.string.kanban_item_priority))
        KanbanBlockedFieldRow(label = stringResource(R.string.kanban_item_score))
        KanbanBlockedFieldRow(label = stringResource(R.string.kanban_item_start_date))
        KanbanBlockedFieldRow(label = stringResource(R.string.kanban_item_target_date))
        KanbanBlockedFieldRow(label = stringResource(R.string.kanban_item_estimated_time))
        KanbanBlockedFieldRow(label = stringResource(R.string.kanban_item_metadata))
    }
}

@Composable
private fun KanbanBlockedFieldRow(label: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        Text(
            text = stringResource(R.string.kanban_item_field_requires_server),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        OutlinedTextField(
            value = "",
            onValueChange = {},
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            enabled = false,
            placeholder = { Text(stringResource(R.string.kanban_item_field_unavailable)) },
        )
    }
}

@Composable
private fun priorityLabel(priority: String): String =
    when (priority) {
        KanbanPriority.CRITICAL -> stringResource(R.string.kanban_item_priority_critical)
        KanbanPriority.HIGH -> stringResource(R.string.kanban_item_priority_high)
        KanbanPriority.MEDIUM -> stringResource(R.string.kanban_item_priority_medium)
        KanbanPriority.LOW -> stringResource(R.string.kanban_item_priority_low)
        else -> stringResource(R.string.kanban_item_priority_none)
    }

@Composable
private fun formatHistoryEntry(
    entry: ItemStatusChange,
    taskStatuses: List<TaskStatus>,
): String {
    val statusLabel =
        taskStatuses.firstOrNull { it.id.equals(entry.status, ignoreCase = true) }?.label
            ?: entry.status.orEmpty()
    val whenLabel = formatMetadataTimestamp(entry.timestamp) ?: entry.timestamp.orEmpty()
    val user = entry.user.orEmpty()
    return stringResource(R.string.kanban_item_history_entry, user, statusLabel, whenLabel)
}

private fun parseOptionalDouble(text: String): Double? =
    text.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

private fun millisToIsoDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()

private fun isoDateToMillis(iso: String): Long? =
    runCatching {
        LocalDate.parse(iso.trim()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }.getOrNull()

private fun formatMetadataTimestamp(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val formatter =
        DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
    return try {
        OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()).format(formatter)
    } catch (_: DateTimeParseException) {
        try {
            Instant.parse(raw).atZone(ZoneId.systemDefault()).format(formatter)
        } catch (_: DateTimeParseException) {
            try {
                LocalDate.parse(raw).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            } catch (_: DateTimeParseException) {
                raw
            }
        }
    }
}
