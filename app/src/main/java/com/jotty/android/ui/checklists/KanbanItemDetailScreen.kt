package com.jotty.android.ui.checklists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jotty.android.R
import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.TaskStatus
import com.jotty.android.data.api.effectiveColorHex
import com.jotty.android.ui.common.ConfirmDeleteDialog
import com.jotty.android.ui.common.DeleteDropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Box
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
) {
    val scope = rememberCoroutineScope()
    var titleText by remember(itemPath, item.text) { mutableStateOf(item.text) }
    var descriptionText by remember(itemPath, item.description) { mutableStateOf(item.description.orEmpty()) }
    var localDescription by remember(itemPath) { mutableStateOf(item.description.orEmpty()) }
    var subtasks by remember(itemPath, item.children) { mutableStateOf(item.children.orEmpty()) }
    var currentStatusId by remember(itemPath, item.status) { mutableStateOf(item.status) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    fun hasUnsavedChanges(): Boolean {
        val titleChanged = titleText.trim() != item.text.trim() && titleText.trim().isNotEmpty()
        val descChanged = descriptionText.trim() != item.description.orEmpty().trim()
        return titleChanged || descChanged
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
            val descChanged = descriptionText.trim() != item.description.orEmpty().trim()
            if (titleChanged) {
                actions.updateTitle(titleText)
                    .onFailure {
                        saving = false
                        onError()
                        return@launch
                    }
            }
            if (descChanged) {
                localDescription = descriptionText
                actions.updateDescription(descriptionText)
                    .onFailure {
                        saving = false
                        onError()
                        return@launch
                    }
            }
            saving = false
            showDiscardConfirm = false
            onDismiss()
        }
    }

    LaunchedEffect(item) {
        titleText = item.text
        val desc = item.description.orEmpty()
        descriptionText = desc
        if (desc.isNotEmpty()) localDescription = desc
        subtasks = item.children.orEmpty()
        currentStatusId = item.status
    }

    fun runAction(block: suspend () -> Result<Unit>) {
        if (saving) return
        scope.launch {
            saving = true
            block()
                .onSuccess {
                    val refreshed = actions.currentItem()
                    if (refreshed != null) {
                        titleText = refreshed.text
                        val desc = refreshed.description.orEmpty()
                        if (desc.isNotEmpty()) {
                            descriptionText = desc
                            localDescription = desc
                        }
                        subtasks = refreshed.children.orEmpty()
                        currentStatusId = refreshed.status
                    }
                }
                .onFailure { onError() }
            saving = false
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
                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.kanban_item_description)) },
                    placeholder = { Text(stringResource(R.string.kanban_item_description_placeholder)) },
                    minLines = 3,
                )
                TextButton(
                    enabled = !saving,
                    onClick = {
                        runAction {
                            localDescription = descriptionText
                            actions.updateDescription(descriptionText)
                        }
                    },
                ) {
                    Text(stringResource(R.string.kanban_item_save_description))
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
                )

                KanbanItemBlockedFieldsSection()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = !saving,
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
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

@Composable
private fun KanbanItemBlockedFieldsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KanbanBlockedFieldRow(label = stringResource(R.string.kanban_item_priority))
        KanbanBlockedFieldRow(label = stringResource(R.string.kanban_item_score))
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
