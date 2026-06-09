package com.jotty.android.ui.checklists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.ui.common.ConfirmDeleteDialog

@Composable
fun KanbanSubtasksSection(
    parentPath: String,
    subtasks: List<ChecklistItem>,
    onCheck: (subPath: String) -> Unit,
    onUncheck: (subPath: String) -> Unit,
    onUpdateText: (subPath: String, text: String) -> Unit,
    onDelete: (subPath: String) -> Unit,
    onAdd: (text: String) -> Unit,
    modifier: Modifier = Modifier,
    showChecklistEmojis: Boolean = true,
) {
    var newSubtaskText by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.kanban_item_subtasks),
            style = MaterialTheme.typography.titleSmall,
        )
        if (subtasks.isEmpty()) {
            Text(
                text = stringResource(R.string.kanban_item_subtasks_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        } else {
            subtasks.forEachIndexed { index, subtask ->
                KanbanSubtaskRow(
                    subPath = kanbanChildPath(parentPath, index),
                    item = subtask,
                    onCheck = onCheck,
                    onUncheck = onUncheck,
                    onUpdateText = onUpdateText,
                    onDelete = onDelete,
                    showChecklistEmojis = showChecklistEmojis,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newSubtaskText,
                onValueChange = { newSubtaskText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.kanban_item_add_subtask_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            val trimmed = newSubtaskText.trim()
                            if (trimmed.isNotEmpty()) {
                                onAdd(trimmed)
                                newSubtaskText = ""
                                focusManager.clearFocus()
                            }
                        },
                    ),
            )
            IconButton(
                onClick = {
                    val trimmed = newSubtaskText.trim()
                    if (trimmed.isNotEmpty()) {
                        onAdd(trimmed)
                        newSubtaskText = ""
                    }
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
            }
        }
    }
}

@Composable
private fun KanbanSubtaskRow(
    subPath: String,
    item: ChecklistItem,
    onCheck: (String) -> Unit,
    onUncheck: (String) -> Unit,
    onUpdateText: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    showChecklistEmojis: Boolean = true,
) {
    var showDeleteConfirm by remember(subPath) { mutableStateOf(false) }
    var isEditing by remember(subPath) { mutableStateOf(false) }
    var editText by remember(item.text, isEditing) { mutableStateOf(item.text) }
    val label =
        checklistDisplayText(item.text, showChecklistEmojis)
            .ifBlank { stringResource(R.string.item_placeholder) }

    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            message = stringResource(R.string.delete_task_named_confirm, label),
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                onDelete(subPath)
            },
        )
    }

    fun saveEdit() {
        val trimmed = editText.trim()
        if (trimmed.isNotEmpty()) {
            onUpdateText(subPath, trimmed)
        }
        isEditing = false
    }

    fun cancelEdit() {
        editText = item.text
        isEditing = false
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = item.completed,
            onCheckedChange = { checked ->
                if (checked) onCheck(subPath) else onUncheck(subPath)
            },
        )
        if (isEditing) {
            OutlinedTextField(
                value = editText,
                onValueChange = { editText = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions(
                        onDone = { saveEdit() },
                    ),
            )
            IconButton(onClick = { saveEdit() }) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
            }
            IconButton(onClick = { cancelEdit() }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
            }
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
            )
        }
        if (!isEditing) {
            IconButton(
                onClick = {
                    isEditing = true
                    editText = item.text
                },
            ) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
