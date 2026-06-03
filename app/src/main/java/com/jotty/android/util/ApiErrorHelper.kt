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
            return context.getString(errorMessageResId(t))
        }
        val cause = t.cause
        if (cause is HttpException) {
            return userMessage(context, cause)
        }
        preformattedUserMessage(t.message)?.let { return it }
        return context.getString(errorMessageResId(t))
    }

    /** Repository/sync code may attach a short message; ignore Retrofit status lines, HTML, and low-level TLS/stack text. */
    private fun preformattedUserMessage(message: String?): String? {
        val trimmed = message?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("HTTP ", ignoreCase = true)) return null
        if (isHtmlErrorBody(trimmed)) return null
        if (isTechnicalTransportMessage(trimmed)) return null
        return trimmed.take(SERVER_MESSAGE_MAX_LEN)
    }

    /** OkHttp/BoringSSL and similar layers expose long diagnostic strings unsuitable for UI. */
    internal fun isTechnicalTransportMessage(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("ssl=") ||
            lower.contains("openssl") ||
            lower.contains("boringssl") ||
            lower.contains("tlsv1_") ||
            lower.contains("failure in ssl library") ||
            lower.contains("ssl routines:")
    }

    internal fun sslErrorMessageResId(messages: String): Int? {
        val lower = messages.lowercase()
        return when {
            lower.contains("tlsv1_alert_unrecognized_name") ||
                lower.contains("unrecognized_name") -> R.string.error_ssl_hostname_mismatch
            lower.contains("ssl") ||
                lower.contains("certificate") ||
                lower.contains("handshake") -> R.string.error_ssl_or_certificate
            else -> null
        }
    }

    /** Best-effort parse of JSON or plain-text API error body; ignores HTML/proxy pages. */
    private fun httpExceptionDetail(t: HttpException): String? {
        return try {
            val response = t.response() ?: return null
            val contentType = response.headers()["Content-Type"]?.lowercase().orEmpty()
            if (contentType.contains("text/html")) return null

            val raw = response.errorBody()?.use { it.string() }?.trim().orEmpty()
            if (raw.isEmpty()) return null
            if (isHtmlErrorBody(raw)) return null
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

    /** Visible for testing: nginx/reverse-proxy HTML must not be shown in the UI. */
    internal fun isHtmlErrorBody(body: String): Boolean {
        val trimmed = body.trimStart()
        val lower = trimmed.lowercase()
        return lower.startsWith("<!doctype") ||
            lower.startsWith("<html") ||
            (lower.contains("<html") && lower.contains("</html>"))
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
        sslErrorMessageResId(combined)?.let { return it }
        return when {
            combined.contains("cleartext", ignoreCase = true) -> R.string.error_cleartext_http_not_allowed
            combined.contains("connection refused", ignoreCase = true) ||
                combined.contains("failed to connect", ignoreCase = true) -> R.string.error_connection_refused
            else -> R.string.network_error
        }
    }

    private fun unknownThrowableMessageResId(t: Throwable): Int {
        val combined = throwableMessages(t).joinToString(" ")
        sslErrorMessageResId(combined)?.let { return it }
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
