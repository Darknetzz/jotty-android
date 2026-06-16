package com.jotty.android.ui.checklists

import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.UpdateTaskItemStatusRequest
import com.jotty.android.data.local.OfflineChecklistsRepository
import com.jotty.android.data.local.itemAtPath
import com.jotty.android.util.ServerCapabilities
import com.jotty.android.util.buildKanbanRichFieldsPatch
import com.jotty.android.util.updateChecklistItemDescription
import com.jotty.android.util.updateChecklistItemRichFields
import com.jotty.android.util.updateChecklistItemText

/** Builds a nested item API path (e.g. parent `"0"` + child index `1` → `"0.1"`). */
fun kanbanChildPath(parentPath: String, childIndex: Int): String = "$parentPath.$childIndex"

/**
 * Kanban card detail mutations — online API or offline checklist repository.
 */
interface KanbanItemActions {
    suspend fun currentItem(): ChecklistItem?

    suspend fun updateTitle(text: String): Result<Unit>

    suspend fun updateDescription(description: String): Result<Unit>

    suspend fun updateTaskDetails(
        description: String,
        priority: String?,
        score: Double?,
        startDate: String?,
        targetDate: String?,
        estimatedTime: Double?,
    ): Result<Unit>

    suspend fun moveToStatus(statusId: String): Result<Unit>

    suspend fun deleteItem(): Result<Unit>

    suspend fun addSubtask(text: String): Result<Unit>

    suspend fun updateSubtaskText(subPath: String, text: String): Result<Unit>

    suspend fun checkSubtask(subPath: String): Result<Unit>

    suspend fun uncheckSubtask(subPath: String): Result<Unit>

    suspend fun deleteSubtask(subPath: String): Result<Unit>
}

class ApiKanbanItemActions(
    private val api: JottyApi,
    private val checklistId: String,
    private val itemPath: String,
    private val items: () -> List<ChecklistItem>,
    private val onRefresh: suspend () -> Unit,
    private val onPatchUnavailable: (() -> Unit)? = null,
    private val performMoveToStatus: suspend (String) -> Result<Unit>,
    private val richFieldsSupported: Boolean = false,
) : KanbanItemActions {
    override suspend fun currentItem(): ChecklistItem? {
        if (richFieldsSupported) {
            runCatching { api.getTaskItem(checklistId, itemPath).item }
                .getOrNull()
                ?.let { return it }
        }
        return itemAtPath(items(), itemPath)
    }

    override suspend fun updateTitle(text: String): Result<Unit> =
        runCatching {
            updateChecklistItemText(
                api = api,
                listId = checklistId,
                path = itemPath,
                text = text.trim(),
                items = items(),
                onPatchUnavailable = onPatchUnavailable,
            )
            onRefresh()
        }

    override suspend fun updateDescription(description: String): Result<Unit> =
        runCatching {
            updateChecklistItemDescription(
                api = api,
                listId = checklistId,
                path = itemPath,
                description = description,
            )
            onRefresh()
        }

    override suspend fun updateTaskDetails(
        description: String,
        priority: String?,
        score: Double?,
        startDate: String?,
        targetDate: String?,
        estimatedTime: Double?,
    ): Result<Unit> =
        runCatching {
            val original =
                itemAtPath(items(), itemPath)
                    ?: throw IllegalArgumentException("Checklist item not found: $itemPath")
            val patch =
                buildKanbanRichFieldsPatch(
                    original = original,
                    description = description,
                    priority = priority,
                    score = score,
                    startDate = startDate,
                    targetDate = targetDate,
                    estimatedTime = estimatedTime,
                ) ?: return@runCatching
            updateChecklistItemRichFields(
                api = api,
                listId = checklistId,
                path = itemPath,
                patch = patch,
                onPatchUnavailable = onPatchUnavailable,
            )
            onRefresh()
        }

    override suspend fun moveToStatus(statusId: String): Result<Unit> = performMoveToStatus(statusId)

    override suspend fun deleteItem(): Result<Unit> =
        runCatching {
            api.deleteItem(checklistId, itemPath)
            onRefresh()
        }

    override suspend fun addSubtask(text: String): Result<Unit> =
        runCatching {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return@runCatching
            api.addChecklistItem(
                checklistId,
                com.jotty.android.data.api.AddItemRequest(text = trimmed, parentIndex = itemPath),
            )
            onRefresh()
        }

    override suspend fun updateSubtaskText(subPath: String, text: String): Result<Unit> =
        runCatching {
            updateChecklistItemText(
                api = api,
                listId = checklistId,
                path = subPath,
                text = text.trim(),
                items = items(),
                onPatchUnavailable = onPatchUnavailable,
            )
            onRefresh()
        }

    override suspend fun checkSubtask(subPath: String): Result<Unit> =
        runCatching {
            api.checkItem(checklistId, subPath)
            onRefresh()
        }

    override suspend fun uncheckSubtask(subPath: String): Result<Unit> =
        runCatching {
            api.uncheckItem(checklistId, subPath)
            onRefresh()
        }

    override suspend fun deleteSubtask(subPath: String): Result<Unit> =
        runCatching {
            api.deleteItem(checklistId, subPath)
            onRefresh()
        }
}

