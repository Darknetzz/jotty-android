package com.jotty.android.util

import java.util.concurrent.ConcurrentHashMap

/**
 * Per-instance feature flags inferred from API responses (e.g. PATCH unsupported on older Jotty).
 */
object ServerCapabilities {
    private val itemPatchLimitedKeys = ConcurrentHashMap.newKeySet<String>()

    fun markItemPatchLimited(capabilitiesKey: String) {
        if (capabilitiesKey.isNotBlank()) {
            itemPatchLimitedKeys.add(capabilitiesKey)
        }
    }

    fun isItemPatchLimited(capabilitiesKey: String): Boolean =
        capabilitiesKey.isNotBlank() && capabilitiesKey in itemPatchLimitedKeys

    fun isPatchUnsupportedHttpCode(code: Int): Boolean = code == 404 || code == 405
}
