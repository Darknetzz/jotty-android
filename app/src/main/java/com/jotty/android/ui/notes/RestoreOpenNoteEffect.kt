package com.jotty.android.ui.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.jotty.android.data.api.Note

/** Reopens the note that was open if Android killed the app while it was in the background. */
@Composable
internal fun RestoreOpenNoteEffect(
    selectedNoteId: String?,
    resolveNote: suspend (String) -> Note?,
    onRestore: (Note) -> Unit,
) {
    var savedNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreChecked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val id = savedNoteId
        if (id != null && selectedNoteId == null) {
            resolveNote(id)?.let(onRestore)
        }
        restoreChecked = true
    }
    LaunchedEffect(selectedNoteId, restoreChecked) {
        if (restoreChecked) savedNoteId = selectedNoteId
    }
}
