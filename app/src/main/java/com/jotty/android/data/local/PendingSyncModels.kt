package com.jotty.android.data.local

/**
 * Unified pending-sync row for notes and checklists (including soft-deleted checklists).
 */
data class PendingSyncItem(
    val id: String,
    val kind: SyncBackupKind,
    val title: String,
    val category: String,
    val updatedAt: String,
    val dirtySinceEpochMs: Long?,
    val isLocalOnly: Boolean,
    val isPendingDelete: Boolean,
    val pendingOpCount: Int = 0,
    /** Stored server-shaped baseline JSON, if captured when the row first became dirty. */
    val syncBaselineJson: String? = null,
)

/** JSON snapshot of a note used for baselines, diffs, and sync backups. */
data class NoteSyncPayload(
    val id: String,
    val title: String,
    val category: String,
    val content: String,
    val createdAt: String,
    val updatedAt: String,
    val encrypted: Boolean?,
)

/** JSON snapshot of a checklist used for baselines, diffs, and sync backups. */
data class ChecklistSyncPayload(
    val id: String,
    val title: String,
    val category: String,
    val type: String,
    val itemsJson: String,
    val createdAt: String,
    val updatedAt: String,
    val pendingOpsJson: String = "[]",
)

fun NoteEntity.toSyncPayload(): NoteSyncPayload =
    NoteSyncPayload(
        id = id,
        title = title,
        category = category,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
        encrypted = encrypted,
    )

fun ChecklistEntity.toSyncPayload(): ChecklistSyncPayload =
    ChecklistSyncPayload(
        id = id,
        title = title,
        category = category,
        type = type,
        itemsJson = itemsJson,
        createdAt = createdAt,
        updatedAt = updatedAt,
        pendingOpsJson = pendingOpsJson,
    )
