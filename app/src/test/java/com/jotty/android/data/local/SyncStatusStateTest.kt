package com.jotty.android.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SyncStatusStateTest {
    @Test
    fun markSyncCompleted_success_setsAttemptSuccessAndDuration() {
        val state = SyncStatusState(initialOnline = true)

        state.markSyncStarted(nowEpochMs = 1_000L)
        state.markSyncCompleted(success = true, nowEpochMs = 2_200L)

        assertEquals(1_000L, state.lastSyncAttemptEpochMs.value)
        assertEquals(2_200L, state.lastSyncSuccessEpochMs.value)
        assertNull(state.lastSyncError.value)
        assertNotNull(state.lastSyncDurationText.value)
    }

    @Test
    fun markSyncCompleted_failure_setsErrorWithoutSuccessTimestamp() {
        val state = SyncStatusState(initialOnline = false)

        state.markSyncStarted(nowEpochMs = 5_000L)
        state.markSyncCompleted(success = false, errorMessage = "offline", nowEpochMs = 6_000L)

        assertEquals(5_000L, state.lastSyncAttemptEpochMs.value)
        assertNull(state.lastSyncSuccessEpochMs.value)
        assertEquals("offline", state.lastSyncError.value)
        assertNotNull(state.lastSyncDurationText.value)
    }
}
