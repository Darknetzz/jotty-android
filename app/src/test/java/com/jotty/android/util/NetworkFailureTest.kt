package com.jotty.android.util

import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class NetworkFailureTest {
    @Test
    fun ioFailures_areUnreachable() {
        assertTrue(UnknownHostException("jotty.internal").isServerUnreachable())
        assertTrue(SocketTimeoutException().isServerUnreachable())
        assertTrue(ConnectException().isServerUnreachable())
        assertTrue(SSLException("handshake").isServerUnreachable())
        assertTrue(IOException("reset").isServerUnreachable())
    }

    @Test
    fun wrappedIoFailure_isUnreachable() {
        val wrapped = Exception("No internet connection", RuntimeException(UnknownHostException()))
        assertTrue(wrapped.isServerUnreachable())
    }

    @Test
    fun httpError_isNotUnreachable() {
        val body = "".toResponseBody("application/json".toMediaTypeOrNull())
        assertFalse(HttpException(Response.error<Any>(500, body)).isServerUnreachable())
        assertFalse(HttpException(Response.error<Any>(404, body)).isServerUnreachable())
    }

    @Test
    fun otherFailures_areNotUnreachable() {
        assertFalse(IllegalStateException("bug").isServerUnreachable())
        assertFalse(CancellationException("cancelled").isServerUnreachable())
        assertFalse(CancellationException("cancelled", IOException()).isServerUnreachable())
    }
}
