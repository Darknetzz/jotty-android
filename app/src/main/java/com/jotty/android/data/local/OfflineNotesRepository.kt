package com.jotty.android.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import com.jotty.android.data.api.CreateNoteRequest
import com.jotty.android.data.api.UpdateNoteRequest
import com.jotty.android.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Repository that manages offline note storage and synchronization.
 * Coordinates between local Room database and remote Jotty API.
 */
class OfflineNotesRepository(
    private val context: Context,
    private val database: JottyDatabase,
    private val instanceId: String,
    private val api: JottyApi
) {
    private val noteDao = database.noteDao()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // Track connectivity state
    private val _isOnline = MutableStateFlow(checkConnectivity())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    // Track sync state
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
    
    // Track conflicts detected during last sync
    private val _conflictsDetected = MutableStateFlow(0)
    val conflictsDetected: StateFlow<Int> = _conflictsDetected.asStateFlow()

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    init {
        // Register connectivity callback
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                AppLog.d("OfflineNotesRepository", "Network available")
                _isOnline.value = true
                // Auto-sync when coming online
                coroutineScope.launch {
                    syncNotes()
                }
            }

            override fun onLost(network: Network) {
                AppLog.d("OfflineNotesRepository", "Network lost")
                _isOnline.value = false
            }
        })
    }

    /**
     * Check current connectivity status.
     */
    private fun checkConnectivity(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Get all notes as Flow (observes changes).
     */
    fun getNotesFlow(): Flow<List<Note>> {
        return noteDao.getAllNotesFlow(instanceId).map { entities ->
            entities.map { it.toNote() }
        }
    }

    /**
     * Get all notes (one-time fetch).
     */
    suspend fun getNotes(): List<Note> = withContext(Dispatchers.IO) {
        noteDao.getAllNotes(instanceId).map { it.toNote() }
    }

    /**
     * Get a specific note by ID.
     */
    suspend fun getNoteById(noteId: String): Note? = withContext(Dispatchers.IO) {
        noteDao.getNoteById(noteId)?.toNote()
    }

    /**
     * Create a new note (works offline).
     */
    suspend fun createNote(title: String, content: String = "", category: String = "Uncategorized"): Result<Note> = withContext(Dispatchers.IO) {
        try {
            val now = java.time.Instant.now().toString() // ISO 8601 format
            val noteId = UUID.randomUUID().toString()
            
            val entity = NoteEntity(
                id = noteId,
                title = title,
                category = category,
                content = content,
                createdAt = now,
                updatedAt = now,
                encrypted = null,
                isDirty = true, // Mark as dirty for sync
                isDeleted = false,
                instanceId = instanceId
            )

            // Save locally
            noteDao.insertNote(entity)
            AppLog.d("OfflineNotesRepository", "Note created locally: $noteId")

            // Try to sync if online
            if (_isOnline.value) {
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
    suspend fun updateNote(noteId: String, title: String, content: String, category: String): Result<Note> = withContext(Dispatchers.IO) {
        try {
            val existing = noteDao.getNoteById(noteId)
            if (existing == null) {
                return@withContext Result.failure(Exception("Note not found"))
            }

            val updated = existing.copy(
                title = title,
                content = content,
                category = category,
                updatedAt = java.time.Instant.now().toString(), // ISO 8601 format
                isDirty = true // Mark as dirty for sync
            )

            noteDao.updateNote(updated)
            AppLog.d("OfflineNotesRepository", "Note updated locally: $noteId")

            // Try to sync if online
            if (_isOnline.value) {
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
     */
    suspend fun deleteNote(noteId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Mark as deleted (soft delete for sync)
            noteDao.markAsDeleted(noteId)
            AppLog.d("OfflineNotesRepository", "Note marked for deletion: $noteId")

            // Try to sync if online
            if (_isOnline.value) {
                syncDeletedNote(noteId)
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
    suspend fun syncNotes(): Result<Unit> = withContext(Dispatchers.IO) {
        if (_isSyncing.value) {
            AppLog.d("OfflineNotesRepository", "Sync already in progress")
            return@withContext Result.success(Unit)
        }

        if (!_isOnline.value) {
            AppLog.d("OfflineNotesRepository", "Cannot sync - offline")
            return@withContext Result.failure(Exception("Offline"))
        }

        _isSyncing.value = true
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
                            val localCopy = localNote.copy(
                                id = UUID.randomUUID().toString(), // New ID for the copy
                                title = "${localNote.title} (Local copy)",
                                isDirty = false, // Don't try to sync the copy
                                isDeleted = false
                            )
                            conflictCopies.add(localCopy)
                            AppLog.d("OfflineNotesRepository", "Conflict detected for '${localNote.title}', creating local copy")
                        }
                    }
                }
                
                // 4. Update database: replace with server notes + add conflict copies
                noteDao.deleteAllNotes(instanceId)
                noteDao.insertNotes(serverNotes)
                if (conflictCopies.isNotEmpty()) {
                    noteDao.insertNotes(conflictCopies)
                    _conflictsDetected.value = conflictCopies.size
                    AppLog.d("OfflineNotesRepository", "Created ${conflictCopies.size} local copies due to conflicts")
                } else {
                    _conflictsDetected.value = 0
                }
                
                AppLog.d("OfflineNotesRepository", "Synced ${serverNotes.size} notes from server")
            }

            _isSyncing.value = false
            AppLog.d("OfflineNotesRepository", "Sync complete")
            Result.success(Unit)
        } catch (e: Exception) {
            _isSyncing.value = false
            AppLog.d("OfflineNotesRepository", "Sync failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Check if there's a conflict between local and server versions.
     * A conflict exists if the content or title differs significantly.
     */
    private fun hasConflict(localNote: NoteEntity, serverNote: NoteEntity): Boolean {
        // If content or title is different, there might be a conflict
        return localNote.title != serverNote.title || 
               localNote.content != serverNote.content ||
               localNote.category != serverNote.category
    }

    /**
     * Sync a single note to the server.
     */
    private suspend fun syncNote(note: NoteEntity) {
        try {
            // Check if this is a new note (starts with random UUID) or an existing one
            val isNew = note.createdAt == note.updatedAt && note.isDirty

            if (isNew) {
                // Create new note on server
                val request = CreateNoteRequest(
                    title = note.title,
                    content = note.content,
                    category = note.category
                )
                val response = api.createNote(request)
                
                if (response.data != null) {
                    // Replace local temp ID with server ID
                    noteDao.deleteNote(note.id)
                    noteDao.insertNote(response.data.toEntity(instanceId, isDirty = false))
                    AppLog.d("OfflineNotesRepository", "Note created on server: ${response.data.id}")
                }
            } else {
                // Update existing note on server
                val request = UpdateNoteRequest(
                    title = note.title,
                    content = note.content,
                    category = note.category
                )
                val response = api.updateNote(note.id, request)
                
                if (response.data != null) {
                    // Update local note with server response
                    noteDao.insertNote(response.data.toEntity(instanceId, isDirty = false))
                    AppLog.d("OfflineNotesRepository", "Note updated on server: ${note.id}")
                }
            }
        } catch (e: Exception) {
            AppLog.d("OfflineNotesRepository", "Failed to sync note ${note.id}: ${e.message}")
            // Keep note marked as dirty for next sync attempt
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
    suspend fun searchNotes(query: String): List<Note> = withContext(Dispatchers.IO) {
        noteDao.searchNotes(instanceId, query).map { it.toNote() }
    }

    /**
     * Clear the conflict notification count.
     */
    fun clearConflictNotification() {
        _conflictsDetected.value = 0
    }

    /**
     * Filter notes by category.
     */
    suspend fun getNotesByCategory(category: String): List<Note> = withContext(Dispatchers.IO) {
        noteDao.getNotesByCategory(instanceId, category).map { it.toNote() }
    }

    /**
     * Clear all local notes (e.g., when disconnecting from instance).
     */
    suspend fun clearAllNotes() = withContext(Dispatchers.IO) {
        noteDao.deleteAllNotes(instanceId)
        AppLog.d("OfflineNotesRepository", "All notes cleared for instance: $instanceId")
    }
}
