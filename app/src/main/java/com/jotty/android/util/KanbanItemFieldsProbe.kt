package com.jotty.android.util

import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.hasAnyRichField
import retrofit2.HttpException
import java.util.concurrent.ConcurrentHashMap

/** Cached per-instance result of probing expanded Kanban item REST fields. */
object KanbanItemFieldsProbe {
    private val supportedKeys = ConcurrentHashMap.newKeySet<String>()
    private val unsupportedKeys = ConcurrentHashMap.newKeySet<String>()

    /** Marks support when list GET already returned expanded item fields. */
    fun markSupportedFromItems(
        capabilitiesKey: String,
        items: List<ChecklistItem>,
    ) {
        if (capabilitiesKey.isBlank()) return
        if (items.any { it.hasAnyRichField() }) {
            supportedKeys.add(capabilitiesKey)
            unsupportedKeys.remove(capabilitiesKey)
        }
    }

    suspend fun probeKanbanItemFields(
        api: JottyApi,
        taskId: String,
        itemPath: String,
        capabilitiesKey: String,
    ): Boolean {
        if (capabilitiesKey.isBlank()) return false
        if (capabilitiesKey in supportedKeys) return true
        if (capabilitiesKey in unsupportedKeys) return false
        return try {
            api.getTaskItem(taskId, itemPath)
            supportedKeys.add(capabilitiesKey)
            true
        } catch (e: HttpException) {
            if (e.code() == 404 || e.code() == 405) {
                unsupportedKeys.add(capabilitiesKey)
                false
            } else {
                // Route exists (400/403/etc.) even if the probe path is invalid.
                supportedKeys.add(capabilitiesKey)
                true
            }
        } catch (_: Exception) {
            unsupportedKeys.add(capabilitiesKey)
            false
        }
    }

    fun isKanbanItemRichFieldsSupported(capabilitiesKey: String): Boolean =
        capabilitiesKey.isNotBlank() && capabilitiesKey in supportedKeys

    internal fun resetForTests() {
        supportedKeys.clear()
        unsupportedKeys.clear()
    }
}
