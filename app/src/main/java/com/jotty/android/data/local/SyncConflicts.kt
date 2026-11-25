package com.jotty.android.data.local

import com.google.gson.Gson
import com.jotty.android.data.api.Checklist
import java.util.UUID

/**
 * Conflict detection shared by [OfflineNotesRepository] and [OfflineChecklistsRepository].
 * See docs/OFFLINE_SYNC.md ("Conflicts", "Resolving conflicts").
 */

/** Title suffix of a conflict copy; the v8 → v9 migration matches it in SQL as well. */
const val LOCAL_COPY_SUFFIX = " (Local copy)"

/** The title without the [LOCAL_COPY_SUFFIX] a conflict copy carries. */
fun String.withoutLocalCopySuffix(): String = removeSuffix(LOCAL_COPY_SUFFIX)

/**
 * After a sync has pushed local changes and fetched [serverRows], returns a conflict copy for every
 * local row that had unsent edits ([hadLocalEdits], checked against [localBefore], the rows as they
 * were before the push) and that [hasConflict] with its server row.
 */
internal inline fun <L, S> conflictCopiesAfterSync(
    serverRows: List<S>,
    serverId: (S) -> String,
    localBefore: Map<String, L>,
    hadLocalEdits: (L) -> Boolean,
    hasConflict: (local: L, server: S) -> Boolean,
    toCopy: (L) -> L,
): List<L> =
    serverRows.mapNotNull { server ->
        localBefore[serverId(server)]
            ?.takeIf { hadLocalEdits(it) && hasConflict(it, server) }
            ?.let(toCopy)
    }

/** A note conflicts when its title, body or category did not end up on the server. */
internal fun noteHasConflict(
    local: NoteEntity,
    server: NoteEntity,
): Boolean =
    local.title != server.title ||
        local.content != server.content ||
        local.category != server.category

private val gson = Gson()

/**
 * [local] is the row as it was before the push; [server] is the server copy after it.
 *
 * Queued item ops were replayed onto the server's current items, so the server copy already holds
 * them merged with any remote edits: differing items are not a conflict then. A conflict is a title
 * or category that did not stick, an op the server could not take ([lostEdits]), or, for a row
 * without queued ops, items that differ.
 */
internal fun checklistHasConflict(
    local: ChecklistEntity,
    server: Checklist,
    lostEdits: Boolean,
): Boolean {
    if (lostEdits) return true
    if (local.title != server.title || local.category != server.category) return true
    if (local.pendingOps().isNotEmpty()) return false
    return gson.toJson(local.items()) != gson.toJson(server.items)
}

/** A "(Local copy)" of [this] (a dirty note the server changed too), kept until resolved. */
internal fun NoteEntity.toConflictCopy(): NoteEntity =
    copy(
        id = UUID.randomUUID().toString(),
        title = "$title$LOCAL_COPY_SUFFIX",
        isDirty = false,
        isDeleted = false,
        isLocalOnly = false,
        originalCategory = null,
        syncBaselineJson = null,
        dirtySinceEpochMs = null,
        isConflictCopy = true,
        conflictOfId = id,
    )

/** A "(Local copy)" of [this] (a dirty list the server changed too), kept until resolved. */
internal fun ChecklistEntity.toConflictCopy(): ChecklistEntity =
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
