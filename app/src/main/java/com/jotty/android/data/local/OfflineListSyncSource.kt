package com.jotty.android.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * What an offline-enabled list screen needs from its repository to show and drive sync
 * ([com.jotty.android.ui.common.rememberOfflineListSync]). Implemented by
 * [OfflineNotesRepository] and [OfflineChecklistsRepository].
 */
interface OfflineListSyncSource {
    val isSyncing: StateFlow<Boolean>

    /** Conflict copies made by the last sync; reset with [clearConflictNotification]. */
    val conflictsDetected: StateFlow<Int>
    val lastSyncAttemptEpochMs: StateFlow<Long?>
    val lastSyncDurationText: StateFlow<String?>
    val lastSyncError: StateFlow<String?>

    /** Ids with unsent changes (dirty or pending delete). */
    fun pendingSyncIdsFlow(): Flow<Set<String>>

    /** Number of unresolved conflict copies. */
    fun conflictCopyCountFlow(): Flow<Int>

    /** A sync the user asked for (pull to refresh, refresh action, retry). */
    suspend fun syncNow(): Result<Unit>

    fun clearConflictNotification()
}
