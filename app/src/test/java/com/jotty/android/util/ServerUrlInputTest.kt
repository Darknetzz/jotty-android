package com.jotty.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerUrlInputTest {
    @Test
    fun parseServerUrl_splitsHttps() {
        val parts = parseServerUrl("https://jotty.example.com/path")
        assertEquals(ServerUrlScheme.HTTPS, parts.scheme)
        assertEquals("jotty.example.com/path", parts.hostAndPath)
    }

    @Test
    fun parseServerUrl_splitsHttp() {
        val parts = parseServerUrl("http://192.168.1.10:8080")
        assertEquals(ServerUrlScheme.HTTP, parts.scheme)
        assertEquals("192.168.1.10:8080", parts.hostAndPath)
    }

    @Test
    fun parseServerUrl_defaultsToHttpsWhenMissingScheme() {
        val parts = parseServerUrl("jotty.example.com")
        assertEquals(ServerUrlScheme.HTTPS, parts.scheme)
        assertEquals("jotty.example.com", parts.hostAndPath)
    }

    @Test
    fun combineServerUrl_joinsSchemeAndHost() {
        assertEquals(
            "https://jotty.example.com",
            combineServerUrl(ServerUrlScheme.HTTPS, "jotty.example.com"),
        )
        assertEquals("", combineServerUrl(ServerUrlScheme.HTTPS, "  "))
    }

    @Test
    fun extractSchemeFromTypedHost_movesPrefixToScheme() {
        val (scheme, host) = extractSchemeFromTypedHost("https://jotty.example.com")
        assertEquals(ServerUrlScheme.HTTPS, scheme)
        assertEquals("jotty.example.com", host)
    }

    @Test
    fun extractSchemeFromTypedHost_leavesHostUntouchedWithoutPrefix() {
        val (scheme, host) = extractSchemeFromTypedHost("jotty.example.com")
        assertEquals(null, scheme)
        assertEquals("jotty.example.com", host)
    }

    @Test
    fun browserUrlFromServerUrl_trimsTrailingSlash() {
        assertEquals("https://jotty.example.com", browserUrlFromServerUrl("https://jotty.example.com/"))
    }
}
