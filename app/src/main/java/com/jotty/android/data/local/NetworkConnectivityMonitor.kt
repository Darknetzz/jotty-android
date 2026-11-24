package com.jotty.android.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.jotty.android.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * App-wide network connectivity (single [ConnectivityManager.NetworkCallback]).
 * Offline notes and checklists repositories subscribe so tab switches do not
 * register competing callbacks and desync "Online" / "Offline" state ([#27]).
 *
 * [isOnline] means "the device has a network AND the Jotty server answered the last time we
 * asked". A phone on mobile data with a LAN-only server (private DNS, VPN down) has a network,
 * yet every request fails; repositories report that with [reportServerUnreachable] so the app
 * behaves as offline (local writes + pending sync) until a probe succeeds again ([#115]).
 */
object NetworkConnectivityMonitor {
    private const val TAG = "ConnectivityMonitor"

    /** Retry delays after the server stops answering; the last one repeats. */
    private val DEFAULT_PROBE_BACKOFF_MS = longArrayOf(15_000L, 30_000L, 60_000L, 120_000L, 300_000L)

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val stateLock = Any()

    /** Device-level connectivity ([checkConnectivity]). */
    private var networkOnline = false

    /** Instance whose server stopped answering, or null when reachable / unknown. */
    private var unreachableInstanceId: String? = null

    private var probeJob: Job? = null

    /** Wakes the probe loop early (network change, app foreground, sync request). */
    private val probeWakeUp = Channel<Unit>(Channel.CONFLATED)

    private var probeScope = newProbeScope()

    /** Visible for tests. */
    @Volatile
    internal var probeBackoffMs: LongArray = DEFAULT_PROBE_BACKOFF_MS

    @Volatile
    private var started = false

    private var connectivityManager: ConnectivityManager? = null

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    val isServerUnreachable: Boolean
        get() = synchronized(stateLock) { unreachableInstanceId != null }

    private fun newProbeScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
                    requestProbe()
                }

                override fun onLost(network: Network) {
                    refreshOnlineState(appContext, reason = "onLost")
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    refreshOnlineState(appContext, reason = "onCapabilitiesChanged")
                    requestProbe()
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
        // INTERNET only (not VALIDATED): LAN / self-hosted servers often have INTERNET but
        // stay "unvalidated" briefly or on split DNS; server reachability is tracked separately.
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Re-check when the app returns to foreground (e.g. after toggling Wi‑Fi). */
    fun refreshIfStarted(context: Context) {
        if (!started) return
        refreshOnlineState(context.applicationContext, reason = "refresh")
        requestProbe()
    }

    /**
     * The server for [instanceId] did not answer (see `Throwable.isServerUnreachable`).
     * Flips [isOnline] to false and retries [probe] with backoff until it returns true.
     */
    fun reportServerUnreachable(
        instanceId: String,
        probe: suspend () -> Boolean,
    ) {
        synchronized(stateLock) {
            if (unreachableInstanceId == instanceId && probeJob?.isActive == true) return
            unreachableInstanceId = instanceId
            AppLog.d(TAG, "Server unreachable (instance: $instanceId) — treating as offline")
            publishLocked()
            probeJob?.cancel()
            probeJob = probeScope.launch { runProbeLoop(instanceId, probe) }
        }
    }

    /** A request to [instanceId] succeeded. */
    fun reportServerReachable(instanceId: String) {
        synchronized(stateLock) {
            if (unreachableInstanceId == instanceId) clearUnreachableLocked(reason = "request succeeded")
        }
    }

    /** Another instance became active: its reachability is unknown, so assume reachable. */
    fun onInstanceActive(instanceId: String) {
        synchronized(stateLock) {
            val current = unreachableInstanceId ?: return
            if (current != instanceId) clearUnreachableLocked(reason = "instance changed")
        }
    }

    /** Probe now instead of waiting out the backoff. No-op while the server is reachable. */
    fun requestProbe() {
        if (isServerUnreachable) probeWakeUp.trySend(Unit)
    }

    private suspend fun runProbeLoop(
        instanceId: String,
        probe: suspend () -> Boolean,
    ) {
        var attempt = 0
        while (true) {
            val backoff = probeBackoffMs
            withTimeoutOrNull(backoff[attempt.coerceAtMost(backoff.lastIndex)]) { probeWakeUp.receive() }
            attempt++
            if (!synchronized(stateLock) { networkOnline }) continue
            val reachable =
                try {
                    probe()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
            if (!reachable) continue
            synchronized(stateLock) {
                if (unreachableInstanceId == instanceId) {
                    // Finishing normally: don't cancel ourselves from clearUnreachableLocked.
                    probeJob = null
                    clearUnreachableLocked(reason = "probe succeeded")
                }
            }
            return
        }
    }

    private fun clearUnreachableLocked(reason: String) {
        AppLog.d(TAG, "Server reachable again ($reason)")
        unreachableInstanceId = null
        probeJob?.cancel()
        probeJob = null
        publishLocked()
    }

    private fun publishLocked() {
        val online = networkOnline && unreachableInstanceId == null
        if (_isOnline.value != online) _isOnline.value = online
    }

    private fun refreshOnlineState(
        context: Context,
        reason: String,
    ) {
        val online = checkConnectivity(context)
        synchronized(stateLock) {
            if (networkOnline != online) {
                AppLog.d(TAG, "Connectivity $reason -> online=$online")
                networkOnline = online
            }
            publishLocked()
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
        synchronized(stateLock) {
            probeScope.cancel()
            probeScope = newProbeScope()
            probeJob = null
            unreachableInstanceId = null
            networkOnline = false
            probeBackoffMs = DEFAULT_PROBE_BACKOFF_MS
            while (probeWakeUp.tryReceive().isSuccess) Unit
            publishLocked()
        }
    }

    internal fun setOnlineForTests(value: Boolean) {
        synchronized(stateLock) {
            networkOnline = value
            publishLocked()
        }
    }
}
