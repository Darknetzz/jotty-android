package com.jotty.android.util

import okhttp3.Headers
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Helpers for optional per-instance HTTP headers (e.g. reverse-proxy auth).
 * Validates names/values with OkHttp rules and keeps apply/redact logic in one place.
 */
object CustomHttpHeaders {
    /** Trims names, drops blank names, last value wins for duplicate names. */
    fun normalize(pairs: Iterable<Pair<String, String>>): Map<String, String> {
        val out = linkedMapOf<String, String>()
        for ((rawName, value) in pairs) {
            val name = rawName.trim()
            if (name.isBlank()) continue
            out[name] = value
        }
        return out
    }

    fun normalize(headers: Map<String, String>?): Map<String, String> {
        if (headers.isNullOrEmpty()) return emptyMap()
        return normalize(headers.entries.map { it.key to it.value })
    }

    /** Returns true when OkHttp accepts [name] and [value] as a request header. */
    fun isValid(
        name: String,
        value: String,
    ): Boolean = runCatching { Headers.Builder().add(name, value) }.isSuccess

    /** First invalid entry after [normalize], or null if all are valid. */
    fun firstInvalid(headers: Map<String, String>): Pair<String, String>? =
        normalize(headers).entries
            .firstOrNull { (name, value) -> !isValid(name, value) }
            ?.let { it.key to it.value }

    fun applyTo(
        builder: Request.Builder,
        headers: Map<String, String>,
    ) {
        normalize(headers).forEach { (name, value) ->
            builder.addHeader(name, value)
        }
    }

    /** Redact every custom header name in debug HTTP logs (values are often secrets). */
    fun redactIn(
        logging: HttpLoggingInterceptor,
        headers: Map<String, String>,
    ) {
        normalize(headers).keys.forEach { logging.redactHeader(it) }
    }
}
