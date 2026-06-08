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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import com.jotty.android.R
import com.jotty.android.ui.common.CategorySelector
import com.jotty.android.util.noteContentContainsRawHtml

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
    val textScale = LocalReaderTextScale.current.coerceIn(0.75f, 1.5f)
    val baseStyle = MaterialTheme.typography.bodyLarge
    val editorTextStyle =
        remember(textScale, baseStyle) {
            val fontSize =
                if (baseStyle.fontSize.isUnspecified) {
                    16.sp
                } else {
                    (baseStyle.fontSize.value * textScale).sp
                }
            val lineHeight =
                if (baseStyle.lineHeight.isUnspecified) {
                    fontSize * 1.5f
                } else {
                    (baseStyle.lineHeight.value * textScale).sp
                }
            baseStyle.copy(fontSize = fontSize, lineHeight = lineHeight)
        }
    fun updateContentField(updated: TextFieldValue) {
        val adjusted = applyMarkdownContentChange(contentField, updated)
        contentField = adjusted
        onContentChange(adjusted.text)
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
        if (noteContentContainsRawHtml(contentField.text)) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = stringResource(R.string.note_html_save_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        MarkdownToolbar(
            value = contentField,
            onValueChange = { updateContentField(it) },
        )
        OutlinedTextField(
            value = contentField,
            onValueChange = { updateContentField(it) },
            textStyle = editorTextStyle,
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
