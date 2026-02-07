package com.jotty.android.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiClientTest {

    @Test
    fun normalizeBaseUrl_adds_https_when_no_scheme() {
        assertEquals("https://jotty.example.com", ApiClient.normalizeBaseUrl("jotty.example.com"))
        assertEquals("https://example.com", ApiClient.normalizeBaseUrl("example.com"))
    }

    @Test
    fun normalizeBaseUrl_preserves_https() {
        assertEquals("https://jotty.example.com", ApiClient.normalizeBaseUrl("https://jotty.example.com"))
        assertEquals("https://jotty.example.com", ApiClient.normalizeBaseUrl("https://jotty.example.com/"))
    }

    @Test
    fun normalizeBaseUrl_preserves_http() {
        assertEquals("http://localhost:8080", ApiClient.normalizeBaseUrl("http://localhost:8080"))
        assertEquals("http://localhost:8080", ApiClient.normalizeBaseUrl("http://localhost:8080/"))
    }

    @Test
    fun normalizeBaseUrl_trims_trailing_slash() {
        assertEquals("https://jotty.example.com", ApiClient.normalizeBaseUrl("https://jotty.example.com/"))
        assertEquals("https://jotty.example.com", ApiClient.normalizeBaseUrl("https://jotty.example.com/path/"))
    }
}
