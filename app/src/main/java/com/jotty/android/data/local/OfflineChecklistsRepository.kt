package com.jotty.android.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.room.withTransaction
import com.google.gson.Gson
import com.jotty.android.data.api.AddItemRequest
import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.CreateChecklistRequest
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.UpdateChecklistRequest
import com.jotty.android.data.api.UpdateItemRequest
import com.jotty.android.util.AppLog
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private val context: Context,
    private val database: JottyDatabase,
    private val instanceId: String,
    private val api: JottyApi,
    initialOnlineOverride: Boolean? = null,
    private val registerNetworkCallback: Boolean = true,
) {
    private val checklistDao = database.checklistDao()

    private val scopeExceptionHandler = CoroutineExceptionHandler { _, t ->
        AppLog.d(TAG, "Background coroutine failed: ${t.message}")
    }
    private val coroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + scopeExceptionHandler)

    private val _isOnline = MutableStateFlow(initialOnlineOverride ?: checkConnectivity())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _conflictsDetected = MutableStateFlow(0)
    val conflictsDetected: StateFlow<Int> = _conflictsDetected.asStateFlow()

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    @Volatile private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var closed = false

    init {
        if (registerNetworkCallback) {
            connectivityManager?.let { cm ->
                val req = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                val cb = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        if (closed) return
                        AppLog.d(TAG, "Network available")
                        _isOnline.value = true
                        coroutineScope.launch { syncChecklists() }
                    }

                    override fun onLost(network: Network) {
                        if (closed) return
                        AppLog.d(TAG, "Network lost")
                        _isOnline.value = false
                    }
                }
                networkCallback = cb
                cm.registerNetworkCallback(req, cb)
                AppLog.d(TAG, "Network callback registered (instance: $instanceId)")
            }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        networkCallback?.let {
            connectivityManager?.unregisterNetworkCallback(it)
            networkCallback = null
        }
        coroutineScope.cancel()
        AppLog.d(TAG, "Closed (instance: $instanceId)")
    }

    private fun checkConnectivity(): Boolean {
        val cm = connectivityManager ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ─── Flows ──────────────────────────────────────────────────────────────

    fun getChecklistsFlow(): Flow<List<Checklist>> =
        checklistDao.getAllChecklistsFlow(instanceId)
            .map { it.map { e -> e.toChecklist() } }
            .flowOn(Dispatchers.IO)

    fun clearConflictNotification() { _conflictsDetected.value = 0 }

    // ─── CRUD ───────────────────────────────────────────────────────────────

    suspend fun createChecklist(
        title: String,
        type: String = "simple",
        category: String = "Uncategorized",
    ): Result<Checklist> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Instant.now().toString()
            val entity = ChecklistEntity(
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

    suspend fun deleteChecklist(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val entity = checklistDao.getById(id)
            if (entity != null && entity.isLocalOnly) {
                checklistDao.delete(id)
                AppLog.d(TAG, "Local-only checklist hard-deleted: $id")
            } else {
                checklistDao.markAsDeleted(id)
                AppLog.d(TAG, "Checklist marked for deletion: $id")
                if (_isOnline.value) syncDeletedChecklist(id)
            }
        }
    }

    // ─── Item operations ────────────────────────────────────────────────────

    suspend fun addItem(
        checklistId: String,
        text: String,
        parentIndex: String? = null,
    ): Result<Checklist> = withContext(Dispatchers.IO) {
        runCatching {
            if (_isOnline.value) {
                val response = api.addChecklistItem(
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

    suspend fun checkItem(checklistId: String, path: String): Result<Checklist> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (_isOnline.value) {
                    api.checkItem(checklistId, path)
                    refreshFromServer(checklistId)
                } else {
                    applyOpLocally(checklistId, PendingItemOp(type = "CHECK", path = path))
                }
            }
        }

    suspend fun uncheckItem(checklistId: String, path: String): Result<Checklist> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (_isOnline.value) {
                    api.uncheckItem(checklistId, path)
                    refreshFromServer(checklistId)
                } else {
                    applyOpLocally(checklistId, PendingItemOp(type = "UNCHECK", path = path))
                }
            }
        }

    suspend fun deleteItem(checklistId: String, path: String): Result<Checklist> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (_isOnline.value) {
                    api.deleteItem(checklistId, path)
                    refreshFromServer(checklistId)
                } else {
                    applyOpLocally(checklistId, PendingItemOp(type = "DELETE", path = path))
                }
            }
        }

    suspend fun updateItem(
        checklistId: String,
        path: String,
        text: String,
    ): Result<Checklist> = withContext(Dispatchers.IO) {
        runCatching {
            if (_isOnline.value) {
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

    // ─── Sync ────────────────────────────────────────────────────────────────

    suspend fun syncChecklists(): Result<Unit> = withContext(Dispatchers.IO) {
        if (_isSyncing.value) return@withContext Result.success(Unit)
        if (!_isOnline.value) return@withContext Result.failure(Exception("Offline"))

        _isSyncing.value = true
        AppLog.d(TAG, "Starting sync…")
        try {
            val localBefore = checklistDao.getAllChecklists(instanceId).associateBy { it.id }

            val dirty = checklistDao.getDirty(instanceId)
            AppLog.d(TAG, "${dirty.size} dirty checklists")
            for (entity in dirty) {
                if (entity.isDeleted) syncDeletedChecklist(entity.id)
                else syncChecklist(entity)
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
            _conflictsDetected.value = if (conflictCopies.isNotEmpty()) conflictCopies.size else 0

            AppLog.d(TAG, "Sync complete — ${serverChecklists.size} checklists from server")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLog.d(TAG, "Sync failed: ${e.message}")
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private fun hasConflict(local: ChecklistEntity, server: Checklist): Boolean =
        local.title != server.title || local.category != server.category

    private suspend fun syncChecklist(entity: ChecklistEntity) {
        try {
            if (entity.isLocalOnly) {
                val response = api.createChecklist(
                    CreateChecklistRequest(
                        title = entity.title,
                        category = entity.category,
                        type = entity.type,
                    ),
                )
                if (response.data != null) {
                    // Add any locally-created items
                    for (item in entity.items()) {
                        runCatching { api.addChecklistItem(response.data.id, AddItemRequest(text = item.text)) }
                    }
                    // Replace local temp ID with server ID
                    checklistDao.delete(entity.id)
                    val fresh = api.getChecklists().checklists.find { it.id == response.data.id }
                    if (fresh != null) checklistDao.insert(fresh.toEntity(instanceId))
                    AppLog.d(TAG, "Local-only checklist pushed: ${response.data.id}")
                }
            } else {
                // Update metadata
                api.updateChecklist(
                    entity.id,
                    UpdateChecklistRequest(title = entity.title, category = entity.category),
                )
                // Replay pending item ops
                replayPendingOps(entity)
                // Pull fresh state
                val fresh = api.getChecklists().checklists.find { it.id == entity.id }
                if (fresh != null) checklistDao.insert(fresh.toEntity(instanceId))
                AppLog.d(TAG, "Checklist synced: ${entity.id}")
            }
        } catch (e: Exception) {
            AppLog.d(TAG, "Failed to sync checklist ${entity.id}: ${e.message}")
        }
    }

    private suspend fun replayPendingOps(entity: ChecklistEntity) {
        for (op in entity.pendingOps()) {
            runCatching {
                when (op.type) {
                    "CHECK" -> api.checkItem(entity.id, op.path!!)
                    "UNCHECK" -> api.uncheckItem(entity.id, op.path!!)
                    "ADD" -> api.addChecklistItem(
                        entity.id,
                        AddItemRequest(text = op.text ?: "", parentIndex = op.parentIndex),
                    )
                    "DELETE" -> api.deleteItem(entity.id, op.path!!)
                    "UPDATE_TEXT" -> api.updateItem(
                        entity.id, op.path!!, UpdateItemRequest(text = op.text ?: ""),
                    )
                }
            }.onFailure { AppLog.d(TAG, "Pending op ${op.type} failed (stale path?): ${it.message}") }
        }
    }

    private suspend fun syncDeletedChecklist(id: String) {
        try {
            api.deleteChecklist(id)
            checklistDao.delete(id)
            AppLog.d(TAG, "Checklist deleted on server: $id")
        } catch (e: Exception) {
            AppLog.d(TAG, "Failed to delete checklist on server $id: ${e.message}")
        }
    }

    private suspend fun applyOpLocally(checklistId: String, op: PendingItemOp): Checklist {
        val entity = checklistDao.getById(checklistId)
            ?: throw Exception("Checklist not found: $checklistId")
        val newItems = applyOpToItems(entity.items(), op)
        val newOps = entity.pendingOps() + op
        val updated = entity.copy(
            itemsJson = gson.toJson(newItems),
            pendingOpsJson = gson.toJson(newOps),
            isDirty = true,
            updatedAt = Instant.now().toString(),
        )
        checklistDao.update(updated)
        return updated.toChecklist()
    }

    private suspend fun refreshFromServer(checklistId: String): Checklist {
        val fresh = api.getChecklists().checklists.find { it.id == checklistId }
            ?: throw Exception("Checklist not found on server: $checklistId")
        checklistDao.insert(fresh.toEntity(instanceId))
        return fresh
    }

    companion object {
        private const val TAG = "OfflineChecklistsRepo"
        const val LOCAL_COPY_SUFFIX = " (Local copy)"
    }
}
