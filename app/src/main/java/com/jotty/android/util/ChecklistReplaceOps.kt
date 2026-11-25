package com.jotty.android.util

import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.local.PendingItemOp
import com.jotty.android.data.local.toPendingItemOp

/**
 * Positional ops that turn a list holding [current] items into one holding [target]: delete every
 * top-level item (highest index first, as for bulk clears), then re-add [target] depth-first with
 * its fields and completion state. Used to "keep mine" on a checklist conflict, since the API has no
 * endpoint that replaces a list's items. Ops must be queued in this order and never deduplicated.
 */
fun buildReplaceItemsOps(
    current: List<ChecklistItem>,
    target: List<ChecklistItem>,
): List<PendingItemOp> =
    buildList {
        for (index in current.indices.reversed()) add(PendingItemOp(type = "DELETE", path = "$index"))
        addItemOps(target, parentPath = null)
    }

private fun MutableList<PendingItemOp>.addItemOps(
    items: List<ChecklistItem>,
    parentPath: String?,
) {
    items.forEachIndexed { index, item ->
        val path = if (parentPath == null) "$index" else "$parentPath.$index"
        add(PendingItemOp(type = "ADD", text = item.text, parentIndex = parentPath, status = item.status))
        item.fieldsPatch(topLevel = parentPath == null)?.let { add(it.toPendingItemOp(path)) }
        addItemOps(item.children.orEmpty(), path)
        if (item.completed) add(PendingItemOp(type = "CHECK", path = path))
    }
}

/** The optional fields [ChecklistItem] carries beyond text and status, or null when it has none. */
private fun ChecklistItem.fieldsPatch(topLevel: Boolean): Map<String, Any?>? {
    val patch = linkedMapOf<String, Any?>()
    description?.takeIf { it.isNotBlank() }?.let { patch["description"] = it }
    priority?.takeIf { it.isNotBlank() }?.let { patch["priority"] = it }
    score?.let { patch["score"] = it }
    startDate?.takeIf { it.isNotBlank() }?.let { patch["startDate"] = it }
    targetDate?.takeIf { it.isNotBlank() }?.let { patch["targetDate"] = it }
    estimatedTime?.let { patch["estimatedTime"] = it }
    // Repeat rules are top-level only (Jotty 1.30+).
    if (topLevel) recurrence?.toInput()?.let { patch["recurrence"] = it }
    return patch.takeIf { it.isNotEmpty() }
}
