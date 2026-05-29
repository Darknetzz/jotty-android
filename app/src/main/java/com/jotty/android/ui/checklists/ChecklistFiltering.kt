package com.jotty.android.ui.checklists

import com.jotty.android.data.api.Checklist

/**
 * Filters checklists by a free-text [query] (title or category) and an optional [category].
 * Both filters are applied together so users can search within a selected category.
 */
fun filterChecklists(
    lists: List<Checklist>,
    query: String,
    category: String?,
): List<Checklist> =
    lists.filter { list ->
        val matchesQuery =
            query.isBlank() ||
                list.title.contains(query, ignoreCase = true) ||
                list.category.contains(query, ignoreCase = true)
        val matchesCategory = category == null || list.category == category
        matchesQuery && matchesCategory
    }