class OfflineKanbanItemActions(
    private val offlineRepository: OfflineChecklistsRepository,
    private val checklistId: String,
    private val itemPath: String,
    private val items: () -> List<ChecklistItem>,
    private val onChecklistUpdated: (Checklist) -> Unit,
    private val moveToStatusOnline: (suspend (String) -> Result<Unit>)? = null,
) : KanbanItemActions {
    private suspend fun applyChecklistResult(result: Result<Checklist>): Result<Unit> =
        result.map {
            onChecklistUpdated(it)
            Unit
        }

    override suspend fun currentItem(): ChecklistItem? = itemAtPath(items(), itemPath)

    override suspend fun updateTitle(text: String): Result<Unit> =
        applyChecklistResult(offlineRepository.updateItemText(checklistId, itemPath, text))

    override suspend fun updateDescription(description: String): Result<Unit> =
        applyChecklistResult(offlineRepository.updateItemDescription(checklistId, itemPath, description))

    override suspend fun updateTaskDetails(
        description: String,
        priority: String?,
        score: Double?,
        startDate: String?,
        targetDate: String?,
        estimatedTime: Double?,
    ): Result<Unit> {
        val original = itemAtPath(items(), itemPath) ?: return Result.failure(IllegalArgumentException("Item not found"))
        val patch =
            buildKanbanRichFieldsPatch(
                original = original,
                description = description,
                priority = priority,
                score = score,
                startDate = startDate,
                targetDate = targetDate,
                estimatedTime = estimatedTime,
            ) ?: return Result.success(Unit)
        return applyChecklistResult(offlineRepository.updateItemRichFields(checklistId, itemPath, patch))
    }

    override suspend fun moveToStatus(statusId: String): Result<Unit> {
        val mover = moveToStatusOnline ?: return Result.failure(Exception("offline"))
        return mover(statusId)
    }

    override suspend fun deleteItem(): Result<Unit> =
        applyChecklistResult(offlineRepository.deleteItem(checklistId, itemPath))

    override suspend fun addSubtask(text: String): Result<Unit> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Result.success(Unit)
        return applyChecklistResult(offlineRepository.addItem(checklistId, trimmed, parentIndex = itemPath))
    }

    override suspend fun updateSubtaskText(subPath: String, text: String): Result<Unit> =
        applyChecklistResult(offlineRepository.updateItemText(checklistId, subPath, text))

    override suspend fun checkSubtask(subPath: String): Result<Unit> =
        applyChecklistResult(offlineRepository.checkItem(checklistId, subPath))

    override suspend fun uncheckSubtask(subPath: String): Result<Unit> =
        applyChecklistResult(offlineRepository.uncheckItem(checklistId, subPath))

    override suspend fun deleteSubtask(subPath: String): Result<Unit> =
        applyChecklistResult(offlineRepository.deleteItem(checklistId, subPath))
}

fun apiKanbanItemActions(
    api: JottyApi,
    checklistId: String,
    itemPath: String,
    items: () -> List<ChecklistItem>,
    onRefresh: suspend () -> Unit,
    serverCapabilitiesKey: String?,
    performMoveToStatus: suspend (String) -> Result<Unit>,
    richFieldsSupported: Boolean = false,
): KanbanItemActions =
    ApiKanbanItemActions(
        api = api,
        checklistId = checklistId,
        itemPath = itemPath,
        items = items,
        onRefresh = onRefresh,
        onPatchUnavailable =
            serverCapabilitiesKey?.let { key ->
                { ServerCapabilities.markItemPatchLimited(key) }
            },
        performMoveToStatus = performMoveToStatus,
        richFieldsSupported = richFieldsSupported,
    )
