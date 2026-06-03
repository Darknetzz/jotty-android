package com.jotty.android.util

import com.jotty.android.R
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class ApiErrorHelperTest {
    @Test
    fun errorMessageResId_unknownHost_returns_no_internet() {
        assertEquals(
            R.string.no_internet_connection,
            ApiErrorHelper.errorMessageResId(UnknownHostException()),
        )
    }

    @Test
    fun errorMessageResId_socketTimeout_returns_connection_timed_out() {
        assertEquals(
            R.string.connection_timed_out,
            ApiErrorHelper.errorMessageResId(SocketTimeoutException()),
        )
    }

    @Test
    fun errorMessageResId_ioException_returns_network_error() {
        assertEquals(
            R.string.network_error,
            ApiErrorHelper.errorMessageResId(IOException()),
        )
    }

    @Test
    fun errorMessageResId_ioException_ssl_stack_returns_ssl_message() {
        assertEquals(
            R.string.error_ssl_hostname_mismatch,
            ApiErrorHelper.errorMessageResId(
                IOException(
                    "Read error: ssl=0x7a0a408209d8: Failure in SSL library, usually a protocol error " +
                        "error:10000458:SSL routines:OPENSSL_internal:TLSV1_ALERT_UNRECOGNIZED_NAME",
                ),
            ),
        )
    }

    @Test
    fun isTechnicalTransportMessage_detects_boringssl_diagnostic() {
        assertEquals(
            true,
            ApiErrorHelper.isTechnicalTransportMessage(
                "Read error: ssl=0x7: Failure in SSL library (external/boringssl/src/ssl/tls_record.cc:486)",
            ),
        )
    }

    @Test
    fun errorMessageResId_ioException_cleartext_returns_dedicated_message() {
        assertEquals(
            R.string.error_cleartext_http_not_allowed,
            ApiErrorHelper.errorMessageResId(
                IOException("Cleartext HTTP traffic to 192.168.1.10 not permitted"),
            ),
        )
    }

    @Test
    fun errorMessageResId_ioException_connection_refused_returns_dedicated_message() {
        assertEquals(
            R.string.error_connection_refused,
            ApiErrorHelper.errorMessageResId(
                IOException("Failed to connect to /192.168.1.10:8080"),
            ),
        )
    }

    @Test
    fun errorMessageResId_sslException_returns_ssl_message() {
        assertEquals(
            R.string.error_ssl_or_certificate,
            ApiErrorHelper.errorMessageResId(SSLException("handshake failed")),
        )
    }

    @Test
    fun errorMessageResId_httpException_401_returns_invalid_api_key() {
        val body = "".toResponseBody("text/plain".toMediaTypeOrNull())
        val response = Response.error<String>(401, body)
        assertEquals(
            R.string.error_invalid_api_key,
            ApiErrorHelper.errorMessageResId(HttpException(response)),
        )
    }

    @Test
    fun errorMessageResId_httpException_403_returns_access_denied() {
        val body = "".toResponseBody("text/plain".toMediaTypeOrNull())
        val response = Response.error<String>(403, body)
        assertEquals(
            R.string.error_access_denied,
            ApiErrorHelper.errorMessageResId(HttpException(response)),
        )
    }

    @Test
    fun errorMessageResId_httpException_5xx_returns_server_error() {
        val body = "".toResponseBody("text/plain".toMediaTypeOrNull())
        val response = Response.error<String>(500, body)
        assertEquals(
            R.string.server_error,
            ApiErrorHelper.errorMessageResId(HttpException(response)),
        )
    }

    @Test
    fun errorMessageResId_httpException_404_returns_not_found() {
        val body = "".toResponseBody("text/plain".toMediaTypeOrNull())
        val response = Response.error<String>(404, body)
        assertEquals(
            R.string.error_not_found,
            ApiErrorHelper.errorMessageResId(HttpException(response)),
        )
    }

    @Test
    fun errorMessageResId_httpException_429_returns_rate_limited() {
        val body = "".toResponseBody("text/plain".toMediaTypeOrNull())
        val response = Response.error<String>(429, body)
        assertEquals(
            R.string.error_rate_limited,
            ApiErrorHelper.errorMessageResId(HttpException(response)),
        )
    }

    @Test
    fun errorMessageResId_httpException_other4xx_returns_request_failed() {
        val body = "".toResponseBody("text/plain".toMediaTypeOrNull())
        val response = Response.error<String>(400, body)
        assertEquals(
            R.string.request_failed,
            ApiErrorHelper.errorMessageResId(HttpException(response)),
        )
    }

    @Test
    fun errorMessageResId_other_returns_unknown_error() {
        assertEquals(
            R.string.unknown_error,
            ApiErrorHelper.errorMessageResId(RuntimeException()),
        )
    }

    @Test
    fun isHtmlErrorBody_detects_nginx_forbidden_page() {
        val nginx403 =
            """
            <html>
            <head><title>403 Forbidden</title></head>
            <body>
            <center><h1>403 Forbidden</h1></center>
            </body>
            </html>
            """.trimIndent()
        assertEquals(true, ApiErrorHelper.isHtmlErrorBody(nginx403))
    }

    @Test
    fun isHtmlErrorBody_allows_plain_api_message() {
        assertEquals(false, ApiErrorHelper.isHtmlErrorBody("Invalid API key"))
    }
}
