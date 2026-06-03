package com.jotty.android.util

import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.DEFAULT_TASK_STATUSES
import com.jotty.android.data.api.TaskStatus
import com.jotty.android.data.api.isCompletedForApi

/** Top-level row on the Kanban board (index + item). */
data class KanbanCard(val index: Int, val item: ChecklistItem)

/** One column with its cards, sorted by status order. */
data class KanbanColumn(val status: TaskStatus, val cards: List<KanbanCard>)

/**
 * Groups top-level [items] into Kanban columns using each item's [ChecklistItem.status].
 * Unknown statuses are placed in the first column.
 */
fun buildKanbanColumns(
    items: List<ChecklistItem>,
    statuses: List<TaskStatus>,
): List<KanbanColumn> {
    val columns = statuses.sortedBy { it.order }.ifEmpty { DEFAULT_TASK_STATUSES }
    val statusIds = columns.map { it.id.lowercase() }.toSet()
    val defaultStatusId = columns.firstOrNull()?.id ?: DEFAULT_TASK_STATUSES.first().id

    fun resolveStatusId(item: ChecklistItem): String {
        val raw = item.status?.takeIf { it.isNotBlank() }
        if (raw != null && raw.lowercase() in statusIds) return raw
        if (item.isCompletedForApi()) {
            return columns.firstOrNull { it.id.equals("completed", ignoreCase = true) }?.id
                ?: columns.lastOrNull()?.id
                ?: defaultStatusId
        }
        return raw?.takeIf { it.isNotBlank() } ?: defaultStatusId
    }

    val grouped = LinkedHashMap<String, MutableList<KanbanCard>>()
    columns.forEach { grouped[it.id] = mutableListOf() }

    items.forEachIndexed { index, item ->
        val statusId = resolveStatusId(item)
        val bucket =
            grouped.keys.firstOrNull { it.equals(statusId, ignoreCase = true) }
                ?: defaultStatusId
        grouped.getOrPut(bucket) { mutableListOf() }.add(KanbanCard(index = index, item = item))
    }

    return columns.map { status ->
        val cards = grouped[status.id].orEmpty()
        KanbanColumn(status = status, cards = cards)
    }
}

/** When [hideEmpty] is true, drops columns with no cards. */
fun List<KanbanColumn>.visibleKanbanColumns(hideEmpty: Boolean): List<KanbanColumn> =
    if (hideEmpty) filter { it.cards.isNotEmpty() } else this
