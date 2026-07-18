package com.jotty.android.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Kind of item a [SyncBackupEntity] belongs to. */
enum class SyncBackupKind {
    NOTE,
    CHECKLIST,
}

/**
 * Direction of the resolution that created the backup.
 * - [BEFORE_PUSH_LOCAL]: server (or baseline) snapshot saved before overwriting the server with local
 * - [BEFORE_PULL_SERVER]: local snapshot saved before overwriting local from the server
 */
enum class SyncBackupDirection {
    BEFORE_PUSH_LOCAL,
    BEFORE_PULL_SERVER,
}

/**
 * On-device backup taken around pending-sync resolve actions so the user can restore either side.
 */
@Entity(
    tableName = "sync_backups",
    indices = [
        Index("instanceId"),
        Index("itemId"),
        Index(value = ["instanceId", "kind", "itemId"]),
    ],
)
data class SyncBackupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val instanceId: String,
    /** [SyncBackupKind] name */
    val kind: String,
    val itemId: String,
    /** [SyncBackupDirection] name */
    val direction: String,
    val createdAtEpochMs: Long,
    val title: String,
    /** Full local or server payload JSON (note or checklist shaped). */
    val payloadJson: String,
    /** Checklist pending ops at backup time, if any. */
    val pendingOpsJson: String? = null,
)

data class SyncBackup(
    val id: Long,
    val instanceId: String,
    val kind: SyncBackupKind,
    val itemId: String,
    val direction: SyncBackupDirection,
    val createdAtEpochMs: Long,
    val title: String,
    val payloadJson: String,
    val pendingOpsJson: String? = null,
)

fun SyncBackupEntity.toSyncBackup(): SyncBackup =
    SyncBackup(
        id = id,
        instanceId = instanceId,
        kind = runCatching { SyncBackupKind.valueOf(kind) }.getOrDefault(SyncBackupKind.NOTE),
        itemId = itemId,
        direction =
            runCatching { SyncBackupDirection.valueOf(direction) }
                .getOrDefault(SyncBackupDirection.BEFORE_PULL_SERVER),
        createdAtEpochMs = createdAtEpochMs,
        title = title,
        payloadJson = payloadJson,
        pendingOpsJson = pendingOpsJson,
    )
