package com.jotty.android.util

import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.isCompletedForApi
import com.jotty.android.data.local.PendingItemOp

/** Bulk actions on the To Do / Completed sections of a simple checklist (mirrors the Jotty web app). */
enum class ChecklistBulkAction {
    /** To Do: tick every open item. */
    CheckAll,

    /** To Do: delete every open top-level item. */
    ClearToDo,

    /** Completed: untick every ticked item. */
    UncheckAll,

    /** Completed: delete every ticked top-level item. */
    ClearCompleted,
    ;

    val isDestructive: Boolean get() = this == ClearToDo || this == ClearCompleted
}

/**
 * Per-item ops that carry out [action] on [items], in an order whose paths stay valid while they
 * are applied one after another.
 *
 * - Check / uncheck: every item at any depth whose state differs, parents before children (as the
 *   web app's bulk toggle). The server cascades a parent's state to its children, so the child ops
 *   are no-ops there but keep the local copy right.
 * - Clear: top-level items only, matched on their own completed state (as the web app's
 *   "Clear all"), highest index first so earlier paths do not shift.
 */
fun checklistBulkOps(
    items: List<ChecklistItem>,
    action: ChecklistBulkAction,
): List<PendingItemOp> =
    when (action) {
        ChecklistBulkAction.CheckAll ->
            togglePaths(items, completed = false).map { PendingItemOp(type = "CHECK", path = it) }
        ChecklistBulkAction.UncheckAll ->
            togglePaths(items, completed = true).map { PendingItemOp(type = "UNCHECK", path = it) }
        ChecklistBulkAction.ClearToDo -> clearPaths(items, completed = false)
        ChecklistBulkAction.ClearCompleted -> clearPaths(items, completed = true)
    }

private fun togglePaths(
    items: List<ChecklistItem>,
    completed: Boolean,
    parentPath: String = "",
): List<String> =
    items.flatMapIndexed { index, item ->
        val path = if (parentPath.isEmpty()) "$index" else "$parentPath.$index"
        val self = if (item.isCompletedForApi() == completed) listOf(path) else emptyList()
        self + togglePaths(item.children.orEmpty(), completed, path)
    }

private fun clearPaths(
    items: List<ChecklistItem>,
    completed: Boolean,
): List<PendingItemOp> =
    items.indices
        .filter { items[it].isCompletedForApi() == completed }
        .reversed()
        .map { PendingItemOp(type = "DELETE", path = "$it") }

/** Sends one CHECK / UNCHECK / DELETE op produced by [checklistBulkOps] to the server. */
suspend fun JottyApi.sendBulkOp(
    listId: String,
    op: PendingItemOp,
) {
    val path = requireNotNull(op.path) { "Bulk op without path" }
    when (op.type) {
        "CHECK" -> checkItem(listId, path)
        "UNCHECK" -> uncheckItem(listId, path)
        "DELETE" -> deleteItem(listId, path)
        else -> throw IllegalArgumentException("Unsupported bulk op ${op.type}")
    }
}
