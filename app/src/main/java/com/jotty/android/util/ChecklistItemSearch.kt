package com.jotty.android.util

import com.jotty.android.data.api.isCompletedForApi
import com.jotty.android.ui.checklists.ChecklistFlatItem

enum class ChecklistAddItemAction {
    AddNew,
    UncheckExisting,
    AlreadyExists,
}

fun findExactChecklistItemMatch(
    items: List<ChecklistFlatItem>,
    query: String,
): ChecklistFlatItem? {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return null
    return items.firstOrNull { it.item.text.trim().equals(trimmed, ignoreCase = true) }
}

fun filterChecklistItemsByQuery(
    items: List<ChecklistFlatItem>,
    query: String,
    maxResults: Int = 5,
): List<ChecklistFlatItem> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()
    return items
        .filter { it.item.text.trim().contains(trimmed, ignoreCase = true) }
        .sortedWith(
            compareByDescending<ChecklistFlatItem> {
                it.item.text.trim().equals(trimmed, ignoreCase = true)
            }
                .thenByDescending { it.item.isCompletedForApi() }
                .thenBy { it.item.text.lowercase() },
        )
        .take(maxResults)
}

fun resolveChecklistAddItemAction(
    items: List<ChecklistFlatItem>,
    query: String,
): Pair<ChecklistAddItemAction, ChecklistFlatItem?> {
    val match = findExactChecklistItemMatch(items, query) ?: return ChecklistAddItemAction.AddNew to null
    return if (match.item.isCompletedForApi()) {
        ChecklistAddItemAction.UncheckExisting to match
    } else {
        ChecklistAddItemAction.AlreadyExists to match
    }
}
