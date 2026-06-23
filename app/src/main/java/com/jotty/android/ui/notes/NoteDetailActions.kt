package com.jotty.android.ui.notes

import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import com.jotty.android.data.api.UpdateNoteRequest
import com.jotty.android.data.api.normalizedForClient
import com.jotty.android.data.local.OfflineNotesRepository
import com.jotty.android.util.AppLog

/**
 * Note detail save/delete operations — implemented by online API or offline repository.
 */
interface NoteDetailActions {
    suspend fun updateNote(
        noteId: String,
        title: String,
        content: String,
        category: String,
        originalCategory: String = category,
    ): Result<Note>

    /** Re-reads the note from the server (or local DB when offline) after a save. */
    suspend fun fetchNote(noteId: String): Result<Note> =
        Result.failure(UnsupportedOperationException("fetchNote not supported"))

    suspend fun deleteNote(noteId: String): Result<Unit>
}

class ApiNoteDetailActions(
    private val api: JottyApi,
) : NoteDetailActions {
    override suspend fun updateNote(
        noteId: String,
        title: String,
        content: String,
        category: String,
        originalCategory: String,
    ): Result<Note> =
        runCatching {
            val response =
                api.updateNote(
                    noteId,
                    UpdateNoteRequest(
                        title = title,
                        content = content,
                        category = category,
                        originalCategory = originalCategory,
                    ),
                )
            if (response.success) {
                response.data
            } else {
                error("Update failed")
            }
        }

    override suspend fun fetchNote(noteId: String): Result<Note> =
        runCatching {
            val response = api.getNote(noteId)
            if (response.success && response.data != null) {
                response.data.normalizedForClient()
            } else {
                error("Fetch failed")
            }
        }

    override suspend fun deleteNote(noteId: String): Result<Unit> =
        runCatching {
            val response = api.deleteNote(noteId)
            if (!response.success) error("Delete failed")
        }
}

class OfflineNoteDetailActions(
    private val offlineRepository: OfflineNotesRepository,
    private val isOnline: () -> Boolean,
    private val onSavedLocally: () -> Unit,
) : NoteDetailActions {
    override suspend fun updateNote(
        noteId: String,
        title: String,
        content: String,
        category: String,
        originalCategory: String,
    ): Result<Note> {
        AppLog.d("OfflineNoteDetail", "Updating note offline: $noteId")
        val result =
            offlineRepository.updateNote(
                noteId = noteId,
                title = title,
                content = content,
                category = category,
            )
        if (result.isSuccess && !isOnline()) {
            onSavedLocally()
        }
        return result
    }

    override suspend fun fetchNote(noteId: String): Result<Note> =
        runCatching {
            offlineRepository.getNoteById(noteId)
                ?: error("Note not found")
        }

    override suspend fun deleteNote(noteId: String): Result<Unit> {
        AppLog.d("OfflineNoteDetail", "Deleting note offline: $noteId")
        val result = offlineRepository.deleteNote(noteId)
        if (result.isSuccess && !isOnline()) {
            onSavedLocally()
        }
        return result.map { }
    }
}
