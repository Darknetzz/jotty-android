package com.jotty.android.ui.notes

import android.content.Context
import android.content.Intent
import com.jotty.android.data.api.CreateNoteRequest
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import com.jotty.android.data.encryption.NoteDecryptionSession
import com.jotty.android.data.encryption.NoteEncryption
import com.jotty.android.data.local.OfflineNotesRepository
import com.jotty.android.util.AppLog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult

/** Plain note body suitable for text export/share from the list (not ciphertext). */
internal fun notePlainTextForListShare(note: Note): String? {
    val encrypted = note.encrypted == true || NoteEncryption.isEncrypted(note.content)
    return if (encrypted) {
        NoteDecryptionSession.get(note.id)?.trim()?.takeIf { it.isNotBlank() }
    } else {
        note.content.trim().takeIf { it.isNotBlank() }
    }
}

internal fun buildNoteListShareText(note: Note): String? {
    val encrypted = note.encrypted == true || NoteEncryption.isEncrypted(note.content)
    val body = notePlainTextForListShare(note)
    if (encrypted && body == null) return null
    val title = note.title.trim().takeIf { it.isNotBlank() }
    return when {
        title != null && body != null -> "# $title\n\n$body"
        title != null -> title
        body != null -> body
        else -> null
    }
}

internal fun shareNoteTextExport(
    context: Context,
    note: Note,
    chooserTitle: String,
): Boolean {
    val shareText = buildNoteListShareText(note) ?: return false
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, note.title)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
    return true
}

internal suspend fun deleteOfflineNoteWithUndo(
    note: Note,
    offlineRepository: OfflineNotesRepository,
    snackbarHostState: SnackbarHostState,
    noteDeletedMsg: String,
    undoActionLabel: String,
    deleteFailedMsg: String,
    saveFailedMsg: String,
    savedLocallyMsg: String,
    isOnline: Boolean,
    onClearSelectionIfNeeded: () -> Unit,
) {
    val result = offlineRepository.deleteNote(note.id)
    if (result.isFailure) {
        snackbarHostState.showSnackbar(deleteFailedMsg)
        return
    }
    onClearSelectionIfNeeded()
    val snackbarResult =
        snackbarHostState.showSnackbar(
            message = noteDeletedMsg,
            actionLabel = undoActionLabel,
        )
    if (snackbarResult == SnackbarResult.ActionPerformed) {
        val undoResult =
            offlineRepository.createNote(
                title = note.title,
                content = note.content,
                category = note.category,
            )
        when {
            undoResult.isFailure -> snackbarHostState.showSnackbar(saveFailedMsg)
            !isOnline -> snackbarHostState.showSnackbar(savedLocallyMsg)
        }
    } else if (!isOnline) {
        snackbarHostState.showSnackbar(savedLocallyMsg)
    }
}

internal suspend fun deleteOnlineNoteWithUndo(
    note: Note,
    api: JottyApi,
    snackbarHostState: SnackbarHostState,
    noteDeletedMsg: String,
    undoActionLabel: String,
    deleteFailedMsg: String,
    saveFailedMsg: String,
    onRemovedFromList: () -> Unit,
    onReload: () -> Unit,
) {
    try {
        api.deleteNote(note.id)
        onRemovedFromList()
        val snackbarResult =
            snackbarHostState.showSnackbar(
                message = noteDeletedMsg,
                actionLabel = undoActionLabel,
                duration = SnackbarDuration.Short,
            )
        if (snackbarResult == SnackbarResult.ActionPerformed) {
            try {
                val resp =
                    api.createNote(
                        CreateNoteRequest(
                            title = note.title,
                            content = note.content,
                            category = note.category,
                        ),
                    )
                if (resp.success) {
                    onReload()
                } else {
                    snackbarHostState.showSnackbar(saveFailedMsg)
                }
            } catch (e: Exception) {
                AppLog.e("notes", "Undo delete failed", e)
                snackbarHostState.showSnackbar(saveFailedMsg)
            }
        }
    } catch (e: Exception) {
        AppLog.e("notes", "Delete note failed", e)
        snackbarHostState.showSnackbar(deleteFailedMsg)
    }
}
