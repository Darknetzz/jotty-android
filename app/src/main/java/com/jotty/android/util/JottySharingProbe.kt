package com.jotty.android.util

import com.jotty.android.data.api.JottyApi
import retrofit2.HttpException
import java.util.concurrent.ConcurrentHashMap

/** Cached per-instance result of probing optional Jotty sharing REST endpoints. */
object JottySharingProbe {
    private val supportedKeys = ConcurrentHashMap.newKeySet<String>()
    private val unsupportedKeys = ConcurrentHashMap.newKeySet<String>()

    suspend fun probeSharingApi(
        api: JottyApi,
        capabilitiesKey: String,
    ): Boolean {
        if (capabilitiesKey.isBlank()) return false
        if (capabilitiesKey in supportedKeys) return true
        if (capabilitiesKey in unsupportedKeys) return false
        return try {
            api.getSharingInfo("note", "00000000-0000-0000-0000-000000000000")
            supportedKeys.add(capabilitiesKey)
            true
        } catch (e: HttpException) {
            if (e.code() == 404 || e.code() == 405) {
                unsupportedKeys.add(capabilitiesKey)
                false
            } else {
                // Endpoint exists (400/403/etc.) even if probe id is invalid.
                supportedKeys.add(capabilitiesKey)
                true
            }
        } catch (_: Exception) {
            unsupportedKeys.add(capabilitiesKey)
            false
        }
    }

    fun isSharingSupported(capabilitiesKey: String): Boolean =
        capabilitiesKey.isNotBlank() && capabilitiesKey in supportedKeys

    internal fun resetForTests() {
        supportedKeys.clear()
        unsupportedKeys.clear()
    }
}
