package com.jotty.android.util

import android.content.Context
import com.google.gson.JsonParser
import com.jotty.android.R
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Maps exceptions to short, user-friendly messages suitable for UI (Snackbar, error state).
 * Uses string resources for i18n consistency.
 */
object ApiErrorHelper {
    private const val SERVER_MESSAGE_MAX_LEN = 220

    fun userMessage(
        context: Context,
        t: Throwable,
    ): String {
        if (t is HttpException) {
            httpExceptionDetail(t)?.let { return it }
        }
        return context.getString(errorMessageResId(t))
    }

    /** Best-effort parse of JSON error body (e.g. `{ "message": "..." }`). */
    private fun httpExceptionDetail(t: HttpException): String? {
        return try {
            val raw = t.response()?.errorBody()?.use { it.string() }?.trim().orEmpty()
            if (raw.isEmpty()) return null
            if (!raw.startsWith("{")) {
                return raw.take(SERVER_MESSAGE_MAX_LEN)
            }
            val obj = JsonParser.parseString(raw).asJsonObject
            val msg =
                sequenceOf("message", "error", "detail", "title")
                    .mapNotNull { key -> obj.get(key)?.takeUnless { it.isJsonNull }?.asString?.trim() }
                    .firstOrNull { it.isNotEmpty() }
                    ?: return null
            msg.take(SERVER_MESSAGE_MAX_LEN)
        } catch (_: Exception) {
            null
        }
    }

    /** Visible for testing: returns the string resource ID for the given throwable. */
    internal fun errorMessageResId(t: Throwable): Int =
        when (t) {
            is UnknownHostException -> R.string.no_internet_connection
            is SocketTimeoutException -> R.string.connection_timed_out
            is SSLException -> R.string.error_ssl_or_certificate
            is IOException -> ioExceptionMessageResId(t)
            is HttpException ->
                when (t.code()) {
                    401 -> R.string.error_invalid_api_key
                    403 -> R.string.error_access_denied
                    404 -> R.string.error_not_found
                    429 -> R.string.error_rate_limited
                    in 500..599 -> R.string.server_error
                    else -> R.string.request_failed
                }
            else -> unknownThrowableMessageResId(t)
        }

    private fun ioExceptionMessageResId(t: IOException): Int {
        val combined = throwableMessages(t).joinToString(" ")
        return when {
            combined.contains("cleartext", ignoreCase = true) -> R.string.error_cleartext_http_not_allowed
            combined.contains("connection refused", ignoreCase = true) ||
                combined.contains("failed to connect", ignoreCase = true) -> R.string.error_connection_refused
            else -> R.string.network_error
        }
    }

    private fun unknownThrowableMessageResId(t: Throwable): Int {
        val combined = throwableMessages(t).joinToString(" ")
        if (combined.contains("cleartext", ignoreCase = true)) {
            return R.string.error_cleartext_http_not_allowed
        }
        return R.string.unknown_error
    }

    private fun throwableMessages(t: Throwable): Sequence<String> =
        sequence {
            var current: Throwable? = t
            while (current != null) {
                current.message?.let { yield(it) }
                current = current.cause
            }
        }
}
