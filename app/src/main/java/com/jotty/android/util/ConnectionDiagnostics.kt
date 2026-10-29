package com.jotty.android.util

import androidx.annotation.StringRes
import com.google.gson.JsonParseException
import com.jotty.android.R
import com.jotty.android.data.api.ApiClient
import com.jotty.android.data.api.JottyApi
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Two-step probe used when adding/editing an instance: `GET api/health` (server reachable, version)
 * then `GET api/summary` (API key accepted). Failures are mapped to a title, an explanation and a
 * short list of likely causes so the user can fix the setup without guessing.
 */
object ConnectionDiagnostics {
    enum class Stage { Reach, Authenticate }

    sealed interface Result {
        data class Success(
            val api: JottyApi,
            val version: String?,
            val username: String?,
            val latencyMs: Long,
        ) : Result

        data class Failure(
            @StringRes val titleRes: Int,
            @StringRes val explanationRes: Int,
            val hintResIds: List<Int>,
            /** Optional server-provided or formatted detail shown under the explanation. */
            val detail: String? = null,
            val formatArgs: List<String> = emptyList(),
        ) : Result
    }

    suspend fun run(
        serverUrl: String,
        apiKey: String,
        customHeaders: Map<String, String>,
    ): Result {
        val started = System.currentTimeMillis()
        var stage = Stage.Reach
        return try {
            val api = ApiClient.create(serverUrl, apiKey, customHeaders)
            val health = api.health()
            if (JottyServerVersion.isBelowMinimum(health.version)) {
                return Result.Failure(
                    titleRes = R.string.conn_test_title_too_old,
                    explanationRes = R.string.server_version_unsupported,
                    hintResIds = listOf(R.string.conn_hint_upgrade_jotty),
                    formatArgs = listOf(JottyServerVersion.MIN_SUPPORTED_LABEL, health.version.orEmpty()),
                )
            }
            val latency = System.currentTimeMillis() - started
            stage = Stage.Authenticate
            // health() is unauthenticated; verify the API key with an authenticated endpoint.
            val summary = api.getSummary()
            Result.Success(api, health.version, summary.summary?.username, latency)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            diagnose(e, stage)
        }
    }

    /** Visible for testing. */
    internal fun diagnose(
        t: Throwable,
        stage: Stage,
    ): Result.Failure {
        val messages = messages(t)
        val http = t as? HttpException ?: t.cause as? HttpException
        if (http != null) return diagnoseHttp(http.code(), stage)
        return when {
            t is UnknownHostException || t.cause is UnknownHostException ->
                Result.Failure(
                    R.string.conn_test_title_unknown_host,
                    R.string.conn_test_explain_unknown_host,
                    listOf(R.string.conn_hint_check_url_typo, R.string.conn_hint_vpn, R.string.conn_hint_device_online),
                )
            t is SocketTimeoutException || t.cause is SocketTimeoutException ->
                Result.Failure(
                    R.string.conn_test_title_timeout,
                    R.string.conn_test_explain_timeout,
                    listOf(R.string.conn_hint_server_running, R.string.conn_hint_firewall, R.string.conn_hint_vpn),
                )
            messages.contains("cleartext", ignoreCase = true) ->
                Result.Failure(
                    R.string.conn_test_title_cleartext,
                    R.string.conn_test_explain_cleartext,
                    listOf(R.string.conn_hint_use_https),
                )
            t is SSLException || ApiErrorHelper.sslErrorMessageResId(messages) != null ->
                Result.Failure(
                    R.string.conn_test_title_ssl,
                    if (ApiErrorHelper.sslErrorMessageResId(messages) == R.string.error_ssl_hostname_mismatch) {
                        R.string.error_ssl_hostname_mismatch
                    } else {
                        R.string.conn_test_explain_ssl
                    },
                    listOf(R.string.conn_hint_self_signed, R.string.conn_hint_cert_expired, R.string.conn_hint_proxy_hostname),
                )
            messages.contains("connection refused", ignoreCase = true) ||
                messages.contains("failed to connect", ignoreCase = true) ->
                Result.Failure(
                    R.string.conn_test_title_refused,
                    R.string.conn_test_explain_refused,
                    listOf(R.string.conn_hint_port, R.string.conn_hint_server_running, R.string.conn_hint_firewall),
                )
            t is JsonParseException || t.cause is JsonParseException || t is IllegalStateException ->
                Result.Failure(
                    R.string.conn_test_title_not_jotty,
                    R.string.conn_test_explain_not_jotty,
                    listOf(R.string.conn_hint_proxy_login, R.string.conn_hint_subpath, R.string.conn_hint_check_url_typo),
                )
            t is IllegalArgumentException ->
                Result.Failure(
                    R.string.conn_test_title_bad_url,
                    R.string.conn_test_explain_bad_url,
                    listOf(R.string.conn_hint_url_format),
                )
            t is IOException ->
                Result.Failure(
                    R.string.conn_test_title_network,
                    R.string.conn_test_explain_network,
                    listOf(R.string.conn_hint_device_online, R.string.conn_hint_vpn, R.string.conn_hint_server_running),
                )
            else ->
                Result.Failure(
                    R.string.conn_test_title_unknown,
                    R.string.conn_test_explain_unknown,
                    listOf(R.string.conn_hint_check_url_typo, R.string.conn_hint_export_logs),
                    detail = t.message?.takeUnless { ApiErrorHelper.isTechnicalTransportMessage(it) }?.take(200),
                )
        }
    }

    private fun diagnoseHttp(
        code: Int,
        stage: Stage,
    ): Result.Failure =
        when {
            code == 401 ->
                Result.Failure(
                    R.string.conn_test_title_bad_key,
                    R.string.conn_test_explain_bad_key,
                    listOf(R.string.conn_hint_key_copy, R.string.conn_hint_key_regenerate),
                )
            code == 403 ->
                Result.Failure(
                    R.string.conn_test_title_forbidden,
                    R.string.conn_test_explain_forbidden,
                    listOf(R.string.conn_hint_proxy_headers, R.string.conn_hint_key_regenerate),
                )
            code == 404 && stage == Stage.Reach ->
                Result.Failure(
                    R.string.conn_test_title_not_jotty,
                    R.string.conn_test_explain_404,
                    listOf(R.string.conn_hint_subpath, R.string.conn_hint_check_url_typo),
                )
            code == 429 ->
                Result.Failure(
                    R.string.conn_test_title_rate_limited,
                    R.string.error_rate_limited,
                    listOf(R.string.conn_hint_wait),
                )
            code in 502..504 ->
                Result.Failure(
                    R.string.conn_test_title_gateway,
                    R.string.conn_test_explain_gateway,
                    listOf(R.string.conn_hint_server_running, R.string.conn_hint_proxy_upstream),
                )
            code in 500..599 ->
                Result.Failure(
                    R.string.conn_test_title_server_error,
                    R.string.conn_test_explain_server_error,
                    listOf(R.string.conn_hint_server_logs),
                    formatArgs = listOf(code.toString()),
                )
            else ->
                Result.Failure(
                    R.string.conn_test_title_unexpected_status,
                    R.string.conn_test_explain_unexpected_status,
                    listOf(R.string.conn_hint_check_url_typo, R.string.conn_hint_proxy_login),
                    formatArgs = listOf(code.toString()),
                )
        }

    private fun messages(t: Throwable): String =
        generateSequence(t) { it.cause }.mapNotNull { it.message }.joinToString(" ")
}
