package com.jotty.android.ui.checklists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.data.api.CreateTaskStatusRequest
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.TaskStatus
import com.jotty.android.data.api.UpdateTaskStatusRequest

private data class EditableTaskStatus(
    val id: String,
    val isExisting: Boolean,
    val label: String,
    val color: String?,
    val autoComplete: Boolean?,
)

private val statusColorPalette =
    listOf(
        null,
        "#6b7280",
        "#3b82f6",
        "#10b981",
        "#f59e0b",
        "#ef4444",
        "#8b5cf6",
        "#ec4899",
    )

@Composable
fun ManageTaskStatusesDialog(
    statuses: List<TaskStatus>,
    onDismiss: () -> Unit,
    onSave: (List<TaskStatus>) -> Unit,
) {
    var rows by remember(statuses) {
        mutableStateOf(
            statuses.map {
                EditableTaskStatus(
                    id = it.id,
                    isExisting = true,
                    label = it.label,
                    color = it.color,
                    autoComplete = it.autoComplete,
                )
            },
        )
    }
    val supportsAutoComplete = statuses.any { it.autoComplete != null }
    val hasInvalidLabels = rows.any { it.label.trim().isEmpty() }
    val newStatusLabel = stringResource(R.string.kanban_new_status)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.kanban_manage_statuses)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                rows.forEachIndexed { index, row ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        rows =
                                            rows.toMutableList().also {
                                                val current = it.removeAt(index)
                                                it.add(index - 1, current)
                                            }
                                    }
                                },
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.cd_move_item_up))
                            }
                            IconButton(
                                onClick = {
                                    if (index < rows.lastIndex) {
                                        rows =
                                            rows.toMutableList().also {
                                                val current = it.removeAt(index)
                                                it.add(index + 1, current)
                                            }
                                    }
                                },
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.cd_move_item_down))
                            }
                            OutlinedTextField(
                                value = row.label,
                                onValueChange = { value ->
                                    rows = rows.toMutableList().also { it[index] = row.copy(label = value) }
                                },
                                label = { Text(stringResource(R.string.title)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            StatusColorSwatchPicker(
                                selectedHex = row.color,
                                onSelect = { color ->
                                    rows = rows.toMutableList().also { it[index] = row.copy(color = color) }
                                },
                            )
                            IconButton(
                                onClick = { rows = rows.toMutableList().also { it.removeAt(index) } },
                                enabled = rows.size > 1,
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        if (supportsAutoComplete) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Switch(
                                    checked = row.autoComplete == true,
                                    onCheckedChange = { checked ->
                                        rows = rows.toMutableList().also { it[index] = row.copy(autoComplete = checked) }
                                    },
                                )
                                Text(stringResource(R.string.kanban_status_auto_complete))
                            }
                        }
                    }
                }
                TextButton(
                    onClick = {
                        val key = "tmp-${System.currentTimeMillis()}-${rows.size}"
                        rows =
                            rows + EditableTaskStatus(
                                id = key,
                                isExisting = false,
                                label = newStatusLabel,
                                color = null,
                                autoComplete = if (supportsAutoComplete) false else null,
                            )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(
                        text = stringResource(R.string.kanban_add_status),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(resolveEditableStatuses(rows, statuses.map { it.id }, supportsAutoComplete))
                },
                enabled = rows.isNotEmpty() && !hasInvalidLabels,
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private fun resolveEditableStatuses(
    rows: List<EditableTaskStatus>,
    existingIds: List<String>,
    supportsAutoComplete: Boolean,
): List<TaskStatus> {
    val usedIds = existingIds.toMutableSet()
    return rows.mapIndexed { index, row ->
        val id =
            if (row.isExisting) {
                row.id
            } else {
                generateStatusId(row.label, usedIds)
            }
        usedIds.add(id)
        TaskStatus(
            id = id,
            label = row.label.trim(),
            color = row.color?.takeIf { it.isNotBlank() },
            order = index,
            autoComplete = if (supportsAutoComplete) row.autoComplete ?: false else null,
        )
    }
}

private fun generateStatusId(
    label: String,
    usedIds: Set<String>,
): String {
    val base = label.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "status" }
    var candidate = base
    var suffix = 2
    while (candidate in usedIds) {
        candidate = "${base}_$suffix"
        suffix += 1
    }
    return candidate
}

suspend fun saveTaskStatuses(
    api: JottyApi,
    taskId: String,
    previous: List<TaskStatus>,
    updated: List<TaskStatus>,
) {
    val previousMap = previous.associateBy { it.id }
    val supportsAutoComplete = previous.any { it.autoComplete != null } || updated.any { it.autoComplete != null }

    updated.forEachIndexed { order, status ->
        val current = status.copy(order = order)
        val existing = previousMap[current.id]
        if (existing == null) {
            api.createTaskStatus(
                taskId = taskId,
                body =
                    CreateTaskStatusRequest(
                        id = current.id,
                        label = current.label,
                        color = current.color,
                        order = current.order,
                        autoComplete = if (supportsAutoComplete) current.autoComplete else null,
                    ),
            )
        } else {
            api.updateTaskStatus(
                taskId = taskId,
                statusId = current.id,
                body =
                    UpdateTaskStatusRequest(
                        label = current.label,
                        color = current.color,
                        order = current.order,
                        autoComplete = if (supportsAutoComplete) current.autoComplete else null,
                    ),
            )
        }
    }

    val deleted = previous.map { it.id }.toSet() - updated.map { it.id }.toSet()
    deleted.forEach { statusId ->
        api.deleteTaskStatus(taskId = taskId, statusId = statusId)
    }
}

@Composable
private fun StatusColorSwatchPicker(
    selectedHex: String?,
    onSelect: (String?) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        statusColorPalette.forEach { hex ->
            val color = parseHexColorOrNull(hex) ?: MaterialTheme.colorScheme.surfaceVariant
            val selected = selectedHex.equals(hex, ignoreCase = true)
            val borderColor =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
            Row(
                modifier =
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(if (selected) 2.dp else 1.dp, borderColor, CircleShape)
                        .clickable { onSelect(hex) },
            ) {}
        }
    }
}
