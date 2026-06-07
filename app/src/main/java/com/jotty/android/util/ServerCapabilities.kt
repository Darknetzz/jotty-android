package com.jotty.android.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-instance feature flags inferred from API responses (e.g. PATCH unsupported on older Jotty).
 */
object ServerCapabilities {
    private val itemPatchLimitedKeys = ConcurrentHashMap.newKeySet<String>()
    private val privateImagesAuthBlockedKeys = ConcurrentHashMap.newKeySet<String>()
    private val _privateImagesAuthBlocked =
        MutableStateFlow(privateImagesAuthBlockedKeys.toSet())

    /** Emits when [markPrivateImagesAuthBlocked] / [clearPrivateImagesAuthBlocked] change. */
    val privateImagesAuthBlocked: StateFlow<Set<String>> = _privateImagesAuthBlocked.asStateFlow()

    fun markItemPatchLimited(capabilitiesKey: String) {
        if (capabilitiesKey.isNotBlank()) {
            itemPatchLimitedKeys.add(capabilitiesKey)
        }
    }

    fun isItemPatchLimited(capabilitiesKey: String): Boolean =
        capabilitiesKey.isNotBlank() && capabilitiesKey in itemPatchLimitedKeys

    fun isPatchUnsupportedHttpCode(code: Int): Boolean = code == 404 || code == 405

    fun markPrivateImagesAuthBlocked(capabilitiesKey: String) {
        if (capabilitiesKey.isNotBlank() && privateImagesAuthBlockedKeys.add(capabilitiesKey)) {
            _privateImagesAuthBlocked.value = privateImagesAuthBlockedKeys.toSet()
        }
    }

    fun clearPrivateImagesAuthBlocked(capabilitiesKey: String) {
        if (capabilitiesKey.isNotBlank() && privateImagesAuthBlockedKeys.remove(capabilitiesKey)) {
            _privateImagesAuthBlocked.value = privateImagesAuthBlockedKeys.toSet()
        }
    }

    fun isPrivateImagesAuthBlocked(capabilitiesKey: String): Boolean =
        capabilitiesKey.isNotBlank() && capabilitiesKey in privateImagesAuthBlockedKeys

    fun isPrivateImagesAuthBlockedHttpCode(code: Int): Boolean = code == 401 || code == 403

    internal fun resetPrivateImagesAuthBlockedForTests() {
        privateImagesAuthBlockedKeys.clear()
        _privateImagesAuthBlocked.value = emptySet()
    }
}
