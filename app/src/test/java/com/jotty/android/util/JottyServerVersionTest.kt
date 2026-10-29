package com.jotty.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JottyServerVersionTest {
    @Test
    fun parsesSemver() {
        assertEquals(JottyServerVersion(1, 28), JottyServerVersion.parse("1.28.0"))
        assertEquals(JottyServerVersion(1, 27), JottyServerVersion.parse("v1.27.1-beta"))
        assertEquals(JottyServerVersion(2, 0), JottyServerVersion.parse("2.0"))
    }

    @Test
    fun parseRejectsGarbage() {
        assertNull(JottyServerVersion.parse(null))
        assertNull(JottyServerVersion.parse(""))
        assertNull(JottyServerVersion.parse("dev"))
    }

    @Test
    fun ceilingWarningOnlyAbove129() {
        assertFalse(JottyServerVersion.isNewerThanCompatCeiling("1.29.0"))
        assertFalse(JottyServerVersion.isNewerThanCompatCeiling("1.29.9"))
        assertFalse(JottyServerVersion.isNewerThanCompatCeiling("1.28.0"))
        assertFalse(JottyServerVersion.isNewerThanCompatCeiling("1.27.0"))
        assertFalse(JottyServerVersion.isNewerThanCompatCeiling(null))
        assertTrue(JottyServerVersion.isNewerThanCompatCeiling("1.30.0"))
        assertTrue(JottyServerVersion.isNewerThanCompatCeiling("2.0.0"))
    }

    @Test
    fun isBelowMinimum_blocksOnlyKnownOldVersions() {
        assertTrue(JottyServerVersion.isBelowMinimum("1.24.9"))
        assertTrue(JottyServerVersion.isBelowMinimum("v1.20.0"))
        assertFalse(JottyServerVersion.isBelowMinimum("1.25.0"))
        assertFalse(JottyServerVersion.isBelowMinimum("1.29.2"))
        assertFalse(JottyServerVersion.isBelowMinimum(null))
        assertFalse(JottyServerVersion.isBelowMinimum("dev"))
    }
}
