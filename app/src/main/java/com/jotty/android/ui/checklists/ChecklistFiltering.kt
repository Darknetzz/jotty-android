package com.jotty.android.ui.checklists

import com.jotty.android.data.api.Checklist
import com.jotty.android.util.filterChecklistsForCategory

/**
 * Filters checklists by a free-text [query] (title or category) and an optional [category].
 * Both filters are applied together so users can search within a selected category.
 * When [category] is null, archived checklists are hidden; use [com.jotty.android.util.JOTTY_ARCHIVE_CATEGORY] to show them.
 */
fun filterChecklists(
    lists: List<Checklist>,
    query: String,
    category: String?,
): List<Checklist> =
    filterChecklistsForCategory(lists, category).filter { list ->
        query.isBlank() ||
            list.title.contains(query, ignoreCase = true) ||
            list.category.contains(query, ignoreCase = true)
    }
