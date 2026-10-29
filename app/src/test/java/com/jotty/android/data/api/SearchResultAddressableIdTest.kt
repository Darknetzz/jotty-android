package com.jotty.android.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResultAddressableIdTest {
    @Test
    fun prefersUuidWhenPresent() {
        val hit =
            SearchResult(
                id = "filename-slug",
                uuid = "f47ac10b-58cc-4372-a567-0e02b2c3d479",
                type = "note",
                title = "Hello",
            )
        assertEquals("f47ac10b-58cc-4372-a567-0e02b2c3d479", hit.addressableId())
    }

    @Test
    fun fallsBackToIdWhenUuidMissing() {
        val hit =
            SearchResult(
                id = "legacy-uuid-or-slug",
                uuid = null,
                type = "checklist",
                title = "List",
            )
        assertEquals("legacy-uuid-or-slug", hit.addressableId())
    }

    @Test
    fun fallsBackToIdWhenUuidBlank() {
        val hit =
            SearchResult(
                id = "slug",
                uuid = "  ",
                type = "note",
                title = "Hello",
            )
        assertEquals("slug", hit.addressableId())
    }
}
