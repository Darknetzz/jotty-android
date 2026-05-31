package com.jotty.android.util

import com.jotty.android.data.api.AddItemRequest
import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.UpdateItemRequest
import retrofit2.HttpException

/**
 * Updates checklist item text via PATCH when the server supports it (Jotty develop / next release).
 * Falls back to delete-and-recreate for **leaf** items on older stable Jotty servers.
 */
suspend fun updateChecklistItemText(
    api: JottyApi,
    listId: String,
    path: String,
    text: String,
    items: List<ChecklistItem>,
) {
    runCatching {
        api.updateItem(listId, path, UpdateItemRequest(text = text))
    }.getOrElse { error ->
        if (error is HttpException && error.code() in UNSUPPORTED_PATCH_CODES) {
            legacyRenameLeafItem(api, listId, path, text, items)
        } else {
            throw error
        }
    }
}

private suspend fun legacyRenameLeafItem(
    api: JottyApi,
    listId: String,
    path: String,
    text: String,
    items: List<ChecklistItem>,
) {
    val item =
        itemAtPath(items, path)
            ?: throw IllegalArgumentException("Checklist item not found: $path")
    if (item.children.orEmpty().isNotEmpty()) {
        throw UnsupportedOperationException("Parent item rename requires Jotty develop or newer")
    }
    val parentIndex = parentPath(path)
    val deletedItems = deleteAtPath(items, path)
    val newPath = appendedPath(deletedItems, parentIndex)
    api.deleteItem(listId, path)
    api.addChecklistItem(
        listId,
        AddItemRequest(text = text, status = item.status, parentIndex = parentIndex),
    )
    if (item.completed) api.checkItem(listId, newPath)
}

private val UNSUPPORTED_PATCH_CODES = setOf(404, 405)
