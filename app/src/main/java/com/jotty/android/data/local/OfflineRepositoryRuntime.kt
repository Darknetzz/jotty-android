package com.jotty.android.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.jotty.android.util.AppLog
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Shared sync status for offline repositories.
 */
class SyncStatusState(initialOnline: Boolean) {
    private val _isOnline = MutableStateFlow(initialOnline)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _conflictsDetected = MutableStateFlow(0)
    val conflictsDetected: StateFlow<Int> = _conflictsDetected.asStateFlow()

    private val _lastSyncAttemptEpochMs = MutableStateFlow<Long?>(null)
    val lastSyncAttemptEpochMs: StateFlow<Long?> = _lastSyncAttemptEpochMs.asStateFlow()

    private val _lastSyncSuccessEpochMs = MutableStateFlow<Long?>(null)
    val lastSyncSuccessEpochMs: StateFlow<Long?> = _lastSyncSuccessEpochMs.asStateFlow()

    private val _lastSyncDurationText = MutableStateFlow<String?>(null)
    val lastSyncDurationText: StateFlow<String?> = _lastSyncDurationText.asStateFlow()

    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    private var syncStartedEpochMs: Long? = null

    fun setOnline(value: Boolean) {
        _isOnline.value = value
    }

    fun setSyncing(value: Boolean) {
        _isSyncing.value = value
    }

    fun setConflictsDetected(value: Int) {
        _conflictsDetected.value = value
    }

    fun markSyncStarted(nowEpochMs: Long = System.currentTimeMillis()) {
        syncStartedEpochMs = nowEpochMs
        _lastSyncAttemptEpochMs.value = nowEpochMs
        _lastSyncError.value = null
    }

    fun markSyncCompleted(
        success: Boolean,
        errorMessage: String? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        val startedAt = syncStartedEpochMs
        syncStartedEpochMs = null
        if (startedAt != null) {
            val elapsedMs = (nowEpochMs - startedAt).coerceAtLeast(0L)
            _lastSyncDurationText.value = elapsedMs.toDuration(DurationUnit.MILLISECONDS).toString()
        }
        if (success) {
            _lastSyncSuccessEpochMs.value = nowEpochMs
            _lastSyncError.value = null
        } else {
            _lastSyncError.value = errorMessage
        }
    }
}

/**
 * Shared lifecycle and connectivity handling for offline repositories.
 */
class OfflineRepositoryLifecycle(
    private val context: Context,
    initialOnlineOverride: Boolean?,
    private val registerNetworkCallback: Boolean,
    private val logTag: String,
    private val instanceId: String,
    private val onNetworkAvailable: suspend () -> Unit,
) {
    private val scopeExceptionHandler =
        CoroutineExceptionHandler { _, t ->
            AppLog.d(logTag, "Background coroutine failed: ${t.message}")
        }

    val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + scopeExceptionHandler)
    val syncStatus = SyncStatusState(initialOnlineOverride ?: checkConnectivity())

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    @Volatile private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile private var closed = false

    init {
        if (registerNetworkCallback) {
            connectivityManager?.let { cm ->
                val networkRequest =
                    NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            if (closed) return
                            AppLog.d(logTag, "Network available")
                            syncStatus.setOnline(true)
                            coroutineScope.launch { onNetworkAvailable() }
                        }

                        override fun onLost(network: Network) {
                            if (closed) return
                            AppLog.d(logTag, "Network lost")
                            syncStatus.setOnline(false)
                        }
                    }
                networkCallback = callback
                cm.registerNetworkCallback(networkRequest, callback)
                AppLog.d(logTag, "Network callback registered (instance: $instanceId)")
            }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        networkCallback?.let {
            connectivityManager?.unregisterNetworkCallback(it)
            networkCallback = null
        }
        coroutineScope.cancel()
        AppLog.d(logTag, "Closed (instance: $instanceId)")
    }

    private fun checkConnectivity(): Boolean {
        val cm = connectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
