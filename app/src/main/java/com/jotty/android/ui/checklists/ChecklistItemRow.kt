package com.jotty.android.ui.checklists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.ui.common.ConfirmDeleteDialog
import sh.calvin.reorderable.ReorderableCollectionItemScope

@Composable
fun ChecklistItemRow(
    item: ChecklistItem,
    itemKey: String,
    editingItemKey: String?,
    onEditingItemKeyChange: (String?) -> Unit,
    depth: Int = 0,
    isProject: Boolean = false,
    onCheck: () -> Unit,
    onUncheck: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (String) -> Unit,
    onAddSubItem: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    reorderableScope: ReorderableCollectionItemScope? = null,
    onDragStarted: (() -> Unit)? = null,
    onDragStopped: (() -> Unit)? = null,
    actionIconSize: Dp = 48.dp,
    actionGlyphSize: Dp = 22.dp,
    showChecklistEmojis: Boolean = true,
) {
    val indent = (depth * 20).dp
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val taskLabel = item.text.ifBlank { stringResource(R.string.item_placeholder) }
    val isEditing = editingItemKey == itemKey
    var editText by remember(item.text, isEditing) { mutableStateOf(item.text) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(isEditing, itemKey) {
        if (isEditing) focusRequester.requestFocus()
    }

    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            message = stringResource(R.string.delete_task_named_confirm, taskLabel),
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(indent))
        if (reorderableScope != null) {
            IconButton(
                onClick = {},
                modifier =
                    with(reorderableScope) {
                        Modifier
                            .size(actionIconSize)
                            .draggableHandle(
                                onDragStarted = { onDragStarted?.invoke() },
                                onDragStopped = { onDragStopped?.invoke() },
                            )
                    },
            ) {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = stringResource(R.string.cd_drag_to_reorder),
                    modifier = Modifier.size(actionGlyphSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Checkbox(
            checked = item.completed,
            onCheckedChange = { if (it) onCheck() else onUncheck() },
        )
        if (isEditing) {
            OutlinedTextField(
                value = editText,
                onValueChange = { editText = it },
                modifier =
                    Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            val trimmed = editText.trim()
                            if (trimmed.isNotBlank()) onUpdate(trimmed)
                            onEditingItemKeyChange(null)
                        },
                    ),
            )
        } else {
            val displayText =
                checklistDisplayText(item.text, showChecklistEmojis)
                    .ifBlank { stringResource(R.string.item_placeholder) }
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.completed) TextDecoration.LineThrough else null,
                color =
                    if (item.completed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable(role = Role.Button) {
                            onEditingItemKeyChange(itemKey)
                            editText = item.text
                        },
            )
        }
        val hasSecondaryActions = onMoveUp != null || onMoveDown != null || (isProject && depth == 0 && onAddSubItem != null)
        if (hasSecondaryActions) {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(actionIconSize)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    modifier = Modifier.size(actionGlyphSize),
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                if (onMoveUp != null) {
                    val moveUpLabel = stringResource(R.string.cd_move_item_up)
                    DropdownMenuItem(
                        text = { Text(moveUpLabel) },
                        onClick = {
                            menuExpanded = false
                            onMoveUp()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = moveUpLabel,
                            )
                        },
                    )
                }
                if (onMoveDown != null) {
                    val moveDownLabel = stringResource(R.string.cd_move_item_down)
                    DropdownMenuItem(
                        text = { Text(moveDownLabel) },
                        onClick = {
                            menuExpanded = false
                            onMoveDown()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = moveDownLabel,
                            )
                        },
                    )
                }
                if (isProject && depth == 0 && onAddSubItem != null) {
                    val addSubLabel = stringResource(R.string.add_sub_task)
                    DropdownMenuItem(
                        text = { Text(addSubLabel) },
                        onClick = {
                            menuExpanded = false
                            onAddSubItem()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = addSubLabel,
                            )
                        },
                    )
                }
            }
        }
        IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(actionIconSize)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete_task),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(actionGlyphSize),
            )
        }
    }
}
