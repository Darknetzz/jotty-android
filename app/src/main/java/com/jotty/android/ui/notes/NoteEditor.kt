package com.jotty.android.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.ui.common.CategorySelector

@Composable
internal fun NoteEditor(
    title: String,
    onTitleChange: (String) -> Unit,
    content: String,
    onContentChange: (String) -> Unit,
    category: String = "",
    onCategoryChange: ((String) -> Unit)? = null,
    categorySuggestions: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    // Track selection locally so the formatting toolbar can wrap/insert at the cursor, while still
    // exposing a plain-String API to callers via onContentChange.
    var contentField by remember { mutableStateOf(TextFieldValue(content, TextRange(content.length))) }
    LaunchedEffect(content) {
        if (content != contentField.text) {
            contentField = contentField.copy(text = content, selection = TextRange(content.length))
        }
    }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.title)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
        if (onCategoryChange != null) {
            CategorySelector(
                category = category,
                onCategoryChange = onCategoryChange,
                suggestions = categorySuggestions,
            )
        }
        MarkdownToolbar(
            value = contentField,
            onValueChange = {
                contentField = it
                onContentChange(it.text)
            },
        )
        OutlinedTextField(
            value = contentField,
            onValueChange = {
                contentField = it
                onContentChange(it.text)
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
            placeholder = { Text(stringResource(R.string.write_your_note)) },
            supportingText = {
                Text(
                    stringResource(R.string.markdown_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            minLines = 12,
            shape = RoundedCornerShape(12.dp),
        )
    }
}
