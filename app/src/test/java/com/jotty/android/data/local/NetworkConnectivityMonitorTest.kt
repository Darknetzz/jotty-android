package com.jotty.android.data.local

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetworkConnectivityMonitorTest {
    @After
    fun tearDown() {
        NetworkConnectivityMonitor.resetForTests()
    }

    private suspend fun awaitOnline() {
        withTimeout(5_000) { NetworkConnectivityMonitor.isOnline.first { it } }
    }

    @Test
    fun setOnlineForTests_updatesFlow() {
        NetworkConnectivityMonitor.setOnlineForTests(true)
        assertTrue(NetworkConnectivityMonitor.isOnline.value)
        NetworkConnectivityMonitor.setOnlineForTests(false)
        assertFalse(NetworkConnectivityMonitor.isOnline.value)
    }

    @Test
    fun reportServerUnreachable_isOfflineUntilProbeSucceeds() =
        runBlocking {
            NetworkConnectivityMonitor.setOnlineForTests(true)
            NetworkConnectivityMonitor.probeBackoffMs = longArrayOf(10L)
            val attempts = AtomicInteger()

            NetworkConnectivityMonitor.reportServerUnreachable("instance-1") { attempts.incrementAndGet() >= 3 }

            assertFalse(NetworkConnectivityMonitor.isOnline.value)
            assertTrue(NetworkConnectivityMonitor.isServerUnreachable)
            awaitOnline()
            assertEquals(3, attempts.get())
            assertFalse(NetworkConnectivityMonitor.isServerUnreachable)
        }

    @Test
    fun probeThatThrows_countsAsStillUnreachable() =
        runBlocking {
            NetworkConnectivityMonitor.setOnlineForTests(true)
            NetworkConnectivityMonitor.probeBackoffMs = longArrayOf(10L)
            val attempts = AtomicInteger()

            NetworkConnectivityMonitor.reportServerUnreachable("instance-1") {
                if (attempts.incrementAndGet() < 2) throw java.net.UnknownHostException("jotty.internal")
                true
            }

            awaitOnline()
            assertEquals(2, attempts.get())
        }

    @Test
    fun requestProbe_skipsTheBackoffWait() =
        runBlocking {
            NetworkConnectivityMonitor.setOnlineForTests(true)
            NetworkConnectivityMonitor.probeBackoffMs = longArrayOf(600_000L)

            NetworkConnectivityMonitor.reportServerUnreachable("instance-1") { true }
            assertFalse(NetworkConnectivityMonitor.isOnline.value)
            NetworkConnectivityMonitor.requestProbe()

            awaitOnline()
        }

    @Test
    fun reportServerUnreachable_twice_keepsOneProbeLoop() =
        runBlocking {
            NetworkConnectivityMonitor.setOnlineForTests(true)
            NetworkConnectivityMonitor.probeBackoffMs = longArrayOf(600_000L)
            val first = AtomicInteger()
            val second = AtomicInteger()

            NetworkConnectivityMonitor.reportServerUnreachable("instance-1") {
                first.incrementAndGet()
                true
            }
            NetworkConnectivityMonitor.reportServerUnreachable("instance-1") {
                second.incrementAndGet()
                true
            }
            NetworkConnectivityMonitor.requestProbe()

            awaitOnline()
            assertEquals(1, first.get())
            assertEquals(0, second.get())
        }

    @Test
    fun probe_waitsForDeviceNetwork() =
        runBlocking {
            NetworkConnectivityMonitor.setOnlineForTests(false)
            NetworkConnectivityMonitor.probeBackoffMs = longArrayOf(10L)
            val attempts = AtomicInteger()

            NetworkConnectivityMonitor.reportServerUnreachable("instance-1") {
                attempts.incrementAndGet()
                true
            }
            delay(200)
            assertEquals(0, attempts.get())
            assertFalse(NetworkConnectivityMonitor.isOnline.value)

            NetworkConnectivityMonitor.setOnlineForTests(true)
            awaitOnline()
            assertEquals(1, attempts.get())
        }

    @Test
    fun reportServerReachable_restoresOnline() {
        NetworkConnectivityMonitor.setOnlineForTests(true)
        NetworkConnectivityMonitor.probeBackoffMs = longArrayOf(600_000L)
        NetworkConnectivityMonitor.reportServerUnreachable("instance-1") { false }
        assertFalse(NetworkConnectivityMonitor.isOnline.value)

        NetworkConnectivityMonitor.reportServerReachable("other-instance")
        assertFalse(NetworkConnectivityMonitor.isOnline.value)

        NetworkConnectivityMonitor.reportServerReachable("instance-1")
        assertTrue(NetworkConnectivityMonitor.isOnline.value)
    }

    @Test
    fun onInstanceActive_clearsAnotherInstancesUnreachableState() {
        NetworkConnectivityMonitor.setOnlineForTests(true)
        NetworkConnectivityMonitor.probeBackoffMs = longArrayOf(600_000L)
        NetworkConnectivityMonitor.reportServerUnreachable("instance-1") { false }

        NetworkConnectivityMonitor.onInstanceActive("instance-1")
        assertFalse(NetworkConnectivityMonitor.isOnline.value)

        NetworkConnectivityMonitor.onInstanceActive("instance-2")
        assertTrue(NetworkConnectivityMonitor.isOnline.value)
    }
}
