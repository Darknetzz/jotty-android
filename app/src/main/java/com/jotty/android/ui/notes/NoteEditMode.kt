package com.jotty.android.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jotty.android.R

/** Note body editor: visual (WYSIWYG) or markdown source. */
enum class NoteEditMode {
    Visual,
    Markdown,
}

@Composable
internal fun NoteEditModeToggle(
    mode: NoteEditMode,
    onModeChange: (NoteEditMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = mode == NoteEditMode.Visual,
            onClick = { onModeChange(NoteEditMode.Visual) },
            label = { Text(stringResource(R.string.note_edit_mode_visual)) },
        )
        FilterChip(
            selected = mode == NoteEditMode.Markdown,
            onClick = { onModeChange(NoteEditMode.Markdown) },
            label = { Text(stringResource(R.string.note_edit_mode_markdown)) },
        )
    }
}
