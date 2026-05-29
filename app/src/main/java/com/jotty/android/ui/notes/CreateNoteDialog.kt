package com.jotty.android.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.data.api.API_CATEGORY_UNCATEGORIZED
import com.jotty.android.ui.common.CategorySelector

/**
 * Dialog for creating a note with a title, optional initial content, and category.
 * Reused by the online and offline notes screens.
 */
@Composable
fun CreateNoteDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, content: String, category: String) -> Unit,
    categorySuggestions: List<String> = emptyList(),
    initialTitle: String = "",
    initialContent: String = "",
) {
    var title by remember { mutableStateOf(initialTitle) }
    var content by remember { mutableStateOf(initialContent) }
    var category by remember { mutableStateOf("") }
    val untitled = stringResource(R.string.untitled)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_note)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.note_content_optional)) },
                    placeholder = { Text(stringResource(R.string.write_your_note)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                    minLines = 3,
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
                    onCreate(
                        title.ifBlank { untitled },
                        content,
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
