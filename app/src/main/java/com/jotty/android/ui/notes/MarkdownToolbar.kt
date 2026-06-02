package com.jotty.android.ui.notes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import com.jotty.android.R

/** Horizontal scrolling row of Markdown formatting actions operating on [value]. */
@Composable
fun MarkdownToolbar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        IconButton(onClick = { onValueChange(toggleWrapSelection(value, "**")) }) {
            Icon(Icons.Default.FormatBold, contentDescription = stringResource(R.string.md_bold))
        }
        IconButton(onClick = { onValueChange(toggleWrapSelection(value, "*")) }) {
            Icon(Icons.Default.FormatItalic, contentDescription = stringResource(R.string.md_italic))
        }
        IconButton(onClick = { onValueChange(toggleWrapSelection(value, "`")) }) {
            Icon(Icons.Default.Code, contentDescription = stringResource(R.string.md_code))
        }
        IconButton(onClick = { onValueChange(toggleHeadingLine(value)) }) {
            Icon(Icons.Default.Title, contentDescription = stringResource(R.string.md_heading))
        }
        IconButton(onClick = { onValueChange(toggleBulletLine(value)) }) {
            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = stringResource(R.string.md_list))
        }
        IconButton(onClick = { onValueChange(toggleNumberedLine(value)) }) {
            Icon(Icons.Default.FormatListNumbered, contentDescription = stringResource(R.string.md_numbered_list))
        }
        IconButton(onClick = { onValueChange(toggleTaskLine(value)) }) {
            Icon(Icons.Default.CheckBox, contentDescription = stringResource(R.string.md_task_list))
        }
        IconButton(onClick = { onValueChange(toggleQuoteLine(value)) }) {
            Icon(Icons.Default.FormatQuote, contentDescription = stringResource(R.string.md_quote))
        }
        IconButton(onClick = { onValueChange(toggleLink(value)) }) {
            Icon(Icons.Default.Link, contentDescription = stringResource(R.string.md_link))
        }
    }
}
