package com.jotty.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JottyPublicShareUrlTest {
    @Test
    fun buildsNoteAndChecklistUrls() {
        assertEquals(
            "https://jotty.example.com/note/abc",
            publicShareUrl("https://jotty.example.com", "note", "abc"),
        )
        assertEquals(
            "https://jotty.example.com/checklist/def",
            publicShareUrl("https://jotty.example.com/", "checklist", "def"),
        )
    }

    @Test
    fun rejectsBlankInputs() {
        assertNull(publicShareUrl(null, "note", "abc"))
        assertNull(publicShareUrl("https://jotty.example.com", "note", ""))
    }
}
