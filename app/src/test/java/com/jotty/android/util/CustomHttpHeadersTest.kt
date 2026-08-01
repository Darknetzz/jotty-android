package com.jotty.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomHttpHeadersTest {
    @Test
    fun normalize_trimsNamesAndDropsBlank() {
        val result =
            CustomHttpHeaders.normalize(
                listOf(
                    "  Authorization  " to "Bearer x",
                    "" to "ignored",
                    "X-Custom" to "a",
                    "X-Custom" to "b",
                ),
            )
        assertEquals(mapOf("Authorization" to "Bearer x", "X-Custom" to "b"), result)
    }

    @Test
    fun isValid_rejectsControlCharactersInName() {
        assertTrue(CustomHttpHeaders.isValid("Authorization", "Bearer token"))
        assertFalse(CustomHttpHeaders.isValid("Bad\nName", "value"))
        assertFalse(CustomHttpHeaders.isValid("Bad Name", "value"))
    }

    @Test
    fun firstInvalid_returnsFirstBadEntry() {
        assertNull(CustomHttpHeaders.firstInvalid(mapOf("X-Ok" to "1")))
        val bad = CustomHttpHeaders.firstInvalid(mapOf("X-Ok" to "1", "Bad Name" to "2"))
        assertEquals("Bad Name", bad?.first)
    }

    @Test
    fun applyTo_skipsInvalidHeaders() {
        val request =
            okhttp3.Request.Builder()
                .url("https://example.com/")
                .also {
                    CustomHttpHeaders.applyTo(
                        it,
                        mapOf(
                            "X-Ok" to "1",
                            "Bad Name" to "2",
                            "X-Also" to "3",
                        ),
                    )
                }
                .build()
        assertEquals("1", request.header("X-Ok"))
        assertEquals("3", request.header("X-Also"))
        assertNull(request.header("Bad Name"))
    }
}
