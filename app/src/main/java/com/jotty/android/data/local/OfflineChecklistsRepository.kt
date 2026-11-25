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
import com.jotty.android.util.buildReplaceItemsOps
import com.jotty.android.util.AppLog
import com.jotty.android.util.isServerUnreachable
import com.jotty.android.util.sendBulkOp
import com.jotty.android.util.updateChecklistItemDescription
import com.jotty.android.util.updateChecklistItemRichFields
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
import java.util.concurrent.ConcurrentHashMap
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
    private val syncBackups = SyncBackupRepository(database, instanceId)
    private val itemMutationDepth = AtomicInteger(0)
    private val pendingSyncAfterMutations = AtomicBoolean(false)
    /** Local UUID → server id after [syncChecklist] pushes a local-only checklist. */
    private val localToServerIdRemap = ConcurrentHashMap<String, String>()

    /**
     * Checklists whose replay dropped a local edit the server could no longer take (rename or field
     * edit of an item deleted on the server). Reset at the start of each sync; a "(Local copy)" keeps
     * those edits.
     */
    private val checklistsWithLostEdits = ConcurrentHashMap.newKeySet<String>()

    @Volatile private var lastSyncCompletedAtMs: Long? = null
    /** Shared across UI + [OfflineSyncWorker] repos for this [instanceId]. */
    private val syncMutex = OfflineSyncLocks.forChecklists(instanceId)
    private val runtime =
        OfflineRepositoryLifecycle(
            context = context,
            initialOnlineOverride = initialOnlineOverride,
            useSharedConnectivity = useSharedConnectivity,
            logTag = TAG,
            instanceId = instanceId,
            probeServer = { api.isReachable() },
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
        checklistDao.getConflictCopiesFlow(instanceId)
            .map { entities -> entities.map { it.toChecklist() } }
            .flowOn(Dispatchers.IO)

    /** The conflict copy [copyId] with the checklist it was split from (null when that list is gone). */
    suspend fun getConflict(copyId: String): ChecklistConflict? =
        withContext(Dispatchers.IO) {
            val copy = checklistDao.getById(copyId)?.takeIf { it.isConflictCopy } ?: return@withContext null
            ChecklistConflict(copy = copy, original = copy.conflictOfId?.let { checklistDao.getById(it) })
        }

    /** Keep the server version: drops the local copy. */
    suspend fun resolveConflictKeepServer(copyId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                requireConflictCopy(copyId)
                checklistDao.delete(copyId)
                AppLog.d(TAG, "Conflict copy discarded: $copyId")
            }
        }

    /**
     * Keep mine: queues item ops on the original that delete its items and re-add the copy's
     * ([buildReplaceItemsOps]), takes the copy's title and category, and drops the copy. The ops go
     * through the normal positional queue, so this works offline and replays in order on sync.
     */
    suspend fun resolveConflictKeepMine(copyId: String): Result<Checklist> =
        withContext(Dispatchers.IO) {
            runCatching {
                val result =
                    withItemMutation {
                        val copy = requireConflictCopy(copyId)
                        val original =
                            copy.conflictOfId?.let { checklistDao.getById(it) }
                                ?: throw IllegalStateException(appContext.getString(R.string.conflict_original_missing))
                        val ops = buildReplaceItemsOps(original.items(), copy.items())
                        val newItems = ops.fold(original.items()) { items, op -> applyOpToItems(items, op) }
                        val updated =
                            original.withFirstDirtyBaseline(
                                original.copy(
                                    title = copy.title.removeSuffix(LOCAL_COPY_SUFFIX),
                                    category = copy.category,
                                    itemsJson = gson.toJson(newItems),
                                    // Keep every queued op in order after the ones already waiting (#120).
                                    pendingOpsJson = gson.toJson(original.pendingOps() + ops),
                                    isDirty = true,
                                    updatedAt = Instant.now().toString(),
                                ),
                            )
                        database.withTransaction {
                            checklistDao.update(updated)
                            checklistDao.delete(copyId)
                        }
                        AppLog.d(TAG, "Conflict resolved with local version: $copyId -> ${original.id} (${ops.size} ops)")
                        updated.toChecklist()
                    }
                if (isOnline.value) runtime.coroutineScope.launch { syncChecklists(force = true) }
                result
            }
        }

    /** Keep both: the copy becomes a normal local-only checklist that is created on the server on sync. */
    suspend fun resolveConflictKeepBoth(copyId: String): Result<Checklist> =
        withContext(Dispatchers.IO) {
            runCatching {
                val copy = requireConflictCopy(copyId)
                val promoted =
                    copy.copy(
                        isConflictCopy = false,
                        conflictOfId = null,
                        isLocalOnly = true,
                        isDirty = true,
                        pendingOpsJson = "[]",
                        updatedAt = Instant.now().toString(),
                        dirtySinceEpochMs = System.currentTimeMillis(),
                    )
                checklistDao.insert(promoted)
                AppLog.d(TAG, "Conflict copy kept as a separate checklist: $copyId")
                // Same as createChecklist: no inline push, since the push swaps the local id.
                if (isOnline.value) runtime.coroutineScope.launch { syncChecklists(force = true) }
                promoted.toChecklist()
            }
        }

    private suspend fun requireConflictCopy(copyId: String): ChecklistEntity =
        checklistDao.getById(copyId)?.takeIf { it.isConflictCopy }
            ?: throw IllegalStateException(appContext.getString(R.string.conflict_gone))

    fun getDirtyChecklistIdsFlow(): Flow<Set<String>> =
        checklistDao.getDirtyChecklistIdsFlow(instanceId)
            .map { it.toSet() }
            .flowOn(Dispatchers.IO)

    /** Dirty and soft-deleted checklists awaiting sync (includes pending deletes). */
    fun getPendingSyncItemsFlow(): Flow<List<PendingSyncItem>> =
        checklistDao.getDirtyChecklistIdsFlow(instanceId)
            .map {
                checklistDao.getDirty(instanceId).map { SyncPayloadCodec.checklistEntityToPendingItem(it) }
            }
            .flowOn(Dispatchers.IO)

    suspend fun getPendingSyncItems(): List<PendingSyncItem> =
        withContext(Dispatchers.IO) {
            checklistDao.getDirty(instanceId).map { SyncPayloadCodec.checklistEntityToPendingItem(it) }
        }

    suspend fun getPendingSyncItem(checklistId: String): PendingSyncItem? =
        withContext(Dispatchers.IO) {
            checklistDao.getByIdIncludingDeleted(resolveChecklistId(checklistId))
                ?.takeIf { it.isDirty || it.isDeleted || it.pendingOps().isNotEmpty() }
                ?.let { SyncPayloadCodec.checklistEntityToPendingItem(it) }
        }

    suspend fun listSyncBackups(checklistId: String): List<SyncBackup> =
        syncBackups.listForItem(SyncBackupKind.CHECKLIST, resolveChecklistId(checklistId))

    suspend fun getLocalSyncPayload(checklistId: String): ChecklistSyncPayload? =
        withContext(Dispatchers.IO) {
            checklistDao.getByIdIncludingDeleted(resolveChecklistId(checklistId))?.toSyncPayload()
        }

    suspend fun getServerSyncPayload(checklistId: String): ChecklistSyncPayload? =
        withContext(Dispatchers.IO) {
            val resolved = resolveChecklistId(checklistId)
            val local = checklistDao.getByIdIncludingDeleted(resolved)
            if (local?.isLocalOnly == true) return@withContext null
            if (isOnline.value) {
                runCatching {
                    api.getChecklists().checklists.find { it.id == resolved }?.let { list ->
                        ChecklistSyncPayload(
                            id = list.id,
                            title = list.title,
                            category = list.category,
                            type = list.type,
                            itemsJson = gson.toJson(list.items.normalizedForLocal()),
                            createdAt = list.createdAt,
                            updatedAt = list.updatedAt,
                        )
                    }
                }.getOrNull()?.let { return@withContext it }
            }
            local?.syncBaselineJson?.let { SyncPayloadCodec.decodeChecklist(it) }
        }

    fun clearConflictNotification() {
        runtime.syncStatus.setConflictsDetected(0)
    }

    fun clearReplayFailureNotification() {
        _replayFailuresDetected.value = 0
    }

    suspend fun isLocalOnlyChecklist(checklistId: String): Boolean =
        withContext(Dispatchers.IO) {
            checklistDao.getByIdIncludingDeleted(resolveChecklistId(checklistId))?.isLocalOnly == true
        }

    /** Server id for a checklist that was created offline and has since synced, if known. */
    fun remappedChecklistId(localId: String): String? = localToServerIdRemap[localId]

    /**
     * Abandons unsynced local changes. Local-only checklists are deleted. Server-backed checklists
     * are replaced with the current server version (requires connectivity). Creates a local backup first.
     */
    suspend fun discardPendingSync(checklistId: String): Result<Checklist?> =
        pullFromServer(checklistId)

    /**
     * Overwrite server with the local copy. Backs up the server/baseline payload first when available.
     */
    suspend fun forcePushLocal(checklistId: String): Result<Checklist?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolved = resolveChecklistId(checklistId)
                val entity =
                    checklistDao.getByIdIncludingDeleted(resolved)
                        ?: throw Exception(appContext.getString(R.string.error_not_found))
                if (!entity.isDirty && !entity.isDeleted && entity.pendingOps().isEmpty()) {
                    return@runCatching entity.toChecklist()
                }
                val serverPayload = getServerSyncPayload(resolved)
                if (serverPayload != null) {
                    syncBackups.save(
                        kind = SyncBackupKind.CHECKLIST,
                        itemId = resolved,
                        direction = SyncBackupDirection.BEFORE_PUSH_LOCAL,
                        title = serverPayload.title,
                        payloadJson = SyncPayloadCodec.encodeChecklist(serverPayload),
                        pendingOpsJson = null,
                    )
                }
                if (entity.isDeleted) {
                    if (!isOnline.value) {
                        throw Exception(appContext.getString(R.string.discard_pending_sync_offline))
                    }
                    syncMutex.withLock { syncDeletedChecklistLocked(resolved) }
                    return@runCatching null
                }
                if (!isOnline.value) {
                    throw Exception(appContext.getString(R.string.discard_pending_sync_offline))
                }
                syncMutex.withLock { syncChecklistLocked(entity) }
                checklistDao.getById(resolved)?.toChecklist()
                    ?: localToServerIdRemap[resolved]?.let { checklistDao.getById(it)?.toChecklist() }
                    ?: throw Exception(appContext.getString(R.string.sync_force_push_failed))
            }
        }

    /**
     * Overwrite local with the server copy. Backs up the local payload first.
     */
    /**
     * Replaces the local clean copy of a checklist with a server snapshot
     * (e.g. after [sortChecklistItems]). Refuses when the local row is dirty.
     */
    suspend fun applyServerChecklistSnapshot(checklist: Checklist): Result<Checklist> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolved = resolveChecklistId(checklist.id)
                val entity = checklistDao.getByIdIncludingDeleted(resolved)
                if (entity != null && (entity.isDirty || entity.isDeleted || entity.pendingOps().isNotEmpty())) {
                    throw IllegalStateException(appContext.getString(R.string.sort_items_pending_sync))
                }
                val snapshot = checklist.copy(id = resolved)
                checklistDao.insert(snapshot.toEntity(instanceId))
                snapshot
            }
        }

    suspend fun pullFromServer(checklistId: String): Result<Checklist?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolved = resolveChecklistId(checklistId)
                val entity =
                    checklistDao.getByIdIncludingDeleted(resolved)
                        ?: throw Exception(appContext.getString(R.string.error_not_found))
                if (!entity.isDirty && !entity.isDeleted && entity.pendingOps().isEmpty()) {
                    return@runCatching entity.toChecklist()
                }
                syncBackups.save(
                    kind = SyncBackupKind.CHECKLIST,
                    itemId = resolved,
                    direction = SyncBackupDirection.BEFORE_PULL_SERVER,
                    title = entity.title,
                    payloadJson = SyncPayloadCodec.encodeChecklist(entity),
                    pendingOpsJson = entity.pendingOpsJson,
                )
                if (entity.isLocalOnly) {
                    checklistDao.delete(resolved)
                    AppLog.d(TAG, "Local-only checklist discarded: $resolved")
                    return@runCatching null
                }
                if (!isOnline.value) {
                    throw Exception(appContext.getString(R.string.discard_pending_sync_offline))
                }
                val fresh =
                    api.getChecklists().checklists.find { it.id == resolved }
                        ?: throw Exception(appContext.getString(R.string.error_not_found))
                checklistDao.insert(fresh.toEntity(instanceId))
                AppLog.d(TAG, "Pending sync discarded — restored checklist from server: $resolved")
                fresh
            }
        }

    suspend fun restoreSyncBackup(backupId: Long): Result<Checklist> =
        withContext(Dispatchers.IO) {
            runCatching {
                val backup =
                    syncBackups.getById(backupId)
                        ?: throw Exception(appContext.getString(R.string.error_not_found))
                if (backup.kind != SyncBackupKind.CHECKLIST) {
                    throw IllegalArgumentException("Backup is not a checklist")
                }
                val payload =
                    SyncPayloadCodec.decodeChecklist(backup.payloadJson)
                        ?: throw Exception(appContext.getString(R.string.sync_backup_invalid))
                val now = Instant.now().toString()
                val entity =
                    ChecklistEntity(
                        id = payload.id,
                        title = payload.title,
                        category = payload.category,
                        type = payload.type,
                        itemsJson = payload.itemsJson,
                        pendingOpsJson = backup.pendingOpsJson ?: payload.pendingOpsJson,
                        createdAt = payload.createdAt,
                        updatedAt = now,
                        isDirty = true,
                        isDeleted = false,
                        instanceId = instanceId,
                        isLocalOnly = false,
                        syncBaselineJson = SyncPayloadCodec.encodeChecklist(payload),
                        dirtySinceEpochMs = System.currentTimeMillis(),
                    )
                checklistDao.insert(entity)
                entity.toChecklist()
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
                        dirtySinceEpochMs = System.currentTimeMillis(),
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
                        dirtySinceEpochMs = System.currentTimeMillis(),
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
                if (entity != null && (entity.isLocalOnly || entity.isConflictCopy)) {
                    checklistDao.delete(id)
                    AppLog.d(TAG, "Local-only checklist hard-deleted: $id")
                } else if (entity != null) {
                    checklistDao.update(
                        entity.withFirstDirtyBaseline(
                            entity.copy(isDeleted = true, isDirty = true),
                        ),
                    )
                    AppLog.d(TAG, "Checklist marked for deletion: $id")
                    pushDeleteOrDefer(id)
                } else {
                    checklistDao.markAsDeleted(id)
                    AppLog.d(TAG, "Checklist marked for deletion: $id")
                    pushDeleteOrDefer(id)
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
                if (existing.isConflictCopy) {
                    // A conflict copy has no server row: edit it in place and never push it.
                    val edited =
                        existing.copy(
                            title = title,
                            category = category ?: existing.category,
                            updatedAt = Instant.now().toString(),
                        )
                    checklistDao.update(edited)
                    return@runCatching edited.toChecklist()
                }
                val updated =
                    existing.withFirstDirtyBaseline(
                        existing.copy(
                            title = title,
                            category = category ?: existing.category,
                            updatedAt = Instant.now().toString(),
                            isDirty = true,
                        ),
                    )
                checklistDao.update(updated)
                AppLog.d(TAG, "Checklist title updated locally: $id")
                if (isOnline.value) {
                    runCatching {
                        syncMutex.withLock {
                            checklistDao.getById(id)?.let { syncChecklistLocked(it) }
                        }
                    }.onFailure {
                        if (it is CancellationException) throw it
                        if (it.isServerUnreachable()) runtime.reportServerUnreachable()
                        AppLog.d(TAG, "Checklist title sync deferred: ${it.message}")
                    }
                }
                checklistDao.getById(id)?.toChecklist()
                    ?: updated.toChecklist()
            }
        }

    // ─── Item operations ────────────────────────────────────────────────────

    /** True when item changes must stay local until [syncChecklist] clears pending ops. */
    private suspend fun needsLocalItemMutations(checklistId: String): Boolean {
        val entity =
            checklistDao.getById(resolveChecklistId(checklistId))
                ?: return true
        return entity.isDirty || entity.pendingOps().isNotEmpty()
    }

    /** Conflict copies have no server row: their item edits stay local and are never queued. */
    private suspend fun isConflictCopy(checklistId: String): Boolean = checklistDao.getById(checklistId)?.isConflictCopy == true

    private suspend fun resolveChecklistId(checklistId: String): String {
        if (checklistDao.getById(checklistId) != null) return checklistId
        val remapped = localToServerIdRemap[checklistId] ?: return checklistId
        return if (checklistDao.getById(remapped) != null) remapped else checklistId
    }

    /**
     * Runs one item mutation: against the server when online and the list is clean, otherwise
     * as a local change plus a [PendingItemOp] replayed on the next sync.
     *
     * If the server cannot be reached ([isServerUnreachable]) the mutation falls back to the
     * local path instead of failing, so nothing the user typed is lost (#115). A server that
     * answers with an error still fails the call.
     */
    private suspend fun mutateItem(
        resolvedId: String,
        op: PendingItemOp,
        remote: suspend () -> Unit,
    ): Checklist {
        if (isConflictCopy(resolvedId)) {
            return applyOpLocally(resolvedId, op, queueForReplay = false, trySync = false)
        }
        if (!isOnline.value || needsLocalItemMutations(resolvedId)) {
            return applyOpLocally(resolvedId, op)
        }
        try {
            remote()
        } catch (e: Exception) {
            if (!e.isServerUnreachable()) throw e
            AppLog.d(TAG, "Server unreachable — ${op.type} saved locally: ${e.message}")
            runtime.reportServerUnreachable()
            return applyOpLocally(resolvedId, op, trySync = false)
        }
        return try {
            refreshFromServer(resolvedId)
        } catch (e: Exception) {
            if (!e.isServerUnreachable()) throw e
            // The server already applied this change; mirror it locally without queueing a
            // replay, which would apply it twice (e.g. a duplicated item).
            AppLog.d(TAG, "Server unreachable after ${op.type} — mirrored locally: ${e.message}")
            runtime.reportServerUnreachable()
            applyOpLocally(resolvedId, op, queueForReplay = false, trySync = false)
        }
    }

    /** Pushes a soft-deleted checklist now, or leaves it queued when the server is unreachable. */
    private suspend fun pushDeleteOrDefer(id: String) {
        if (!isOnline.value) return
        try {
            syncMutex.withLock { syncDeletedChecklistLocked(id) }
        } catch (e: Exception) {
            if (!e.isServerUnreachable()) throw e
            AppLog.d(TAG, "Server unreachable — checklist delete queued: ${e.message}")
            runtime.reportServerUnreachable()
        }
    }

    suspend fun addItem(
        checklistId: String,
        text: String,
        parentIndex: String? = null,
        status: String? = null,
    ): Result<Checklist> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolvedId = resolveChecklistId(checklistId)
                withItemMutation {
                    mutateItem(
                        resolvedId,
                        PendingItemOp(type = "ADD", text = text, parentIndex = parentIndex, status = status),
                    ) {
                        api.addChecklistItem(
                            resolvedId,
                            AddItemRequest(text = text, parentIndex = parentIndex, status = status),
                        )
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
                val resolvedId = resolveChecklistId(checklistId)
                withItemMutation {
                    mutateItem(resolvedId, PendingItemOp(type = "CHECK", path = path)) {
                        api.checkItem(resolvedId, path)
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
                val resolvedId = resolveChecklistId(checklistId)
                withItemMutation {
                    mutateItem(resolvedId, PendingItemOp(type = "UNCHECK", path = path)) {
                        api.uncheckItem(resolvedId, path)
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
                val resolvedId = resolveChecklistId(checklistId)
                withItemMutation {
                    mutateItem(resolvedId, PendingItemOp(type = "DELETE", path = path)) {
                        api.deleteItem(resolvedId, path)
                    }
                }
            }
        }

    /**
     * Applies a batch of CHECK / UNCHECK / DELETE ops (see [com.jotty.android.util.checklistBulkOps])
     * with one refresh at the end instead of one per item.
     *
     * Offline or with pending changes, all ops are applied and queued locally. Online, they are
     * sent one by one; if the server becomes unreachable part-way, the ops it already applied are
     * mirrored locally and the rest are queued for the next sync (#115).
     */
    suspend fun applyBulkItemOps(
        checklistId: String,
        ops: List<PendingItemOp>,
    ): Result<Checklist> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolvedId = resolveChecklistId(checklistId)
                withItemMutation {
                    if (isConflictCopy(resolvedId)) {
                        return@withItemMutation applyOpsLocally(resolvedId, ops, queueForReplay = false, trySync = false)
                    }
                    if (!isOnline.value || needsLocalItemMutations(resolvedId)) {
                        return@withItemMutation applyOpsLocally(resolvedId, ops)
                    }
                    var sent = 0
                    try {
                        for (op in ops) {
                            api.sendBulkOp(resolvedId, op)
                            sent++
                        }
                    } catch (e: Exception) {
                        if (!e.isServerUnreachable()) {
                            // Show what the server did apply before reporting the failure.
                            runCatching { refreshFromServer(resolvedId) }
                            throw e
                        }
                        AppLog.d(TAG, "Server unreachable after $sent/${ops.size} bulk ops — rest saved locally")
                        runtime.reportServerUnreachable()
                        applyOpsLocally(resolvedId, ops.take(sent), queueForReplay = false, trySync = false)
                        return@withItemMutation applyOpsLocally(resolvedId, ops.drop(sent), trySync = false)
                    }
                    try {
                        refreshFromServer(resolvedId)
                    } catch (e: Exception) {
                        if (!e.isServerUnreachable()) throw e
                        runtime.reportServerUnreachable()
                        applyOpsLocally(resolvedId, ops, queueForReplay = false, trySync = false)
                    }
                }
            }
        }

    private suspend fun applyOpsLocally(
        checklistId: String,
        ops: List<PendingItemOp>,
        queueForReplay: Boolean = true,
        trySync: Boolean = true,
    ): Checklist {
        var result: Checklist? = null
        ops.forEachIndexed { i, op ->
            result = applyOpLocally(checklistId, op, queueForReplay, trySync = trySync && i == ops.lastIndex)
        }
        return result
            ?: checklistDao.getById(checklistId)?.toChecklist()
            ?: throw Exception("Checklist not found: $checklistId")
    }

    suspend fun updateItemText(
        checklistId: String,
        path: String,
        text: String,
    ): Result<Checklist> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolvedId = resolveChecklistId(checklistId)
                withItemMutation {
                    val itemText = text.trim()
                    if (itemText.isEmpty()) {
                        throw IllegalArgumentException("Item text cannot be empty")
                    }
                    mutateItem(resolvedId, PendingItemOp(type = "UPDATE", path = path, text = itemText)) {
                        updateChecklistItemText(
                            api = api,
                            listId = resolvedId,
                            path = path,
                            text = itemText,
                        )
                    }
                }
            }
        }

    suspend fun updateItemDescription(
        checklistId: String,
        path: String,
        description: String,
    ): Result<Checklist> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolvedId = resolveChecklistId(checklistId)
                withItemMutation {
                    mutateItem(
                        resolvedId,
                        PendingItemOp(type = "UPDATE", path = path, description = description),
                    ) {
                        updateChecklistItemDescription(
                            api = api,
                            listId = resolvedId,
                            path = path,
                            description = description,
                        )
                    }
                }
            }
        }

    suspend fun updateItemRichFields(
        checklistId: String,
        path: String,
        patch: Map<String, Any?>,
    ): Result<Checklist> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (patch.isEmpty()) {
                    val resolvedId = resolveChecklistId(checklistId)
                    return@runCatching checklistDao.getById(resolvedId)?.toChecklist()
                        ?: throw IllegalStateException("Checklist not found: $checklistId")
                }
                val resolvedId = resolveChecklistId(checklistId)
                withItemMutation {
                    mutateItem(resolvedId, patch.toPendingItemOp(path)) {
                        updateChecklistItemRichFields(
                            api = api,
                            listId = resolvedId,
                            path = path,
                            patch = patch,
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
                val resolvedId = resolveChecklistId(checklistId)
                withItemMutation {
                    mutateItem(
                        resolvedId,
                        PendingItemOp(
                            type = "REORDER",
                            activeItemId = request.activeItemId,
                            overItemId = request.overItemId,
                            position = request.position,
                            isDropInto = request.isDropInto,
                        ),
                    ) {
                        api.reorderItems(resolvedId, request)
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
            if (!isOnline.value) {
                runtime.requestReachabilityProbe()
                return@withContext Result.failure(Exception("Offline"))
            }

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
            checklistsWithLostEdits.clear()
            AppLog.d(TAG, "Starting sync…")
            try {
                val localBefore = checklistDao.getAllChecklists(instanceId).associateBy { it.id }

                val dirty = checklistDao.getDirty(instanceId)
                AppLog.d(TAG, "${dirty.size} dirty checklists")
                for (entity in dirty) {
                    val pushResult =
                        runCatching {
                            if (entity.isDeleted) {
                                syncDeletedChecklistLocked(entity.id)
                            } else {
                                syncChecklistLocked(entity)
                            }
                        }
                    pushResult.onFailure { e ->
                        if (e is CancellationException) throw e
                        if (e.isServerUnreachable()) runtime.reportServerUnreachable()
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
                        if (hasConflict(local, serverList, lostEdits = local.id in checklistsWithLostEdits)) {
                            conflictCopies.add(local.toConflictCopy())
                            AppLog.d(TAG, "Conflict detected for '${local.title}', local copy created")
                        }
                    }
                }

                // Atomic replace: observers see either the old list or the new list, never empty.
                database.withTransaction {
                    checklistDao.deleteSynced(instanceId)
                    checklistDao.insertAll(serverChecklists.map { it.toEntity(instanceId) })
                    if (conflictCopies.isNotEmpty()) checklistDao.insertAll(conflictCopies)
                }
                runtime.syncStatus.setConflictsDetected(if (conflictCopies.isNotEmpty()) conflictCopies.size else 0)
                runtime.syncStatus.markSyncCompleted(success = true)
                runtime.reportServerReachable()
                lastSyncCompletedAtMs = System.currentTimeMillis()

                AppLog.d(TAG, "Sync complete — ${serverChecklists.size} checklists from server")
                Result.success(Unit)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (e.isServerUnreachable()) runtime.reportServerUnreachable()
                val msg = ApiErrorHelper.userMessage(appContext, e)
                runtime.syncStatus.markSyncCompleted(success = false, errorMessage = msg)
                AppLog.d(TAG, "Sync failed: $msg")
                Result.failure(Exception(msg, e))
            } finally {
                runtime.syncStatus.setSyncing(false)
            }
        }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /**
     * [local] is the row as it was before the push; [server] is the server copy after it.
     *
     * Queued item ops were replayed onto the server's current items, so the server copy already holds
     * them merged with any remote edits: differing items are not a conflict then. A conflict is a title
     * or category that did not stick, an op the server could not take ([lostEdits]), or, for a row
     * without queued ops, items that differ.
     */
    private fun hasConflict(
        local: ChecklistEntity,
        server: Checklist,
        lostEdits: Boolean,
    ): Boolean {
        if (lostEdits) return true
        if (local.title != server.title || local.category != server.category) return true
        if (local.pendingOps().isNotEmpty()) return false
        return gson.toJson(local.items()) != gson.toJson(server.items)
    }

    /** A "(Local copy)" of [this] (a dirty list the server changed too), kept until resolved. */
    private fun ChecklistEntity.toConflictCopy(): ChecklistEntity =
        copy(
            id = UUID.randomUUID().toString(),
            title = "$title$LOCAL_COPY_SUFFIX",
            isDirty = false,
            isDeleted = false,
            isLocalOnly = false,
            pendingOpsJson = "[]",
            syncBaselineJson = null,
            dirtySinceEpochMs = null,
            isConflictCopy = true,
            conflictOfId = id,
        )

    private suspend fun syncChecklistLocked(entity: ChecklistEntity) {
        // Re-read under the shared lock so concurrent repos do not create twice.
        val current = checklistDao.getById(entity.id) ?: return
        if (current.isConflictCopy) return
        if (!current.isDirty && !current.isLocalOnly && current.pendingOps().isEmpty()) return
        if (current.isLocalOnly) {
            val localId = current.id
            val response =
                api.createChecklist(
                    CreateChecklistRequest(
                        title = current.title,
                        category = current.category,
                        type = current.type,
                    ),
                )
            val created = response.data
            localToServerIdRemap[localId] = created.id
            replayItemsToServer(created.id, current.items(), null)
            checklistDao.delete(current.id)
            val fresh =
                api.getChecklists().checklists.find { it.id == created.id }
                    ?: throw Exception("Checklist not found after create")
            checklistDao.insert(fresh.toEntity(instanceId))
            AppLog.d(TAG, "Local-only checklist pushed: ${created.id}")
        } else {
            val failedOps = replayPendingOps(current)
            if (failedOps > 0) {
                throw Exception(appContext.getString(R.string.sync_replay_ops_failed, failedOps))
            }
            pushChecklistMetadataIfNeeded(current)
            val fresh =
                api.getChecklists().checklists.find { it.id == current.id }
                    ?: throw Exception("Checklist not found after sync")
            checklistDao.insert(fresh.toEntity(instanceId))
            AppLog.d(TAG, "Checklist synced: ${current.id}")
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
        com.jotty.android.util.replayChecklistItemsToServer(api, listId, items, parentPath)
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
                    AddItemRequest(
                        text = op.text ?: "",
                        parentIndex = op.parentIndex,
                        status = op.status,
                    ),
                )
            "DELETE" -> api.deleteItem(entity.id, op.path!!)
            "UPDATE" -> {
                val path = requireNotNull(op.path) { "UPDATE op missing path" }
                val richPatch = op.toRichFieldsPatch()
                if (richPatch != null) {
                    updateChecklistItemRichFields(
                        api = api,
                        listId = entity.id,
                        path = path,
                        patch = richPatch,
                    )
                } else {
                    api.updateItem(
                        entity.id,
                        path,
                        UpdateItemRequest(text = op.text, description = op.description),
                    )
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

        for (op in entity.pendingOps()) {
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
                    // A check or delete of a vanished item loses nothing; a rename or field edit does.
                    if (op.type == "UPDATE") checklistsWithLostEdits.add(entity.id)
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

    private suspend fun syncDeletedChecklistLocked(id: String) {
        val current = checklistDao.getByIdIncludingDeleted(id) ?: return
        if (!current.isDeleted) return
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

    /**
     * @param queueForReplay false when the server already has this change and only the local
     *   copy needs to catch up; the list is then not marked dirty by this op.
     * @param trySync false when we just learned the server is unreachable.
     */
    private suspend fun applyOpLocally(
        checklistId: String,
        op: PendingItemOp,
        queueForReplay: Boolean = true,
        trySync: Boolean = true,
    ): Checklist {
        val entity =
            checklistDao.getById(checklistId)
                ?: throw Exception("Checklist not found: $checklistId")
        val newItems = applyOpToItems(entity.items(), op)
        if (!queueForReplay) {
            val mirrored = entity.copy(itemsJson = gson.toJson(newItems), updatedAt = Instant.now().toString())
            checklistDao.update(mirrored)
            return mirrored.toChecklist()
        }
        // Keep every op in order: positional ops (CHECK 0, DELETE 0, …) repeat legitimately (#120).
        val newOps = entity.pendingOps() + op
        val updated =
            entity.withFirstDirtyBaseline(
                entity.copy(
                    itemsJson = gson.toJson(newItems),
                    pendingOpsJson = gson.toJson(newOps),
                    isDirty = true,
                    updatedAt = Instant.now().toString(),
                ),
            )
        checklistDao.update(updated)
        if (trySync && isOnline.value) {
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
