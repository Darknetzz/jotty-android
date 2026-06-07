package com.jotty.android.util

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerCapabilitiesTest {
    @After
    fun tearDown() {
        ServerCapabilities.resetPrivateImagesAuthBlockedForTests()
    }

    @Test
    fun isPrivateImagesAuthBlockedHttpCode_matches401And403() {
        assertTrue(ServerCapabilities.isPrivateImagesAuthBlockedHttpCode(401))
        assertTrue(ServerCapabilities.isPrivateImagesAuthBlockedHttpCode(403))
        assertFalse(ServerCapabilities.isPrivateImagesAuthBlockedHttpCode(404))
        assertFalse(ServerCapabilities.isPrivateImagesAuthBlockedHttpCode(200))
    }

    @Test
    fun markAndClearPrivateImagesAuthBlocked_tracksPerInstance() {
        assertFalse(ServerCapabilities.isPrivateImagesAuthBlocked("inst-a"))
        ServerCapabilities.markPrivateImagesAuthBlocked("inst-a")
        assertTrue(ServerCapabilities.isPrivateImagesAuthBlocked("inst-a"))
        assertFalse(ServerCapabilities.isPrivateImagesAuthBlocked("inst-b"))
        ServerCapabilities.clearPrivateImagesAuthBlocked("inst-a")
        assertFalse(ServerCapabilities.isPrivateImagesAuthBlocked("inst-a"))
    }
}
