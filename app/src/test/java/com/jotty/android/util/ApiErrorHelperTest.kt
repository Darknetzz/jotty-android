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
}
