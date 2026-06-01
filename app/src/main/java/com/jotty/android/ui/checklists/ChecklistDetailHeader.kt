package com.jotty.android.ui.checklists

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.ui.common.CategorySelector
import com.jotty.android.ui.common.ConfirmDeleteDialog
import com.jotty.android.ui.common.ConfirmDiscardPendingSyncDialog
import com.jotty.android.ui.common.DeleteDropdownMenuItem
import com.jotty.android.ui.common.DiscardPendingSyncDropdownMenuItem
import com.jotty.android.ui.common.RenameDropdownMenuItem

@Composable
fun ChecklistDetailHeader(
    title: String,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onDiscardPendingSync: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val displayTitle = title.ifBlank { stringResource(R.string.untitled) }
    val deleteConfirmMessage = stringResource(R.string.delete_checklist_confirm, displayTitle)

    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            message = deleteConfirmMessage,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
        )
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Text(
            text = displayTitle,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            RenameDropdownMenuItem(
                onClick = {
                    menuExpanded = false
                    onRename()
                },
            )
            if (onDiscardPendingSync != null) {
                DiscardPendingSyncDropdownMenuItem(
                    onClick = {
                        menuExpanded = false
                        onDiscardPendingSync()
                    },
                )
            }
            DeleteDropdownMenuItem(
                onClick = {
                    menuExpanded = false
                    showDeleteConfirm = true
                },
            )
        }
    }
}

@Composable
fun ChecklistRenameDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, category: String) -> Unit,
    initialCategory: String = "",
    categorySuggestions: List<String> = emptyList(),
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var category by remember(initialCategory) { mutableStateOf(initialCategory) }
    val untitled = stringResource(R.string.untitled)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_checklist)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                CategorySelector(
                    category = category,
                    onCategoryChange = { category = it },
                    suggestions = categorySuggestions,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        title.ifBlank { untitled },
                        category.ifBlank { com.jotty.android.data.api.API_CATEGORY_UNCATEGORIZED },
                    )
                },
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
