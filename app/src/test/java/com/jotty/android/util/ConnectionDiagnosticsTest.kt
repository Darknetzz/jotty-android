package com.jotty.android.util

import com.google.gson.JsonSyntaxException
import com.jotty.android.R
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class ConnectionDiagnosticsTest {
    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody(null)))

    private fun title(
        t: Throwable,
        stage: ConnectionDiagnostics.Stage = ConnectionDiagnostics.Stage.Reach,
    ) = ConnectionDiagnostics.diagnose(t, stage).titleRes

    @Test
    fun networkFailures() {
        assertEquals(R.string.conn_test_title_unknown_host, title(UnknownHostException("jotty.lan")))
        assertEquals(R.string.conn_test_title_timeout, title(SocketTimeoutException()))
        assertEquals(R.string.conn_test_title_refused, title(ConnectException("Failed to connect to /10.0.0.2:1122")))
        assertEquals(R.string.conn_test_title_ssl, title(SSLHandshakeException("handshake failed")))
        assertEquals(
            R.string.conn_test_title_cleartext,
            title(java.io.IOException("CLEARTEXT communication to jotty.lan not permitted")),
        )
    }

    @Test
    fun nonJottyResponse() {
        assertEquals(R.string.conn_test_title_not_jotty, title(JsonSyntaxException("Expected BEGIN_OBJECT")))
        assertEquals(R.string.conn_test_title_not_jotty, title(http(404)))
    }

    @Test
    fun httpStatuses() {
        val auth = ConnectionDiagnostics.Stage.Authenticate
        assertEquals(R.string.conn_test_title_bad_key, title(http(401), auth))
        assertEquals(R.string.conn_test_title_forbidden, title(http(403), auth))
        assertEquals(R.string.conn_test_title_gateway, title(http(502)))
        assertEquals(R.string.conn_test_title_server_error, title(http(500), auth))
        assertEquals(R.string.conn_test_title_unexpected_status, title(http(404), auth))
    }
}
