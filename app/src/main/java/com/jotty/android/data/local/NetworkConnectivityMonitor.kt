package com.jotty.android.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.jotty.android.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide network connectivity (single [ConnectivityManager.NetworkCallback]).
 * Offline notes and checklists repositories subscribe so tab switches do not
 * register competing callbacks and desync "Online" / "Offline" state ([#27]).
 */
object NetworkConnectivityMonitor {
    private const val TAG = "ConnectivityMonitor"

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    @Volatile
    private var started = false

    private var connectivityManager: ConnectivityManager? = null

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @Synchronized
    fun ensureStarted(context: Context) {
        if (started) return
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        connectivityManager = cm
        refreshOnlineState(appContext, reason = "init")

        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    refreshOnlineState(appContext, reason = "onAvailable")
                }

                override fun onLost(network: Network) {
                    refreshOnlineState(appContext, reason = "onLost")
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    refreshOnlineState(appContext, reason = "onCapabilitiesChanged")
                }
            }
        networkCallback = callback
        cm?.registerDefaultNetworkCallback(callback)
        started = true
        AppLog.d(TAG, "Default network callback registered (online=${_isOnline.value})")
    }

    fun checkConnectivity(context: Context): Boolean {
        val cm =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager
                ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun refreshOnlineState(
        context: Context,
        reason: String,
    ) {
        val online = checkConnectivity(context)
        if (_isOnline.value != online) {
            AppLog.d(TAG, "Connectivity $reason -> online=$online")
            _isOnline.value = online
        }
    }

    /** Visible for tests. */
    @Synchronized
    internal fun resetForTests() {
        networkCallback?.let { cb ->
            runCatching { connectivityManager?.unregisterNetworkCallback(cb) }
        }
        networkCallback = null
        connectivityManager = null
        started = false
        _isOnline.value = false
    }

    internal fun setOnlineForTests(value: Boolean) {
        _isOnline.value = value
    }
}
