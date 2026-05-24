package com.jotty.android.data.local

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.Gson
import com.jotty.android.R
import com.jotty.android.data.api.AddItemRequest
import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.CreateChecklistRequest
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.UpdateChecklistRequest
import com.jotty.android.data.api.UpdateItemRequest
import com.jotty.android.util.ApiErrorHelper
import com.jotty.android.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private val gson = Gson()

/**
 * Manages offline checklist storage and synchronisation with the Jotty API.
 *
 * Item mutations while offline are:
 *  1. Applied immediately to the local [itemsJson] blob so the UI reflects the change.
 *  2. Recorded as [PendingItemOp] entries replayed on the next successful sync.
 *
 * Paths in pending ops are positional (e.g. "0", "0.1") captured at mutation time.
 * If the server was also modified during the offline period a path may be stale; the
 * replay silently skips such failing ops — acceptable for a single-user homelab app.
 */
class OfflineChecklistsRepository(
    context: Context,
    private val database: JottyDatabase,
    private val instanceId: String,
    private val api: JottyApi,
    initialOnlineOverride: Boolean? = null,
    private val useSharedConnectivity: Boolean = true,
) {
    private val appContext = context.applicationContext
    private val checklistDao = database.checklistDao()
    private val itemMutationDepth = AtomicInteger(0)
    private val pendingSyncAfterMutations = AtomicBoolean(false)
    @Volatile private var lastSyncCompletedAtMs: Long? = null
    private val runtime =
        OfflineRepositoryLifecycle(
            context = context,
            initialOnlineOverride = initialOnlineOverride,
            useSharedConnectivity = useSharedConnectivity,
            logTag = TAG,
            instanceId = instanceId,
            onNetworkAvailable = { syncChecklists() },
        )

    val isOnline: StateFlow<Boolean> = runtime.syncStatus.isOnline
    val isSyncing: StateFlow<Boolean> = runtime.syncStatus.isSyncing
    val conflictsDetected: StateFlow<Int> = runtime.syncStatus.conflictsDetected
    val lastSyncAttemptEpochMs: StateFlow<Long?> = runtime.syncStatus.lastSyncAttemptEpochMs
    val lastSyncSuccessEpochMs: StateFlow<Long?> = runtime.syncStatus.lastSyncSuccessEpochMs
    val lastSyncDurationText: StateFlow<String?> = runtime.syncStatus.lastSyncDurationText
    val lastSyncError: StateFlow<String?> = runtime.syncStatus.lastSyncError
    private val _replayFailuresDetected = MutableStateFlow(0)
    val replayFailuresDetected: StateFlow<Int> = _replayFailuresDetected.asStateFlow()

    fun close() {
        runtime.close()
    }

    // ─── Flows ──────────────────────────────────────────────────────────────

    fun getChecklistsFlow(): Flow<List<Checklist>> =
        checklistDao.getAllChecklistsFlow(instanceId)
            .map { it.map { e -> e.toChecklist() } }
            .flowOn(Dispatchers.IO)

    fun clearConflictNotification() {
        runtime.syncStatus.setConflictsDetected(0)
    }

    fun clearReplayFailureNotification() {
        _replayFailuresDetected.value = 0
    }

    // ─── CRUD ───────────────────────────────────────────────────────────────

    suspend fun createChecklist(
        title: String,
        type: String = "simple",
        category: String = "Uncategorized",
    ): Result<Checklist> =
        withContext(Dispatchers.IO) {
            runCatching {
                val now = Instant.now().toString()
                val entity =
                    ChecklistEntity(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        category = category,
                        type = type,
                        itemsJson = "[]",
                        pendingOpsJson = "[]",
                        createdAt = now,
                        updatedAt = now,
                        isDirty = true,
                        isDeleted = false,
                        instanceId = instanceId,
                        isLocalOnly = true,
                    )
                checklistDao.insert(entity)
                AppLog.d(TAG, "Checklist created locally: ${entity.id}")
                // Do not inline-sync here: syncChecklist swaps the local PK for the server ID,
                // which would invalidate the returned checklist. The network callback triggers
                // syncChecklists() as soon as the device is online.
                entity.toChecklist()
            }
        }

    suspend fun deleteChecklist(id: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val entity = checklistDao.getById(id)
                if (entity != null && entity.isLocalOnly) {
                    checklistDao.delete(id)
                    AppLog.d(TAG, "Local-only checklist hard-deleted: $id")
                } else {
                    checklistDao.markAsDeleted(id)
                    AppLog.d(TAG, "Checklist marked for deletion: $id")
                    if (isOnline.value) syncDeletedChecklist(id)
                }
            }
        }

    suspend fun updateChecklist(
        id: String,
        title: String,
    ): Result<Checklist> =
        withContext(Dispatchers.IO) {
            runCatching {
                val existing =
                    checklistDao.getById(id)
                        ?: throw Exception("Checklist not found")
                val updated =
                    existing.copy(
                        title = title,
                        updatedAt = Instant.now().toString(),
                        isDirty = true,
                    )
                checklistDao.update(updated)
                AppLog.d(TAG, "Checklist title updated locally: $id")
                if (isOnline.value) {
                    syncChecklist(updated)
                }
                checklistDao.getById(id)?.toChecklist()
                    ?: updated.toChecklist()
            }
        }

    // ─── Item operations ────────────────────────────────────────────────────

    suspend fun addItem(
        checklistId: String,
        text: String,
        parentIndex: String? = null,
    ): Result<Checklist> =
        withContext(Dispatchers.IO) {
            runCatching {
                withItemMutation {
                if (isOnline.value) {
                    val response =
                        api.addChecklistItem(
                            checklistId,
                            AddItemRequest(text = text, parentIndex = parentIndex),
                        )
                    // Refresh local cache from server
                    val fresh = api.getChecklists().checklists.find { it.id == checklistId }
                    if (fresh != null) checklistDao.insert(fresh.toEntity(instanceId))
                    response.let {
                        checklistDao.getById(checklistId)?.toChecklist()
                            ?: throw Exception("Checklist not found")
                    }
                } else {
                    val op = PendingItemOp(type = "ADD", text = text, parentIndex = parentIndex)
                    applyOpLocally(checklistId, op)
                }
                }
            }
        }

    suspend fun checkItem(
        checklistId: String,
        path: String,
    ): Result<Checklist> =
        withContext(Dispatchers.IO) {
            runCatching {
                withItemMutation {
                if (isOnline.value) {
                    api.checkItem(checklistId, path)
                    refreshFromServer(checklistId)
                } else {
                    applyOpLocally(checklistId, PendingItemOp(type = "CHECK", path = path))
                }
                }
            }
        }

    suspend fun uncheckItem(
        checklistId: String,
        path: String,
    ): Result<Checklist> =
        withContext(Dispatchers.IO) {
            runCatching {
                withItemMutation {
                if (isOnline.value) {
                    api.uncheckItem(checklistId, path)
                    refreshFromServer(checklistId)
                } else {
                    applyOpLocally(checklistId, PendingItemOp(type = "UNCHECK", path = path))
                }
                }
            }
        }

    suspend fun deleteItem(
        checklistId: String,
        path: String,
    ): Result<Checklist> =
        withContext(Dispatchers.IO) {
            runCatching {
                withItemMutation {
                if (isOnline.value) {
                    api.deleteItem(checklistId, path)
                    refreshFromServer(checklistId)
                } else {
                    applyOpLocally(checklistId, PendingItemOp(type = "DELETE", path = path))
                }
                }
            }
        }

    suspend fun updateItem(
        checklistId: String,
        path: String,
        text: String,
    ): Result<Checklist> =
        withContext(Dispatchers.IO) {
            runCatching {
                withItemMutation {
                if (isOnline.value) {
                    api.updateItem(checklistId, path, UpdateItemRequest(text = text))
                    refreshFromServer(checklistId)
                } else {
                    applyOpLocally(
                        checklistId,
                        PendingItemOp(type = "UPDATE_TEXT", path = path, text = text),
                    )
                }
                }
            }
        }

    // ─── Sync ────────────────────────────────────────────────────────────────

    suspend fun syncChecklists(force: Boolean = false): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (isSyncing.value) return@withContext Result.success(Unit)
            if (!isOnline.value) return@withContext Result.failure(Exception("Offline"))

            if (itemMutationDepth.get() > 0) {
                pendingSyncAfterMutations.set(true)
                AppLog.d(TAG, "Deferring sync — item mutation in progress")
                return@withContext Result.success(Unit)
            }

            if (!force) {
                val last = lastSyncCompletedAtMs
                if (last != null && System.currentTimeMillis() - last < SYNC_DEBOUNCE_MS) {
                    AppLog.d(TAG, "Skipping sync — debounced")
                    return@withContext Result.success(Unit)
                }
            }

            runtime.syncStatus.setSyncing(true)
            runtime.syncStatus.markSyncStarted()
            _replayFailuresDetected.value = 0
            AppLog.d(TAG, "Starting sync…")
            try {
                val localBefore = checklistDao.getAllChecklists(instanceId).associateBy { it.id }

                val dirty = checklistDao.getDirty(instanceId)
                AppLog.d(TAG, "${dirty.size} dirty checklists")
                for (entity in dirty) {
                    if (entity.isDeleted) {
                        syncDeletedChecklist(entity.id)
                    } else {
                        syncChecklist(entity)
                    }
                }

                val stillDirty = checklistDao.getDirty(instanceId)
                if (stillDirty.isNotEmpty()) {
                    val msg =
                        appContext.getString(
                            R.string.sync_push_failed_kept_local_checklists,
                            stillDirty.size,
                        )
                    AppLog.d(TAG, msg)
                    runtime.syncStatus.markSyncCompleted(success = false, errorMessage = msg)
                    runtime.syncStatus.setSyncing(false)
                    return@withContext Result.failure(Exception(msg))
                }

                val serverResponse = api.getChecklists()
                val serverChecklists = serverResponse.checklists
                val conflictCopies = mutableListOf<ChecklistEntity>()

                for (serverList in serverChecklists) {
                    val local = localBefore[serverList.id]
                    if (local != null && local.isDirty && !local.isDeleted) {
                        if (hasConflict(local, serverList)) {
                            conflictCopies.add(
                                local.copy(
                                    id = UUID.randomUUID().toString(),
                                    title = "${local.title}$LOCAL_COPY_SUFFIX",
                                    isDirty = false,
                                    isDeleted = false,
                                    pendingOpsJson = "[]",
                                ),
                            )
                            AppLog.d(TAG, "Conflict detected for '${local.title}', local copy created")
                        }
                    }
                }

                // Atomic replace: observers see either the old list or the new list, never empty.
                database.withTransaction {
                    checklistDao.deleteAll(instanceId)
                    checklistDao.insertAll(serverChecklists.map { it.toEntity(instanceId) })
                    if (conflictCopies.isNotEmpty()) checklistDao.insertAll(conflictCopies)
                }
                runtime.syncStatus.setConflictsDetected(if (conflictCopies.isNotEmpty()) conflictCopies.size else 0)
                runtime.syncStatus.markSyncCompleted(success = true)
                lastSyncCompletedAtMs = System.currentTimeMillis()

                AppLog.d(TAG, "Sync complete — ${serverChecklists.size} checklists from server")
                Result.success(Unit)
            } catch (e: Exception) {
                val msg = ApiErrorHelper.userMessage(appContext, e)
                runtime.syncStatus.markSyncCompleted(success = false, errorMessage = msg)
                AppLog.d(TAG, "Sync failed: $msg")
                Result.failure(Exception(msg, e))
            } finally {
                runtime.syncStatus.setSyncing(false)
            }
        }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private fun hasConflict(
        local: ChecklistEntity,
        server: Checklist,
    ): Boolean {
        if (local.pendingOps().isNotEmpty()) return true
        if (local.title != server.title || local.category != server.category) return true
        return gson.toJson(local.items()) != gson.toJson(server.items)
    }

    private suspend fun syncChecklist(entity: ChecklistEntity) {
        if (entity.isLocalOnly) {
            val response =
                api.createChecklist(
                    CreateChecklistRequest(
                        title = entity.title,
                        category = entity.category,
                        type = entity.type,
                    ),
                )
            val created = response.data ?: throw Exception("Create checklist failed")
            for (item in entity.items()) {
                runCatching { api.addChecklistItem(created.id, AddItemRequest(text = item.text)) }
            }
            checklistDao.delete(entity.id)
            val fresh = api.getChecklists().checklists.find { it.id == created.id }
                ?: throw Exception("Checklist not found after create")
            checklistDao.insert(fresh.toEntity(instanceId))
            AppLog.d(TAG, "Local-only checklist pushed: ${created.id}")
        } else {
            api.updateChecklist(
                entity.id,
                UpdateChecklistRequest(title = entity.title, category = entity.category),
            )
            val failedOps = replayPendingOps(entity)
            if (failedOps > 0) {
                throw Exception("Replay failed: $failedOps operation(s)")
            }
            val fresh =
                api.getChecklists().checklists.find { it.id == entity.id }
                    ?: throw Exception("Checklist not found after sync")
            checklistDao.insert(fresh.toEntity(instanceId))
            AppLog.d(TAG, "Checklist synced: ${entity.id}")
        }
    }

    /** @return number of ops that failed to replay */
    private suspend fun replayPendingOps(entity: ChecklistEntity): Int {
        var failedOps = 0
        for (op in entity.pendingOps().distinct()) {
            runCatching {
                when (op.type) {
                    "CHECK" -> api.checkItem(entity.id, op.path!!)
                    "UNCHECK" -> api.uncheckItem(entity.id, op.path!!)
                    "ADD" ->
                        api.addChecklistItem(
                            entity.id,
                            AddItemRequest(text = op.text ?: "", parentIndex = op.parentIndex),
                        )
                    "DELETE" -> api.deleteItem(entity.id, op.path!!)
                    "UPDATE_TEXT" ->
                        api.updateItem(
                            entity.id,
                            op.path!!,
                            UpdateItemRequest(text = op.text ?: ""),
                        )
                }
            }.onFailure {
                failedOps += 1
                AppLog.d(TAG, "Pending op ${op.type} failed (stale path?): ${it.message}")
            }
        }

        if (failedOps > 0) {
            _replayFailuresDetected.value += failedOps
            AppLog.d(TAG, "Replay completed with $failedOps failed operation(s)")
        }
        return failedOps
    }

    private suspend fun syncDeletedChecklist(id: String) {
        api.deleteChecklist(id)
        checklistDao.delete(id)
        AppLog.d(TAG, "Checklist deleted on server: $id")
    }

    private suspend fun <T> withItemMutation(block: suspend () -> T): T {
        itemMutationDepth.incrementAndGet()
        try {
            return block()
        } finally {
            if (itemMutationDepth.decrementAndGet() == 0 && pendingSyncAfterMutations.getAndSet(false)) {
                runtime.coroutineScope.launch { syncChecklists() }
            }
        }
    }

    private suspend fun applyOpLocally(
        checklistId: String,
        op: PendingItemOp,
    ): Checklist {
        val entity =
            checklistDao.getById(checklistId)
                ?: throw Exception("Checklist not found: $checklistId")
        val newItems = applyOpToItems(entity.items(), op)
        val newOps = (entity.pendingOps() + op).distinct()
        val updated =
            entity.copy(
                itemsJson = gson.toJson(newItems),
                pendingOpsJson = gson.toJson(newOps),
                isDirty = true,
                updatedAt = Instant.now().toString(),
            )
        checklistDao.update(updated)
        return updated.toChecklist()
    }

    private suspend fun refreshFromServer(checklistId: String): Checklist {
        val fresh =
            api.getChecklists().checklists.find { it.id == checklistId }
                ?: throw Exception("Checklist not found on server: $checklistId")
        checklistDao.insert(fresh.toEntity(instanceId))
        return fresh
    }

    companion object {
        private const val TAG = "OfflineChecklistsRepo"
        const val LOCAL_COPY_SUFFIX = " (Local copy)"
        private const val SYNC_DEBOUNCE_MS = 3_000L
    }
}
