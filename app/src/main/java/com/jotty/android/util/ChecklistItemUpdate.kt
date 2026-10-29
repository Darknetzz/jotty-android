package com.jotty.android.util

import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.UpdateItemRequest
import com.google.gson.GsonBuilder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Updates checklist item text in place via `PATCH /api/checklists/{listId}/items/{path}` (Jotty 1.25+).
 * [path] may be nested (e.g. `0.1`); position, completion state, and children are preserved.
 */
suspend fun updateChecklistItemText(
    api: JottyApi,
    listId: String,
    path: String,
    text: String,
) {
    api.updateItem(listId, path, UpdateItemRequest(text = text))
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
) {
    if (patch.isEmpty()) return
    api.updateItemPatch(listId, path, patch.toPatchRequestBody())
}

private fun Map<String, Any?>.toPatchRequestBody() =
    patchGson.toJson(this).toRequestBody("application/json".toMediaType())

private val patchGson = GsonBuilder().serializeNulls().create()

private fun normalizeOptionalDate(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
