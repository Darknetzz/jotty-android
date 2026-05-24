package com.jotty.android.data.local

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkConnectivityMonitorTest {
    @After
    fun tearDown() {
        NetworkConnectivityMonitor.resetForTests()
    }

    @Test
    fun setOnlineForTests_updatesFlow() {
        NetworkConnectivityMonitor.setOnlineForTests(true)
        assertTrue(NetworkConnectivityMonitor.isOnline.value)
        NetworkConnectivityMonitor.setOnlineForTests(false)
        assertFalse(NetworkConnectivityMonitor.isOnline.value)
    }
}
