package com.jotty.android.data.repository

import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import com.jotty.android.data.local.OfflineChecklistsRepository
import com.jotty.android.data.local.OfflineNotesRepository
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction for notes list data — online API or offline Room cache.
 * Enables a single list ViewModel/screen to switch backends without duplicating UI.
 */
interface NotesListDataSource {
    fun getNotesFlow(): Flow<List<Note>>

    suspend fun searchNotes(query: String): List<Note>

    suspend fun getNotesByCategory(category: String): List<Note>

    suspend fun createNote(
        title: String,
        content: String,
        category: String,
    ): Result<Note>

    suspend fun deleteNote(noteId: String): Result<Unit>
}

/**
 * Abstraction for checklists list data — online API or offline Room cache.
 */
interface ChecklistsListDataSource {
    fun getChecklistsFlow(): Flow<List<Checklist>>

    suspend fun createChecklist(
        title: String,
        type: String,
        category: String,
    ): Result<Checklist>

    suspend fun deleteChecklist(checklistId: String): Result<Unit>
}

class OnlineNotesListDataSource(
    private val api: JottyApi,
) : NotesListDataSource {
    override fun getNotesFlow(): Flow<List<Note>> = error("Online mode uses ViewModel fetch; use [NotesViewModel] directly")

    override suspend fun searchNotes(query: String): List<Note> =
        api.getNotes(search = query).notes.orEmpty()

    override suspend fun getNotesByCategory(category: String): List<Note> =
        api.getNotes(category = category).notes.orEmpty()

    override suspend fun createNote(
        title: String,
        content: String,
        category: String,
    ): Result<Note> =
        runCatching {
            val response =
                api.createNote(
                    com.jotty.android.data.api.CreateNoteRequest(
                        title = title,
                        content = content,
                        category = category,
                    ),
                )
            response.data ?: error("Create failed")
        }

    override suspend fun deleteNote(noteId: String): Result<Unit> =
        runCatching {
            val response = api.deleteNote(noteId)
            if (!response.success) error("Delete failed")
        }
}

class OfflineNotesListDataSource(
    private val repository: OfflineNotesRepository,
) : NotesListDataSource {
    override fun getNotesFlow(): Flow<List<Note>> = repository.getNotesFlow()

    override suspend fun searchNotes(query: String): List<Note> = repository.searchNotes(query)

    override suspend fun getNotesByCategory(category: String): List<Note> = repository.getNotesByCategory(category)

    override suspend fun createNote(
        title: String,
        content: String,
        category: String,
    ): Result<Note> = repository.createNote(title, content, category)

    override suspend fun deleteNote(noteId: String): Result<Unit> = repository.deleteNote(noteId).map { }
}

class OnlineChecklistsListDataSource(
    private val api: JottyApi,
) : ChecklistsListDataSource {
    override fun getChecklistsFlow(): Flow<List<Checklist>> =
        error("Online mode uses ViewModel fetch; use [ChecklistsViewModel] directly")

    override suspend fun createChecklist(
        title: String,
        type: String,
        category: String,
    ): Result<Checklist> =
        runCatching {
            val response =
                api.createChecklist(
                    com.jotty.android.data.api.CreateChecklistRequest(
                        title = title,
                        type = type,
                        category = category,
                    ),
                )
            response.data ?: error("Create failed")
        }

    override suspend fun deleteChecklist(checklistId: String): Result<Unit> =
        runCatching {
            val response = api.deleteChecklist(checklistId)
            if (!response.success) error("Delete failed")
        }
}

class OfflineChecklistsListDataSource(
    private val repository: OfflineChecklistsRepository,
) : ChecklistsListDataSource {
    override fun getChecklistsFlow(): Flow<List<Checklist>> = repository.getChecklistsFlow()

    override suspend fun createChecklist(
        title: String,
        type: String,
        category: String,
    ): Result<Checklist> = repository.createChecklist(title, type, category)

    override suspend fun deleteChecklist(checklistId: String): Result<Unit> =
        repository.deleteChecklist(checklistId).map { }
}
