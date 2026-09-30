package com.jotty.android.util

import com.jotty.android.data.api.SearchResponse
import com.jotty.android.data.api.SearchResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JottySearchFallbackTest {
    @Test
    fun fallsBackWhenIndexingAndNoHits() {
        val response =
            SearchResponse(
                query = "ab",
                results = emptyList(),
                indexing = true,
            )
        assertTrue(shouldFallBackFromSearch(response, rankedIdsEmpty = true))
    }

    @Test
    fun doesNotFallBackWhenIndexingButHasHits() {
        val response =
            SearchResponse(
                query = "ab",
                results =
                    listOf(
                        SearchResult(id = "slug", uuid = "uuid-1", type = "note", title = "A"),
                    ),
                indexing = true,
            )
        assertFalse(shouldFallBackFromSearch(response, rankedIdsEmpty = false))
    }

    @Test
    fun doesNotFallBackWhenEmptyAndNotIndexing() {
        val response =
            SearchResponse(
                query = "zz",
                results = emptyList(),
                indexing = false,
            )
        assertFalse(shouldFallBackFromSearch(response, rankedIdsEmpty = true))
    }

    @Test
    fun doesNotFallBackWhenIndexingNull() {
        val response =
            SearchResponse(
                query = "ab",
                results = emptyList(),
                indexing = null,
            )
        assertFalse(shouldFallBackFromSearch(response, rankedIdsEmpty = true))
    }
}
