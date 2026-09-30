package com.jotty.android.util

import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import com.jotty.android.data.api.SearchResponse
import com.jotty.android.data.api.addressableId
import com.jotty.android.data.api.normalizedForClient
import retrofit2.HttpException

private const val MIN_SEARCH_LENGTH = 2

/**
 * Loads notes using the unified search API when available (query ≥ 2 chars), hydrating full note
 * bodies from [JottyApi.getNotes]. Falls back to [JottyApi.getNotes] with `q` on older servers,
 * while the search index is building, or when ranked ids cannot be hydrated.
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

    suspend fun listFallback(): List<Note> =
        filterNotesForCategory(
            api.getNotes(category = effectiveApiCategory, search = trimmed.takeIf { it.isNotBlank() })
                .notes.orEmpty()
                .map { it.normalizedForClient() },
            category,
        )

    if (trimmed.length < MIN_SEARCH_LENGTH) {
        return listFallback()
    }

    val searchResponse =
        runCatching {
            api.search(query = trimmed, type = "note")
        }.getOrElse { error ->
            if (error is HttpException && error.code() == 404) {
                return listFallback()
            }
            throw error
        }

    val rankedIds = searchResponse.results.map { it.addressableId() }.filter { it.isNotBlank() }
    if (shouldFallBackFromSearch(searchResponse, rankedIds.isEmpty())) {
        return listFallback()
    }
    if (rankedIds.isEmpty()) return emptyList()

    val notesById =
        api.getNotes(category = effectiveApiCategory)
            .notes.orEmpty()
            .map { it.normalizedForClient() }
            .associateBy { it.id }

    val hydrated = rankedIds.mapNotNull { notesById[it] }
    if (hydrated.isEmpty() && rankedIds.isNotEmpty()) {
        return listFallback()
    }
    return filterNotesForCategory(hydrated, category)
}

/**
 * Loads checklists using the unified search API when available (query ≥ 2 chars), hydrating full
 * checklist data from [JottyApi.getChecklists]. Falls back to list filtering on older servers,
 * while the search index is building, or when ranked ids cannot be hydrated.
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

    fun listFallback(): List<Checklist> =
        filterChecklistsForCategory(
            allChecklists.filter { list ->
                list.title.contains(trimmed, ignoreCase = true) ||
                    list.items.any { itemMatchesQuery(it, trimmed) }
            },
            category,
        )

    if (trimmed.length < MIN_SEARCH_LENGTH) {
        return filterChecklistsForCategory(allChecklists, category)
    }

    val searchResponse =
        runCatching {
            api.search(query = trimmed, type = "checklist")
        }.getOrElse { error ->
            if (error is HttpException && error.code() == 404) {
                return listFallback()
            }
            throw error
        }

    val rankedIds = searchResponse.results.map { it.addressableId() }.filter { it.isNotBlank() }
    if (shouldFallBackFromSearch(searchResponse, rankedIds.isEmpty())) {
        return listFallback()
    }
    if (rankedIds.isEmpty()) return emptyList()

    val listsById = allChecklists.associateBy { it.id }
    val hydrated = rankedIds.mapNotNull { listsById[it] }
    if (hydrated.isEmpty() && rankedIds.isNotEmpty()) {
        return listFallback()
    }
    return filterChecklistsForCategory(hydrated, category)
}

/**
 * True when ranked search should yield to list/q filtering: empty results while the server
 * reports [SearchResponse.indexing], or addressable ids that cannot hydrate against loaded items
 * (handled by callers when [rankedIdsEmpty] is false after a failed hydrate).
 */
internal fun shouldFallBackFromSearch(
    response: SearchResponse,
    rankedIdsEmpty: Boolean,
): Boolean = rankedIdsEmpty && response.indexing == true

private fun itemMatchesQuery(
    item: com.jotty.android.data.api.ChecklistItem,
    query: String,
): Boolean {
    if (item.text.contains(query, ignoreCase = true)) return true
    return item.children.orEmpty().any { itemMatchesQuery(it, query) }
}
