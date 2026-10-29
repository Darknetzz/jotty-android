package com.jotty.android.util

import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.ChecklistItemSortDirection
import com.jotty.android.data.api.ChecklistItemSortMode
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.SortItemsRequest
import retrofit2.HttpException

/**
 * Sorts a simple checklist via PUT /api/checklists/{id}/items/sort (Jotty 1.29+).
 * Returns the updated checklist, or throws [UnsupportedOperationException] when the
 * server lacks the route (404/405).
 */
suspend fun sortChecklistItemsOnline(
    api: JottyApi,
    listId: String,
    sort: String,
    direction: String = ChecklistItemSortDirection.ASC,
): Checklist {
    val mode = sort.takeIf { it in ChecklistItemSortMode.ALL } ?: ChecklistItemSortMode.ALPHABETICAL
    val dir =
        when {
            mode == ChecklistItemSortMode.SHUFFLE -> ChecklistItemSortDirection.ASC
            direction == ChecklistItemSortDirection.DESC -> ChecklistItemSortDirection.DESC
            else -> ChecklistItemSortDirection.ASC
        }
    return try {
        val response =
            api.sortChecklistItems(
                listId,
                SortItemsRequest(sort = mode, direction = dir),
            )
        if (!response.success) {
            throw IllegalStateException("sort failed")
        }
        response.data
    } catch (e: HttpException) {
        if (e.code() == 404 || e.code() == 405) {
            throw UnsupportedOperationException("checklist item sort not supported", e)
        }
        throw e
    }
}
