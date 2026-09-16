package com.jotty.android.data.local

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide sync locks keyed by instance id.
 *
 * [OfflineNotesRepository] / [OfflineChecklistsRepository] are constructed per UI session
 * and again inside [OfflineSyncWorker]. A per-instance mutex would not serialize those,
 * so concurrent reconnect syncs could push the same local-only create more than once.
 */
object OfflineSyncLocks {
    private val notesByInstance = ConcurrentHashMap<String, Mutex>()
    private val checklistsByInstance = ConcurrentHashMap<String, Mutex>()

    fun forNotes(instanceId: String): Mutex = notesByInstance.getOrPut(instanceId) { Mutex() }

    fun forChecklists(instanceId: String): Mutex = checklistsByInstance.getOrPut(instanceId) { Mutex() }
}
