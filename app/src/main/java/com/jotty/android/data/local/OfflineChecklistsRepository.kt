package com.jotty.android.data.local

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.Gson
import com.jotty.android.R
import com.jotty.android.data.api.API_CATEGORY_UNCATEGORIZED
import com.jotty.android.data.api.AddItemRequest
import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.CreateChecklistRequest
import com.jotty.android.data.api.normalizedForLocal
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.ReorderItemsRequest
import com.jotty.android.data.api.UpdateChecklistRequest
import com.jotty.android.data.api.UpdateItemRequest
import com.jotty.android.util.ApiErrorHelper
import com.jotty.android.util.AppLog
import com.jotty.android.util.ServerCapabilities
import com.jotty.android.util.updateChecklistItemText
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * If the server was also modified during the offline period a path may be stale; replay
 * skips ops that are already satisfied on the server and drops idempotent DELETE 404s.
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
    private val syncMutex = Mutex()
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

    /**
     * Local conflict copies are kept until users review and delete or merge them.
     */
    fun getConflictCopiesFlow(): Flow<List<Checklist>> =
        checklistDao.getAllChecklistsFlow(instanceId)
            .map { entities ->
                entities
                    .filter { it.title.endsWith(LOCAL_COPY_SUFFIX) }
                    .map { it.toChecklist() }
            }
            .flowOn(Dispatchers.IO)

    fun getDirtyChecklistIdsFlow(): Flow<Set<String>> =
        checklistDao.getDirtyChecklistIdsFlow(instanceId)
            .map { it.toSet() }
            .flowOn(Dispatchers.IO)

    fun clearConflictNotification() {
        runtime.syncStatus.setConflictsDetected(0)
    }

    fun clearReplayFailureNotification() {
        _replayFailuresDetected.value = 0
    }

    suspend fun isLocalOnlyChecklist(checklistId: String): Boolean =
        withContext(Dispatchers.IO) {
            checklistDao.getById(checklistId)?.isLocalOnly == true
        }

    /**
     * Abandons unsynced local changes. Local-only checklists are deleted. Server-backed checklists
     * are replaced with the current server version (requires connectivity).
     */
    suspend fun discardPendingSync(checklistId: String): Result<Checklist?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val entity =
                    checklistDao.getById(checklistId)
                        ?: throw Exception("Checklist not found")
                if (!entity.isDirty && entity.pendingOps().isEmpty()) {
                    return@runCatching entity.toChecklist()
                }
                if (entity.isLocalOnly) {
                    checklistDao.delete(checklistId)
                    AppLog.d(TAG, "Local-only checklist discarded: $checklistId")
                    return@runCatching null
                }
                if (!isOnline.value) {
                    throw Exception(appContext.getString(R.string.discard_pending_sync_offline))
                }
                val fresh =
                    api.getChecklists().checklists.find { it.id == checklistId }
                        ?: throw Exception(appContext.getString(R.string.error_not_found))
                checklistDao.insert(fresh.toEntity(instanceId))
                AppLog.d(TAG, "Pending sync discarded — restored checklist from server: $checklistId")
                fresh
            }
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

    /**
     * Recreate a previously-deleted checklist (e.g. via undo), restoring its full item tree
     * and completion state locally. Items sync to the server on the next push.
     */
    suspend fun recreateChecklistWithItems(original: Checklist): Result<Checklist> =
        withContext(Dispatchers.IO) {
            runCatching {
                val now = Instant.now().toString()
                val entity =
                    ChecklistEntity(
                        id = UUID.randomUUID().toString(),
                        title = original.title,
                        category = original.category,
                        type = original.type,
                        itemsJson = gson.toJson(original.items),
                        pendingOpsJson = "[]",
                        createdAt = now,
                        updatedAt = now,
                        isDirty = true,
                        isDeleted = false,
                        instanceId = instanceId,
                        isLocalOnly = true,
                    )
                checklistDao.insert(entity)
                AppLog.d(TAG, "Checklist recreated locally with ${original.items.size} item(s): ${entity.id}")
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
        category: String? = null,
    ): Result<Checklist> =
        withContext(Dispatchers.IO) {
            runCatching {
                val existing =
                    checklistDao.getById(id)
                        ?: throw Exception("Checklist not found")
                val updated =
                    existing.copy(
                        title = title,
                        category = category ?: existing.category,
                        updatedAt = Instant.now().toString(),
                        isDirty = true,
                    )
                checklistDao.update(updated)
                AppLog.d(TAG, "Checklist title updated locally: $id")
                if (isOnline.value) {
                    runCatching { syncChecklist(updated) }
                        .onFailure { AppLog.d(TAG, "Checklist title sync deferred: ${it.message}") }
                }
                checklistDao.getById(id)?.toChecklist()
                    ?: updated.toChecklist()
            }
        }

    // ─── Item operations ────────────────────────────────────────────────────

    /** True when item changes must stay local until [syncChecklist] clears pending ops. */
    private suspend fun needsLocalItemMutations(checklistId: String): Boolean {
        val entity = checklistDao.getById(checklistId) ?: return false
        return entity.isDirty || entity.pendingOps().isNotEmpty()
    }

    suspend fun addItem(
        checklistId: String,
        text: String,
        parentIndex: String? = null,
    ): Result<Checklist> =
        withContext(Dispatchers.IO) {
            runCatching {
                withItemMutation {
                    if (isOnline.value && !needsLocalItemMutations(checklistId)) {
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
                    if (isOnline.value && !needsLocalItemMutations(checklistId)) {
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
                    if (isOnline.value && !needsLocalItemMutations(checklistId)) {
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
                    if (isOnline.value && !needsLocalItemMutations(checklistId)) {
                        api.deleteItem(checklistId, path)
                        refreshFromServer(checklistId)
                    } else {
                        applyOpLocally(checklistId, PendingItemOp(type = "DELETE", path = path))
                    }
                }
            }
        }

    suspend fun renameLeafItem(
        checklistId: String,
        path: String,
        text: String,
    ): Result<Checklist> = updateItemText(checklistId, path, text)

    suspend fun updateItemText(
        checklistId: String,
        path: String,
        text: String,
    ): Result<Checklist> =
        withContext(Dispatchers.IO) {
            runCatching {
                withItemMutation {
                    val itemText = text.trim()
                    if (itemText.isEmpty()) {
                        throw IllegalArgumentException("Item text cannot be empty")
                    }
                    val checklist =
                        checklistDao.getById(checklistId)?.toChecklist()
                            ?: throw Exception("Checklist not found: $checklistId")
                    if (isOnline.value && !needsLocalItemMutations(checklistId)) {
                        updateChecklistItemText(
                            api = api,
                            listId = checklistId,
                            path = path,
                            text = itemText,
                            items = checklist.items,
                            onPatchUnavailable = { ServerCapabilities.markItemPatchLimited(instanceId) },
                        )
                        return@withItemMutation refreshFromServer(checklistId)
                    } else {
                        return@withItemMutation applyOpLocally(
                            checklistId,
                            PendingItemOp(type = "UPDATE", path = path, text = itemText),
                        )
                    }
                }
            }
        }

    suspend fun reorderItems(
        checklistId: String,
        request: ReorderItemsRequest,
    ): Result<Checklist> =
        withContext(Dispatchers.IO) {
            runCatching {
                withItemMutation {
                    if (isOnline.value && !needsLocalItemMutations(checklistId)) {
                        api.reorderItems(checklistId, request)
                        return@withItemMutation refreshFromServer(checklistId)
                    } else {
                        return@withItemMutation applyOpLocally(
                            checklistId,
                            PendingItemOp(
                                type = "REORDER",
                                activeItemId = request.activeItemId,
                                overItemId = request.overItemId,
                                position = request.position,
                                isDropInto = request.isDropInto,
                            ),
                        )
                    }
                }
            }
        }

    // ─── Sync ────────────────────────────────────────────────────────────────

    suspend fun syncChecklists(force: Boolean = false): Result<Unit> =
        syncMutex.withLock {
            syncChecklistsLocked(force)
        }

    private suspend fun syncChecklistsLocked(force: Boolean = false): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (!isOnline.value) return@withContext Result.failure(Exception("Offline"))

            if (itemMutationDepth.get() > 0) {
                pendingSyncAfterMutations.set(true)
                AppLog.d(TAG, "Deferring sync — item mutation in progress")
                return@withContext Result.success(Unit)
            }

            val localEmpty = checklistDao.getAllChecklists(instanceId).isEmpty()
            if (!force && !localEmpty) {
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
                    val pushResult =
                        runCatching {
                            if (entity.isDeleted) {
                                syncDeletedChecklist(entity.id)
                            } else {
                                syncChecklist(entity)
                            }
                        }
                    pushResult.onFailure { e ->
                        if (e is CancellationException) throw e
                        val opSummary =
                            entity.pendingOps().joinToString(",") { op ->
                                buildString {
                                    append(op.type)
                                    op.path?.let { append("@$it") }
                                }
                            }.ifBlank { "none" }
                        AppLog.d(
                            TAG,
                            "Push failed for checklist ${entity.id} (${entity.title}): " +
                                "${ApiErrorHelper.userMessage(appContext, e)}; pendingOps=[$opSummary]",
                        )
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
                if (e is CancellationException) throw e
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
            val created = response.data
            replayItemsToServer(created.id, entity.items(), null)
            checklistDao.delete(entity.id)
            val fresh =
                api.getChecklists().checklists.find { it.id == created.id }
                    ?: throw Exception("Checklist not found after create")
            checklistDao.insert(fresh.toEntity(instanceId))
            AppLog.d(TAG, "Local-only checklist pushed: ${created.id}")
        } else {
            val failedOps = replayPendingOps(entity)
            if (failedOps > 0) {
                throw Exception(appContext.getString(R.string.sync_replay_ops_failed, failedOps))
            }
            pushChecklistMetadataIfNeeded(entity)
            val fresh =
                api.getChecklists().checklists.find { it.id == entity.id }
                    ?: throw Exception("Checklist not found after sync")
            checklistDao.insert(fresh.toEntity(instanceId))
            AppLog.d(TAG, "Checklist synced: ${entity.id}")
        }
    }

    private suspend fun pushChecklistMetadataIfNeeded(entity: ChecklistEntity) {
        if (!needsChecklistMetadataPush(entity)) {
            AppLog.d(TAG, "Checklist metadata unchanged — skipping update for ${entity.id}")
            return
        }
        val category = entity.category.ifBlank { API_CATEGORY_UNCATEGORIZED }
        runCatching {
            api.updateChecklist(
                entity.id,
                UpdateChecklistRequest(title = entity.title, category = category),
            )
        }.onFailure { e ->
            AppLog.d(TAG, "updateChecklist failed for ${entity.id}: ${ApiErrorHelper.userMessage(appContext, e)}")
            throw e
        }
    }

    private suspend fun needsChecklistMetadataPush(entity: ChecklistEntity): Boolean {
        val server =
            api.getChecklists().checklists.find { it.id == entity.id }
                ?: return true
        val localCategory = entity.category.ifBlank { API_CATEGORY_UNCATEGORIZED }
        val serverCategory = server.category.ifBlank { API_CATEGORY_UNCATEGORIZED }
        return entity.title != server.title || localCategory != serverCategory
    }

    /** Re-adds [items] depth-first under [parentPath], preserving order and completion state. */
    private suspend fun replayItemsToServer(
        listId: String,
        items: List<com.jotty.android.data.api.ChecklistItem>,
        parentPath: String?,
    ) {
        items.forEachIndexed { index, item ->
            val path = if (parentPath == null) "$index" else "$parentPath.$index"
            runCatching {
                api.addChecklistItem(
                    listId,
                    AddItemRequest(text = item.text, status = item.status, parentIndex = parentPath),
                )
                val children = item.children.orEmpty()
                if (children.isNotEmpty()) replayItemsToServer(listId, children, path)
                if (item.completed) api.checkItem(listId, path)
            }
        }
    }

    private suspend fun executePendingOp(
        entity: ChecklistEntity,
        op: PendingItemOp,
    ) {
        when (op.type) {
            "CHECK" -> api.checkItem(entity.id, op.path!!)
            "UNCHECK" -> api.uncheckItem(entity.id, op.path!!)
            "ADD" ->
                api.addChecklistItem(
                    entity.id,
                    AddItemRequest(text = op.text ?: "", parentIndex = op.parentIndex),
                )
            "DELETE" -> api.deleteItem(entity.id, op.path!!)
            "UPDATE" ->
                runCatching {
                    api.updateItem(
                        entity.id,
                        op.path!!,
                        UpdateItemRequest(text = op.text),
                    )
                }.getOrElse { error ->
                    if (error is retrofit2.HttpException && error.code() in setOf(404, 405)) {
                        updateChecklistItemText(
                            api = api,
                            listId = entity.id,
                            path = op.path!!,
                            text = op.text ?: "",
                            items = entity.items(),
                            onPatchUnavailable = { ServerCapabilities.markItemPatchLimited(instanceId) },
                        )
                    } else {
                        throw error
                    }
                }
            "REORDER" ->
                api.reorderItems(
                    entity.id,
                    ReorderItemsRequest(
                        activeItemId = op.activeItemId!!,
                        overItemId = op.overItemId!!,
                        position = op.position,
                        isDropInto = op.isDropInto,
                    ),
                )
        }
    }

    /** @return number of ops that failed to replay */
    private suspend fun replayPendingOps(entity: ChecklistEntity): Int {
        var failedOps = 0
        val remaining = mutableListOf<PendingItemOp>()
        var serverItems = fetchServerItems(entity.id) ?: entity.items()

        for (op in entity.pendingOps().distinct()) {
            if (isPendingOpSatisfied(serverItems, op)) {
                AppLog.d(TAG, "Pending op ${op.type} already on server — skipping")
                continue
            }
            val result = runCatching { executePendingOp(entity, op) }
            fetchServerItems(entity.id)?.let { refreshed ->
                serverItems = refreshed
            }
            when {
                result.isSuccess -> Unit
                isPendingOpSatisfied(serverItems, op) ->
                    AppLog.d(TAG, "Pending op ${op.type} satisfied after server refresh — skipping")
                isStaleIdempotentReplayFailure(result.exceptionOrNull(), op, serverItems) ->
                    AppLog.d(TAG, "Pending op ${op.type} treated as satisfied (${result.exceptionOrNull()?.message})")
                isStalePendingOpPath(serverItems, op) -> {
                    AppLog.d(TAG, "Dropping stale pending op ${op.type}@${op.path} (path missing on server)")
                }
                else -> {
                    failedOps += 1
                    remaining.add(op)
                    AppLog.d(TAG, "Pending op ${op.type} failed (stale path?): ${result.exceptionOrNull()?.message}")
                }
            }
        }

        val consumedAny = remaining.size < entity.pendingOps().size
        if (failedOps > 0 || consumedAny) {
            if (failedOps > 0) {
                _replayFailuresDetected.value += failedOps
            }
            val latestItems = fetchServerItems(entity.id) ?: serverItems
            checklistDao.update(
                entity.copy(
                    itemsJson = gson.toJson(latestItems),
                    pendingOpsJson = gson.toJson(remaining),
                    isDirty = remaining.isNotEmpty(),
                ),
            )
            AppLog.d(TAG, "Replay completed with $failedOps failed operation(s), ${remaining.size} remaining")
        }
        return failedOps
    }

    private suspend fun fetchServerItems(checklistId: String): List<ChecklistItem>? =
        api.getChecklists().checklists.find { it.id == checklistId }?.items?.normalizedForLocal()

    private fun isStalePendingOpPath(
        serverItems: List<ChecklistItem>,
        op: PendingItemOp,
    ): Boolean {
        val path = op.path ?: return false
        if (op.type !in setOf("CHECK", "UNCHECK", "DELETE", "UPDATE")) return false
        return itemAtPath(serverItems, path) == null
    }

    private fun isStaleIdempotentReplayFailure(
        error: Throwable?,
        op: PendingItemOp,
        serverItems: List<ChecklistItem>,
    ): Boolean {
        val http = error as? HttpException ?: return false
        return when (op.type) {
            "DELETE" -> http.code() == 404
            "REORDER" -> http.code() in setOf(404, 405)
            "CHECK", "UNCHECK" ->
                http.code() in setOf(400, 404) &&
                    (isPendingOpSatisfied(serverItems, op) || isStalePendingOpPath(serverItems, op))
            else -> false
        }
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
        if (isOnline.value) {
            runtime.coroutineScope.launch { syncChecklists() }
        }
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
