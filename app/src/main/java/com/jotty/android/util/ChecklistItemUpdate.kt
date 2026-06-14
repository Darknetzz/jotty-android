package com.jotty.android.util

import com.jotty.android.data.api.AddItemRequest
import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.UpdateItemRequest
import com.google.gson.GsonBuilder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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
    onPatchUnavailable: (() -> Unit)? = null,
) {
    runCatching {
        api.updateItem(listId, path, UpdateItemRequest(text = text))
    }.getOrElse { error ->
        if (error is HttpException && error.code() in UNSUPPORTED_PATCH_CODES) {
            onPatchUnavailable?.invoke()
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

/**
 * Updates checklist item description via PATCH. Description is not returned on GET by most servers yet;
 * callers should keep a local draft when needed.
 */
suspend fun updateChecklistItemDescription(
    api: JottyApi,
    listId: String,
    path: String,
    description: String,
) {
    api.updateItem(listId, path, UpdateItemRequest(description = description))
}

/** Builds a PATCH map containing only fields that differ from [original]. Null values clear optional fields. */
fun buildKanbanRichFieldsPatch(
    original: ChecklistItem,
    description: String,
    priority: String?,
    score: Double?,
    startDate: String?,
    targetDate: String?,
    estimatedTime: Double?,
): Map<String, Any?>? {
    val descTrimmed = description.trim()
    val origDesc = original.description.orEmpty().trim()
    val descriptionChanged = descTrimmed != origDesc

    val priorityNorm = priority?.takeIf { it.isNotBlank() }
    val priorityChanged = priorityNorm != original.priority?.takeIf { it.isNotBlank() }

    val scoreChanged = score != original.score
    val startChanged = normalizeOptionalDate(startDate) != normalizeOptionalDate(original.startDate)
    val targetChanged = normalizeOptionalDate(targetDate) != normalizeOptionalDate(original.targetDate)
    val estimatedChanged = estimatedTime != original.estimatedTime

    if (!descriptionChanged && !priorityChanged && !scoreChanged &&
        !startChanged && !targetChanged && !estimatedChanged
    ) {
        return null
    }

    val patch = linkedMapOf<String, Any?>()
    if (descriptionChanged) {
        patch["description"] = descTrimmed.takeIf { it.isNotEmpty() }
    }
    if (priorityChanged) {
        patch["priority"] = priorityNorm
    }
    if (scoreChanged) {
        patch["score"] = score
    }
    if (startChanged) {
        patch["startDate"] = normalizeOptionalDate(startDate)
    }
    if (targetChanged) {
        patch["targetDate"] = normalizeOptionalDate(targetDate)
    }
    if (estimatedChanged) {
        patch["estimatedTime"] = estimatedTime
    }
    return patch
}

/**
 * PATCH expanded Kanban item fields. [patch] must include only changed keys; null values clear fields upstream.
 */
suspend fun updateChecklistItemRichFields(
    api: JottyApi,
    listId: String,
    path: String,
    patch: Map<String, Any?>,
    onPatchUnavailable: (() -> Unit)? = null,
) {
    if (patch.isEmpty()) return
    runCatching {
        api.updateItemPatch(listId, path, patch.toPatchRequestBody())
    }.getOrElse { error ->
        if (error is HttpException && error.code() in UNSUPPORTED_PATCH_CODES) {
            onPatchUnavailable?.invoke()
        }
        throw error
    }
}

private fun Map<String, Any?>.toPatchRequestBody() =
    patchGson.toJson(this).toRequestBody("application/json".toMediaType())

private val patchGson = GsonBuilder().serializeNulls().create()

private fun normalizeOptionalDate(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

private val UNSUPPORTED_PATCH_CODES = setOf(404, 405)
