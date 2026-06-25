package com.jotty.android.util

import com.jotty.android.data.api.AddItemRequest
import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.JottyApi

/** Maps checklist [type] to the value accepted by `POST /api/checklists`. */
fun apiChecklistTypeFrom(checklistType: String): String =
    if (checklistType.equals("task", ignoreCase = true) ||
        checklistType.equals("project", ignoreCase = true)
    ) {
        "task"
    } else {
        "simple"
    }

/**
 * Re-adds [items] depth-first under [parentPath], preserving order and completion state.
 * Used when cloning checklists and when restoring a checklist after delete-undo.
 */
suspend fun replayChecklistItemsToServer(
    api: JottyApi,
    listId: String,
    items: List<ChecklistItem>,
    parentPath: String? = null,
) {
    items.forEachIndexed { index, item ->
        val path = if (parentPath == null) "$index" else "$parentPath.$index"
        api.addChecklistItem(
            listId,
            AddItemRequest(text = item.text, status = item.status, parentIndex = parentPath),
        )
        val children = item.children.orEmpty()
        if (children.isNotEmpty()) {
            replayChecklistItemsToServer(api, listId, children, path)
        }
        if (item.completed) {
            api.checkItem(listId, path)
        }
    }
}
