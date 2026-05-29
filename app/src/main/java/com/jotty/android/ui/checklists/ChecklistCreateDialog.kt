package com.jotty.android.ui.checklists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.data.api.API_CATEGORY_UNCATEGORIZED
import com.jotty.android.ui.common.CategorySelector

/**
 * Dialog for creating a checklist with a title, project/task toggle, and category.
 * Reused by the online and offline checklists screens.
 */
@Composable
fun ChecklistCreateDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, isProjectType: Boolean, category: String) -> Unit,
    categorySuggestions: List<String> = emptyList(),
) {
    var title by remember { mutableStateOf("") }
    var isProjectType by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("") }
    val untitled = stringResource(R.string.untitled)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_checklist)) },
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Checkbox(
                        checked = isProjectType,
                        onCheckedChange = { isProjectType = it },
                    )
                    Text(
                        stringResource(R.string.task_project_sub_tasks),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        title.ifBlank { untitled },
                        isProjectType,
                        category.ifBlank { API_CATEGORY_UNCATEGORIZED },
                    )
                },
            ) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
