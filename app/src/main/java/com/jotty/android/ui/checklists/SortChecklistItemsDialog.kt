package com.jotty.android.ui.checklists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import com.jotty.android.R
import com.jotty.android.data.api.ChecklistItemSortDirection
import com.jotty.android.data.api.ChecklistItemSortMode

@Composable
fun SortChecklistItemsDialog(
    onDismiss: () -> Unit,
    onConfirm: (sort: String, direction: String) -> Unit,
) {
    var sort by remember { mutableStateOf(ChecklistItemSortMode.ALPHABETICAL) }
    var direction by remember { mutableStateOf(ChecklistItemSortDirection.ASC) }
    val showDirection = sort != ChecklistItemSortMode.SHUFFLE

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sort_checklist_items)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SortOptionRow(
                    label = stringResource(R.string.sort_items_alphabetical),
                    selected = sort == ChecklistItemSortMode.ALPHABETICAL,
                    onClick = { sort = ChecklistItemSortMode.ALPHABETICAL },
                )
                SortOptionRow(
                    label = stringResource(R.string.sort_items_created),
                    selected = sort == ChecklistItemSortMode.CREATED,
                    onClick = { sort = ChecklistItemSortMode.CREATED },
                )
                SortOptionRow(
                    label = stringResource(R.string.sort_items_modified),
                    selected = sort == ChecklistItemSortMode.MODIFIED,
                    onClick = { sort = ChecklistItemSortMode.MODIFIED },
                )
                SortOptionRow(
                    label = stringResource(R.string.sort_items_shuffle),
                    selected = sort == ChecklistItemSortMode.SHUFFLE,
                    onClick = { sort = ChecklistItemSortMode.SHUFFLE },
                )
                if (showDirection) {
                    Text(
                        text = stringResource(R.string.sort_items_direction),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SortOptionRow(
                        label = stringResource(R.string.sort_items_asc),
                        selected = direction == ChecklistItemSortDirection.ASC,
                        onClick = { direction = ChecklistItemSortDirection.ASC },
                    )
                    SortOptionRow(
                        label = stringResource(R.string.sort_items_desc),
                        selected = direction == ChecklistItemSortDirection.DESC,
                        onClick = { direction = ChecklistItemSortDirection.DESC },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        sort,
                        if (sort == ChecklistItemSortMode.SHUFFLE) {
                            ChecklistItemSortDirection.ASC
                        } else {
                            direction
                        },
                    )
                },
            ) {
                Text(stringResource(R.string.sort_checklist_items_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun SortOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.RadioButton,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}
