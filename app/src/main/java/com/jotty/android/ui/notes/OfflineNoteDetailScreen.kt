package com.jotty.android.ui.notes

import androidx.compose.runtime.*
import coil.ImageLoader
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import com.jotty.android.data.api.UpdateNoteRequest
import com.jotty.android.data.local.OfflineNotesRepository
import com.jotty.android.util.AppLog
import kotlinx.coroutines.launch

/**
 * Wrapper for NoteDetailScreen that uses OfflineNotesRepository.
 * Provides offline editing with automatic sync.
 */
@Composable
fun OfflineNoteDetailScreen(
    note: Note,
    offlineRepository: OfflineNotesRepository,
    api: JottyApi,
    onBack: () -> Unit,
    onUpdate: (Note) -> Unit,
    onDelete: () -> Unit,
    onSaveFailed: () -> Unit = {},
    onSavedLocally: () -> Unit = {},
    debugLoggingEnabled: Boolean = false,
    imageLoader: ImageLoader? = null,
    isOnline: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    
    // Create a wrapper API that uses the offline repository
    val offlineApi = remember {
        object : JottyApi {
            override suspend fun getNotes(category: String?, search: String?) =
                throw UnsupportedOperationException("Use offline repository")
            
            override suspend fun createNote(request: com.jotty.android.data.api.CreateNoteRequest) =
                throw UnsupportedOperationException("Use offline repository")
            
            override suspend fun updateNote(noteId: String, request: UpdateNoteRequest): com.jotty.android.data.api.ApiResponse<Note> {
                AppLog.d("OfflineNoteDetailScreen", "Updating note offline: $noteId")
                val result = offlineRepository.updateNote(
                    noteId = noteId,
                    title = request.title,
                    content = request.content ?: "",
                    category = request.category ?: note.category
                )
                
                return if (result.isSuccess) {
                    val updated = result.getOrThrow()
                    if (!isOnline) {
                        // Show local save message if offline
                        scope.launch { onSavedLocally() }
                    }
                    com.jotty.android.data.api.ApiResponse(
                        success = true,
                        message = "Note updated",
                        data = updated
                    )
                } else {
                    throw result.exceptionOrNull() ?: Exception("Update failed")
                }
            }
            
            override suspend fun deleteNote(noteId: String): com.jotty.android.data.api.SuccessResponse {
                AppLog.d("OfflineNoteDetailScreen", "Deleting note offline: $noteId")
                val result = offlineRepository.deleteNote(noteId)
                
                return if (result.isSuccess) {
                    if (!isOnline) {
                        scope.launch { onSavedLocally() }
                    }
                    com.jotty.android.data.api.SuccessResponse(
                        success = true,
                        message = "Note deleted"
                    )
                } else {
                    throw result.exceptionOrNull() ?: Exception("Delete failed")
                }
            }
            
            override suspend fun getCategories() = api.getCategories()
            override suspend fun health() = api.health()
            override suspend fun getChecklists(category: String?) = api.getChecklists(category)
            override suspend fun createChecklist(request: com.jotty.android.data.api.CreateChecklistRequest) = api.createChecklist(request)
            override suspend fun updateChecklist(checklistId: String, request: com.jotty.android.data.api.UpdateChecklistRequest) = api.updateChecklist(checklistId, request)
            override suspend fun deleteChecklist(checklistId: String) = api.deleteChecklist(checklistId)
            override suspend fun getChecklistTasks(checklistId: String) = api.getChecklistTasks(checklistId)
            override suspend fun createChecklistTask(checklistId: String, request: com.jotty.android.data.api.CreateTaskRequest) = api.createChecklistTask(checklistId, request)
            override suspend fun updateChecklistTask(checklistId: String, taskId: String, request: com.jotty.android.data.api.UpdateTaskRequest) = api.updateChecklistTask(checklistId, taskId, request)
            override suspend fun deleteChecklistTask(checklistId: String, taskId: String) = api.deleteChecklistTask(checklistId, taskId)
            override suspend fun getSummary() = api.getSummary()
        }
    }
    
    // Use the original NoteDetailScreen with our wrapped API
    NoteDetailScreen(
        note = note,
        api = offlineApi,
        onBack = onBack,
        onUpdate = onUpdate,
        onDelete = { 
            scope.launch {
                offlineRepository.deleteNote(note.id)
                onDelete()
            }
        },
        onSaveFailed = onSaveFailed,
        debugLoggingEnabled = debugLoggingEnabled,
        imageLoader = imageLoader,
    )
}
