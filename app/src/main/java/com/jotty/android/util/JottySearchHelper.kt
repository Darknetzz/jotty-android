package com.jotty.android.util

import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import com.jotty.android.data.api.normalizedForClient
import retrofit2.HttpException

private const val MIN_SEARCH_LENGTH = 2

/**
 * Loads notes using the unified search API when available (query ≥ 2 chars), hydrating full note
 * bodies from [JottyApi.getNotes]. Falls back to [JottyApi.getNotes] with `q` on older servers.
 */
suspend fun loadNotesWithSearch(
    api: JottyApi,
    query: String,
    category: String?,
): List<Note> {
    val trimmed = query.trim()
    val effectiveApiCategory =
        when {
            category == null -> null
            category.equals(JOTTY_ARCHIVE_CATEGORY, ignoreCase = true) -> JOTTY_ARCHIVE_CATEGORY
            else -> category
        }
    if (trimmed.length < MIN_SEARCH_LENGTH) {
        return filterNotesForCategory(
            api.getNotes(category = effectiveApiCategory, search = trimmed.takeIf { it.isNotBlank() })
                .notes.orEmpty()
                .map { it.normalizedForClient() },
            category,
        )
    }

    val rankedIds =
        runCatching {
            api.search(query = trimmed, type = "note").results.map { it.id }
        }.getOrElse { error ->
            if (error is HttpException && error.code() == 404) {
                return filterNotesForCategory(
                    api.getNotes(category = effectiveApiCategory, search = trimmed)
                        .notes.orEmpty()
                        .map { it.normalizedForClient() },
                    category,
                )
            }
            throw error
        }

    if (rankedIds.isEmpty()) return emptyList()

    val notesById =
        api.getNotes(category = effectiveApiCategory)
            .notes.orEmpty()
            .map { it.normalizedForClient() }
            .associateBy { it.id }

    return filterNotesForCategory(rankedIds.mapNotNull { notesById[it] }, category)
}

/**
 * Loads checklists using the unified search API when available (query ≥ 2 chars), hydrating full
 * checklist data from [JottyApi.getChecklists]. Falls back to list filtering on older servers.
 */
suspend fun loadChecklistsWithSearch(
    api: JottyApi,
    query: String,
    category: String?,
): List<Checklist> {
    val trimmed = query.trim()
    val effectiveApiCategory =
        when {
            category == null -> null
            category.equals(JOTTY_ARCHIVE_CATEGORY, ignoreCase = true) -> JOTTY_ARCHIVE_CATEGORY
            else -> category
        }
    val allChecklists = api.getChecklists(category = effectiveApiCategory).checklists

    if (trimmed.length < MIN_SEARCH_LENGTH) {
        return filterChecklistsForCategory(allChecklists, category)
    }

    val rankedIds =
        runCatching {
            api.search(query = trimmed, type = "checklist").results.map { it.id }
        }.getOrElse { error ->
            if (error is HttpException && error.code() == 404) {
                return allChecklists.filter { list ->
                    list.title.contains(trimmed, ignoreCase = true) ||
                        list.items.any { itemMatchesQuery(it, trimmed) }
                }
            }
            throw error
        }

    if (rankedIds.isEmpty()) return emptyList()

    val listsById = allChecklists.associateBy { it.id }
    return filterChecklistsForCategory(rankedIds.mapNotNull { listsById[it] }, category)
}

private fun itemMatchesQuery(
    item: com.jotty.android.data.api.ChecklistItem,
    query: String,
): Boolean {
    if (item.text.contains(query, ignoreCase = true)) return true
    return item.children.orEmpty().any { itemMatchesQuery(it, query) }
}
