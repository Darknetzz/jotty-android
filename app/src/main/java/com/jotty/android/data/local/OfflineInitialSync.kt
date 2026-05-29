package com.jotty.android.data.local

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Pulls remote data when the device is online and the local cache for this instance is empty.
 *
 * Scoped to [viewModelScope] so navigation/setup transitions do not cancel an in-flight sync
 * (unlike `LaunchedEffect` in composables). Re-runs when connectivity is restored while the
 * cache is still empty.
 */
fun ViewModel.scheduleInitialOfflineSyncWhenEmpty(
    observeCacheEmpty: Flow<Boolean>,
    sync: suspend () -> Result<Unit>,
) {
    viewModelScope.launch {
        combine(
            NetworkConnectivityMonitor.isOnline,
            observeCacheEmpty,
        ) { online, empty -> online to empty }
            .distinctUntilChanged()
            .collect { (online, empty) ->
                if (online && empty) {
                    sync()
                }
            }
    }
}
