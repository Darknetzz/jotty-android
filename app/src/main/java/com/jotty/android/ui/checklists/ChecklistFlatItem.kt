package com.jotty.android.ui.checklists

import com.jotty.android.data.api.ChecklistItem

/** Checklist row with display depth and API path (e.g. `"0"` or `"0.1"` for nested). */
data class ChecklistFlatItem(val item: ChecklistItem, val depth: Int, val apiPath: String)

/** Flatten checklist items with depth and API path for project/task type. */
fun flattenChecklistItems(
    items: List<ChecklistItem>,
    depth: Int = 0,
    parentPath: String = "",
): List<ChecklistFlatItem> =
    items.flatMapIndexed { index, item ->
        val path = if (parentPath.isEmpty()) "$index" else "$parentPath.$index"
        listOf(ChecklistFlatItem(item, depth, path)) +
            flattenChecklistItems(item.children.orEmpty(), depth + 1, path)
    }
