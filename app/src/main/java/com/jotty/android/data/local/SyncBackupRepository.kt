package com.jotty.android.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stores on-device backups taken around pending-sync resolve (push local / pull server).
 */
class SyncBackupRepository(
    database: JottyDatabase,
    private val instanceId: String,
) {
    private val dao = database.syncBackupDao()

    suspend fun listForItem(
        kind: SyncBackupKind,
        itemId: String,
    ): List<SyncBackup> =
        withContext(Dispatchers.IO) {
            dao.listForItem(instanceId, kind.name, itemId).map { it.toSyncBackup() }
        }

    suspend fun getById(id: Long): SyncBackup? =
        withContext(Dispatchers.IO) {
            dao.getById(id)?.toSyncBackup()
        }

    suspend fun save(
        kind: SyncBackupKind,
        itemId: String,
        direction: SyncBackupDirection,
        title: String,
        payloadJson: String,
        pendingOpsJson: String? = null,
    ): SyncBackup =
        withContext(Dispatchers.IO) {
            val id =
                dao.insert(
                    SyncBackupEntity(
                        instanceId = instanceId,
                        kind = kind.name,
                        itemId = itemId,
                        direction = direction.name,
                        createdAtEpochMs = System.currentTimeMillis(),
                        title = title,
                        payloadJson = payloadJson,
                        pendingOpsJson = pendingOpsJson,
                    ),
                )
            val all = dao.listForItem(instanceId, kind.name, itemId)
            if (all.size > MAX_BACKUPS_PER_ITEM) {
                all.drop(MAX_BACKUPS_PER_ITEM).forEach { dao.deleteById(it.id) }
            }
            requireNotNull(dao.getById(id)).toSyncBackup()
        }

    companion object {
        const val MAX_BACKUPS_PER_ITEM = 10
    }
}
