package com.jotty.android.data.local

import com.google.gson.Gson
import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.Note
import com.jotty.android.data.api.normalizedForLocal
import com.jotty.android.util.exportChecklistAsPlainText
import com.google.gson.reflect.TypeToken

/** Serialize/deserialize note and checklist sync payloads for baselines, backups, and diffs. */
object SyncPayloadCodec {
    private val gson = Gson()
    private val itemListType = object : TypeToken<List<ChecklistItem>>() {}.type

    fun encodeNote(entity: NoteEntity): String = gson.toJson(entity.toSyncPayload())

    fun encodeNote(payload: NoteSyncPayload): String = gson.toJson(payload)

    fun decodeNote(json: String): NoteSyncPayload? =
        runCatching { gson.fromJson(json, NoteSyncPayload::class.java) }.getOrNull()

    fun encodeChecklist(entity: ChecklistEntity): String = gson.toJson(entity.toSyncPayload())

    fun encodeChecklist(payload: ChecklistSyncPayload): String = gson.toJson(payload)

    fun decodeChecklist(json: String): ChecklistSyncPayload? =
        runCatching { gson.fromJson(json, ChecklistSyncPayload::class.java) }.getOrNull()

    fun notePayloadToNote(payload: NoteSyncPayload): Note =
        Note(
            id = payload.id,
            title = payload.title,
            category = payload.category,
            content = payload.content,
            createdAt = payload.createdAt,
            updatedAt = payload.updatedAt,
            encrypted = payload.encrypted,
        )

    fun checklistPayloadToChecklist(payload: ChecklistSyncPayload): Checklist {
        val items =
            runCatching { gson.fromJson<List<ChecklistItem>>(payload.itemsJson, itemListType) }
                .getOrDefault(emptyList())
                .normalizedForLocal()
        return Checklist(
            id = payload.id,
            title = payload.title,
            category = payload.category,
            type = payload.type,
            items = items,
            createdAt = payload.createdAt,
            updatedAt = payload.updatedAt,
        )
    }

    fun noteDiffText(payload: NoteSyncPayload): String =
        buildString {
            appendLine("Title: ${payload.title}")
            appendLine("Category: ${payload.category}")
            appendLine()
            append(payload.content)
        }

    fun checklistDiffText(payload: ChecklistSyncPayload): String =
        exportChecklistAsPlainText(checklistPayloadToChecklist(payload))

    fun noteEntityToPendingItem(entity: NoteEntity): PendingSyncItem =
        PendingSyncItem(
            id = entity.id,
            kind = SyncBackupKind.NOTE,
            title = entity.title.ifBlank { "(untitled)" },
            category = entity.category,
            updatedAt = entity.updatedAt,
            dirtySinceEpochMs = entity.dirtySinceEpochMs,
            isLocalOnly = entity.isLocalOnly,
            isPendingDelete = entity.isDeleted,
            pendingOpCount = 0,
            syncBaselineJson = entity.syncBaselineJson,
        )

    fun checklistEntityToPendingItem(entity: ChecklistEntity): PendingSyncItem =
        PendingSyncItem(
            id = entity.id,
            kind = SyncBackupKind.CHECKLIST,
            title = entity.title.ifBlank { "(untitled)" },
            category = entity.category,
            updatedAt = entity.updatedAt,
            dirtySinceEpochMs = entity.dirtySinceEpochMs,
            isLocalOnly = entity.isLocalOnly,
            isPendingDelete = entity.isDeleted,
            pendingOpCount = entity.pendingOps().size,
            syncBaselineJson = entity.syncBaselineJson,
        )
}

/**
 * When first becoming dirty, capture the current (pre-mutation) entity as the sync baseline.
 * Subsequent dirty writes keep the original baseline and [dirtySinceEpochMs].
 */
fun NoteEntity.withFirstDirtyBaseline(mutated: NoteEntity): NoteEntity {
    val baseline = syncBaselineJson ?: SyncPayloadCodec.encodeNote(this)
    val since = dirtySinceEpochMs ?: System.currentTimeMillis()
    return mutated.copy(
        isDirty = true,
        syncBaselineJson = baseline,
        dirtySinceEpochMs = since,
    )
}

fun ChecklistEntity.withFirstDirtyBaseline(mutated: ChecklistEntity): ChecklistEntity {
    val baseline = syncBaselineJson ?: SyncPayloadCodec.encodeChecklist(this)
    val since = dirtySinceEpochMs ?: System.currentTimeMillis()
    return mutated.copy(
        isDirty = true,
        syncBaselineJson = baseline,
        dirtySinceEpochMs = since,
    )
}
