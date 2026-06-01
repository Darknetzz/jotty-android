package com.jotty.android.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReducedMotionTest {
    @Test
    fun resolveReducedMotionMode() {
        assertTrue(resolveReducedMotionEnabled(null, systemReducedMotion = false))
        assertTrue(resolveReducedMotionEnabled("on", systemReducedMotion = false))
        assertFalse(resolveReducedMotionEnabled("off", systemReducedMotion = true))
        assertTrue(resolveReducedMotionEnabled("system", systemReducedMotion = true))
        assertFalse(resolveReducedMotionEnabled("system", systemReducedMotion = false))
    }
}
