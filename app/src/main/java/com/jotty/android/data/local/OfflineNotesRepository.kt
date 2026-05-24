package com.jotty.android.data.local

import android.content.Context
import androidx.room.withTransaction
import com.jotty.android.R
import com.jotty.android.data.api.CreateNoteRequest
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import com.jotty.android.data.api.UpdateNoteRequest
import com.jotty.android.util.ApiErrorHelper
import com.jotty.android.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Repository that manages offline note storage and synchronization.
 * Coordinates between local Room database and remote Jotty API.
 */
class OfflineNotesRepository(
    context: Context,
    private val database: JottyDatabase,
    private val instanceId: String,
    private val api: JottyApi,
    /** When non-null, used instead of [checkConnectivity] for initial online state (e.g. unit tests). */
    initialOnlineOverride: Boolean? = null,
    /** Set false in tests to avoid registering a network callback. */
    private val useSharedConnectivity: Boolean = true,
) {
    private val appContext = context.applicationContext
    private val noteDao = database.noteDao()
    private val runtime =
        OfflineRepositoryLifecycle(
            context = context,
            initialOnlineOverride = initialOnlineOverride,
            useSharedConnectivity = useSharedConnectivity,
            logTag = TAG,
            instanceId = instanceId,
            onNetworkAvailable = { syncNotes() },
        )

    val isOnline: StateFlow<Boolean> = runtime.syncStatus.isOnline
    val isSyncing: StateFlow<Boolean> = runtime.syncStatus.isSyncing
    val conflictsDetected: StateFlow<Int> = runtime.syncStatus.conflictsDetected
    val lastSyncAttemptEpochMs: StateFlow<Long?> = runtime.syncStatus.lastSyncAttemptEpochMs
    val lastSyncSuccessEpochMs: StateFlow<Long?> = runtime.syncStatus.lastSyncSuccessEpochMs
    val lastSyncDurationText: StateFlow<String?> = runtime.syncStatus.lastSyncDurationText
    val lastSyncError: StateFlow<String?> = runtime.syncStatus.lastSyncError

    /**
     * Releases resources held by this repository: unregisters the network callback and
     * cancels the background coroutine scope. Must be called by the owner when it is
     * destroyed (e.g. [OfflineNotesViewModel.onCleared]) to prevent leaks.
     * Safe to call multiple times.
     */
    fun close() {
        runtime.close()
    }

    /**
     * Get all notes as Flow (observes changes).
     * Uses flowOn(IO) so Room queries run off the main thread.
     */
    fun getNotesFlow(): Flow<List<Note>> {
        return noteDao.getAllNotesFlow(instanceId)
            .map { entities -> entities.map { it.toNote() } }
            .flowOn(Dispatchers.IO)
    }

    /**
     * Local conflict copies are kept until users review and delete or merge them.
     */
    fun getConflictCopiesFlow(): Flow<List<Note>> {
        return noteDao.getAllNotesFlow(instanceId)
            .map { entities ->
                entities
                    .filter { it.title.endsWith(LOCAL_COPY_SUFFIX) }
                    .map { it.toNote() }
            }
            .flowOn(Dispatchers.IO)
    }

    /**
     * Get all notes (one-time fetch).
     */
    suspend fun getNotes(): List<Note> =
        withContext(Dispatchers.IO) {
            noteDao.getAllNotes(instanceId).map { it.toNote() }
        }

    /**
     * Get a specific note by ID.
     */
    suspend fun getNoteById(noteId: String): Note? =
        withContext(Dispatchers.IO) {
            noteDao.getNoteById(noteId)?.toNote()
        }

    /**
     * Create a new note (works offline).
     */
    suspend fun createNote(
        title: String,
        content: String = "",
        category: String = "Uncategorized",
    ): Result<Note> =
        withContext(Dispatchers.IO) {
            try {
                val now = java.time.Instant.now().toString() // ISO 8601 format
                val noteId = UUID.randomUUID().toString()

                val entity =
                    NoteEntity(
                        id = noteId,
                        title = title,
                        category = category,
                        content = content,
                        createdAt = now,
                        updatedAt = now,
                        encrypted = null,
                        isDirty = true,
                        isDeleted = false,
                        instanceId = instanceId,
                        isLocalOnly = true,
                    )

                // Save locally
                noteDao.insertNote(entity)
                AppLog.d("OfflineNotesRepository", "Note created locally: $noteId")

                // Try to sync if online
                if (isOnline.value) {
                    syncNote(entity)
                }

                Result.success(entity.toNote())
            } catch (e: Exception) {
                AppLog.d("OfflineNotesRepository", "Failed to create note: ${e.message}")
                Result.failure(e)
            }
        }

    /**
     * Update an existing note (works offline).
     */
    suspend fun updateNote(
        noteId: String,
        title: String,
        content: String,
        category: String,
    ): Result<Note> =
        withContext(Dispatchers.IO) {
            try {
                val existing = noteDao.getNoteById(noteId)
                if (existing == null) {
                    return@withContext Result.failure(Exception("Note not found"))
                }

                val updated =
                    existing.copy(
                        title = title,
                        content = content,
                        category = category,
                        updatedAt = java.time.Instant.now().toString(),
                        isDirty = true,
                    )

                noteDao.updateNote(updated)
                AppLog.d("OfflineNotesRepository", "Note updated locally: $noteId")

                // Try to sync if online
                if (isOnline.value) {
                    syncNote(updated)
                }

                Result.success(updated.toNote())
            } catch (e: Exception) {
                AppLog.d("OfflineNotesRepository", "Failed to update note: ${e.message}")
                Result.failure(e)
            }
        }

    /**
     * Delete a note (works offline).
     * If the note only exists locally (never synced), it is hard-deleted immediately —
     * no server call is made since the server has never seen this ID.
     */
    suspend fun deleteNote(noteId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val note = noteDao.getNoteById(noteId)
                if (note != null && note.isLocalOnly) {
                    noteDao.deleteNote(noteId)
                    AppLog.d("OfflineNotesRepository", "Local-only note hard-deleted: $noteId")
                } else {
                    noteDao.markAsDeleted(noteId)
                    AppLog.d("OfflineNotesRepository", "Note marked for deletion: $noteId")
                    if (isOnline.value) {
                        syncDeletedNote(noteId)
                    }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                AppLog.d("OfflineNotesRepository", "Failed to delete note: ${e.message}")
                Result.failure(e)
            }
        }

    /**
     * Sync all notes with the server.
     * Fetches remote notes and pushes local changes.
     * Detects conflicts and creates local copies to avoid data loss.
     */
    suspend fun syncNotes(): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (isSyncing.value) {
                AppLog.d("OfflineNotesRepository", "Sync already in progress")
                return@withContext Result.success(Unit)
            }

            if (!isOnline.value) {
                AppLog.d("OfflineNotesRepository", "Cannot sync - offline")
                return@withContext Result.failure(Exception("Offline"))
            }

            runtime.syncStatus.setSyncing(true)
            runtime.syncStatus.markSyncStarted()
            AppLog.d("OfflineNotesRepository", "Starting sync...")

            try {
                // Get local notes before pushing changes (for conflict detection)
                val localNotesBeforeSync = noteDao.getAllNotes(instanceId)
                val localNotesMap = localNotesBeforeSync.associateBy { it.id }

                // 1. Push local changes first
                val dirtyNotes = noteDao.getDirtyNotes(instanceId)
                AppLog.d("OfflineNotesRepository", "Found ${dirtyNotes.size} dirty notes")

                for (note in dirtyNotes) {
                    if (note.isDeleted) {
                        syncDeletedNote(note.id)
                    } else {
                        syncNote(note)
                    }
                }

                val stillDirty = noteDao.getDirtyNotes(instanceId)
                if (stillDirty.isNotEmpty()) {
                    val msg = appContext.getString(R.string.sync_push_failed_kept_local, stillDirty.size)
                    AppLog.d("OfflineNotesRepository", msg)
                    runtime.syncStatus.markSyncCompleted(success = false, errorMessage = msg)
                    runtime.syncStatus.setSyncing(false)
                    return@withContext Result.failure(Exception(msg))
                }

                // 2. Fetch all notes from server
                val response = api.getNotes()
                if (response.notes != null) {
                    val serverNotes = response.notes.map { it.toEntity(instanceId, isDirty = false) }
                    val conflictCopies = mutableListOf<NoteEntity>()

                    // 3. Detect conflicts: notes that were modified both locally and on server
                    for (serverNote in serverNotes) {
                        val localNote = localNotesMap[serverNote.id]
                        if (localNote != null && localNote.isDirty && !localNote.isDeleted) {
                            // Check if server note has different content than what we just pushed
                            if (hasConflict(localNote, serverNote)) {
                                // Create a copy of the local version to preserve data
                                val localCopy =
                                    localNote.copy(
                                        id = UUID.randomUUID().toString(),
                                        title = "${localNote.title}$LOCAL_COPY_SUFFIX",
                                        isDirty = false,
                                        isDeleted = false,
                                    )
                                conflictCopies.add(localCopy)
                                AppLog.d("OfflineNotesRepository", "Conflict detected for '${localNote.title}', creating local copy")
                            }
                        }
                    }

                    // 4. Update database: replace with server notes + add conflict copies (atomic)
                    database.withTransaction {
                        noteDao.deleteAllNotes(instanceId)
                        noteDao.insertNotes(serverNotes)
                        if (conflictCopies.isNotEmpty()) {
                            noteDao.insertNotes(conflictCopies)
                        }
                    }
                    if (conflictCopies.isNotEmpty()) {
                        runtime.syncStatus.setConflictsDetected(conflictCopies.size)
                        AppLog.d("OfflineNotesRepository", "Created ${conflictCopies.size} local copies due to conflicts")
                    } else {
                        runtime.syncStatus.setConflictsDetected(0)
                    }

                    AppLog.d("OfflineNotesRepository", "Synced ${serverNotes.size} notes from server")
                }

                runtime.syncStatus.markSyncCompleted(success = true)
                runtime.syncStatus.setSyncing(false)
                AppLog.d("OfflineNotesRepository", "Sync complete")
                Result.success(Unit)
            } catch (e: Exception) {
                val msg = ApiErrorHelper.userMessage(appContext, e)
                runtime.syncStatus.markSyncCompleted(success = false, errorMessage = msg)
                runtime.syncStatus.setSyncing(false)
                AppLog.d("OfflineNotesRepository", "Sync failed: $msg")
                Result.failure(Exception(msg, e))
            }
        }

    /**
     * Check if there's a conflict between local and server versions.
     * A conflict exists if the content or title differs significantly.
     */
    private fun hasConflict(
        localNote: NoteEntity,
        serverNote: NoteEntity,
    ): Boolean {
        // If content or title is different, there might be a conflict
        return localNote.title != serverNote.title ||
            localNote.content != serverNote.content ||
            localNote.category != serverNote.category
    }

    /**
     * Sync a single note to the server.
     * Uses [NoteEntity.isLocalOnly] to decide between create and update — a note is local-only
     * when it was created offline and has never been pushed to the server, regardless of timestamps.
     */
    private suspend fun syncNote(note: NoteEntity) {
        try {
            if (note.isLocalOnly) {
                val request =
                    CreateNoteRequest(
                        title = note.title,
                        content = note.content,
                        category = note.category,
                    )
                val response = api.createNote(request)
                if (response.data != null) {
                    // Swap the local temporary ID for the server-assigned ID.
                    noteDao.deleteNote(note.id)
                    noteDao.insertNote(response.data.toEntity(instanceId, isDirty = false))
                    AppLog.d("OfflineNotesRepository", "Note created on server: ${response.data.id}")
                }
            } else {
                val request =
                    UpdateNoteRequest(
                        title = note.title,
                        content = note.content,
                        category = note.category,
                    )
                val response = api.updateNote(note.id, request)
                if (response.data != null) {
                    noteDao.insertNote(response.data.toEntity(instanceId, isDirty = false))
                    AppLog.d("OfflineNotesRepository", "Note updated on server: ${note.id}")
                }
            }
        } catch (e: Exception) {
            AppLog.d("OfflineNotesRepository", "Failed to sync note ${note.id}: ${e.message}")
            // Keep note marked as dirty for next sync attempt.
        }
    }

    /**
     * Sync a deleted note to the server.
     */
    private suspend fun syncDeletedNote(noteId: String) {
        try {
            api.deleteNote(noteId)
            // Permanently delete from local database after successful server delete
            noteDao.deleteNote(noteId)
            AppLog.d("OfflineNotesRepository", "Note deleted on server: $noteId")
        } catch (e: Exception) {
            AppLog.d("OfflineNotesRepository", "Failed to delete note on server $noteId: ${e.message}")
            // Keep note marked as deleted for next sync attempt
        }
    }

    /**
     * Search notes locally.
     */
    suspend fun searchNotes(query: String): List<Note> =
        withContext(Dispatchers.IO) {
            noteDao.searchNotes(instanceId, query).map { it.toNote() }
        }

    /**
     * Clear the conflict notification count.
     */
    fun clearConflictNotification() {
        runtime.syncStatus.setConflictsDetected(0)
    }

    /**
     * Filter notes by category.
     */
    suspend fun getNotesByCategory(category: String): List<Note> =
        withContext(Dispatchers.IO) {
            noteDao.getNotesByCategory(instanceId, category).map { it.toNote() }
        }

    /**
     * Clear all local notes (e.g., when disconnecting from instance).
     */
    suspend fun clearAllNotes() =
        withContext(Dispatchers.IO) {
            noteDao.deleteAllNotes(instanceId)
            AppLog.d("OfflineNotesRepository", "All notes cleared for instance: $instanceId")
        }

    companion object {
        private const val TAG = "OfflineNotesRepository"
        const val LOCAL_COPY_SUFFIX = " (Local copy)"

        suspend fun clearLocalNotes(
            context: Context,
            instanceId: String,
            database: JottyDatabase = JottyDatabase.getDatabase(context.applicationContext),
        ) = withContext(Dispatchers.IO) {
            database.noteDao().deleteAllNotes(instanceId)
            AppLog.d("OfflineNotesRepository", "All notes cleared for removed instance: $instanceId")
        }
    }
}
