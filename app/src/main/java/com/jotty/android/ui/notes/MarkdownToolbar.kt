package com.jotty.android.ui.notes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.jotty.android.R

/**
 * Wraps the current selection (or cursor) in [marker] on both sides. With an empty selection the
 * cursor is placed between the markers so the user can type inside.
 */
fun wrapSelection(
    value: TextFieldValue,
    marker: String,
): TextFieldValue {
    val sel = value.selection
    val start = sel.min
    val end = sel.max
    val text = value.text
    val selected = text.substring(start, end)
    val newText = text.substring(0, start) + marker + selected + marker + text.substring(end)
    val cursor = if (start == end) start + marker.length else end + marker.length * 2
    return value.copy(text = newText, selection = TextRange(cursor))
}

/** Inserts [prefix] at the start of the line containing the cursor (for headings, lists, quotes). */
fun prefixLine(
    value: TextFieldValue,
    prefix: String,
): TextFieldValue {
    val text = value.text
    val cursor = value.selection.min
    val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
    val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
    return value.copy(text = newText, selection = TextRange(cursor + prefix.length))
}

/** Inserts a Markdown link template, selecting the placeholder text so it can be overwritten. */
fun insertLink(value: TextFieldValue): TextFieldValue {
    val sel = value.selection
    val text = value.text
    val label = if (sel.min != sel.max) text.substring(sel.min, sel.max) else "text"
    val snippet = "[$label](url)"
    val newText = text.substring(0, sel.min) + snippet + text.substring(sel.max)
    // Select the "url" placeholder for quick replacement.
    val urlStart = sel.min + snippet.indexOf("url")
    return value.copy(text = newText, selection = TextRange(urlStart, urlStart + 3))
}

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
        IconButton(onClick = { onValueChange(wrapSelection(value, "**")) }) {
            Icon(Icons.Default.FormatBold, contentDescription = stringResource(R.string.md_bold))
        }
        IconButton(onClick = { onValueChange(wrapSelection(value, "*")) }) {
            Icon(Icons.Default.FormatItalic, contentDescription = stringResource(R.string.md_italic))
        }
        IconButton(onClick = { onValueChange(wrapSelection(value, "`")) }) {
            Icon(Icons.Default.Code, contentDescription = stringResource(R.string.md_code))
        }
        IconButton(onClick = { onValueChange(prefixLine(value, "# ")) }) {
            Icon(Icons.Default.Title, contentDescription = stringResource(R.string.md_heading))
        }
        IconButton(onClick = { onValueChange(prefixLine(value, "- ")) }) {
            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = stringResource(R.string.md_list))
        }
        IconButton(onClick = { onValueChange(prefixLine(value, "> ")) }) {
            Icon(Icons.Default.FormatQuote, contentDescription = stringResource(R.string.md_quote))
        }
        IconButton(onClick = { onValueChange(insertLink(value)) }) {
            Icon(Icons.Default.Link, contentDescription = stringResource(R.string.md_link))
        }
    }
}
