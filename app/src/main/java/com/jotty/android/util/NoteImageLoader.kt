package com.jotty.android.util

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.jotty.android.data.api.ApiClient
import okhttp3.OkHttpClient
import java.net.URI
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit

private const val MAX_CACHED_LOADERS = 6
private const val HEADER_API_KEY = "x-api-key"

private val loaderCache: MutableMap<String, ImageLoader> =
    Collections.synchronizedMap(
        object : LinkedHashMap<String, ImageLoader>(MAX_CACHED_LOADERS + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageLoader>?): Boolean = size > MAX_CACHED_LOADERS
        },
    )

/**
 * Builds a Coil [ImageLoader] for use in note Markdown (e.g. `![alt](url)`).
 * When [baseUrl] and [apiKey] are set, requests to the Jotty instance origin (or Jotty media paths)
 * get the `x-api-key` header so server-hosted images can load when the server accepts API keys.
 * Loaders are cached per (baseUrl, apiKey) using application context so caches survive config changes.
 */
fun createNoteImageLoader(
    context: Context,
    baseUrl: String?,
    apiKey: String?,
): ImageLoader {
    if (baseUrl.isNullOrBlank() || apiKey.isNullOrBlank()) {
        return ImageLoader.Builder(context.applicationContext).build()
    }
    val normalizedBase = ApiClient.normalizeBaseUrl(baseUrl)
    val key = "$normalizedBase|$apiKey"
    loaderCache[key]?.let { return it }
    val jottyOrigin =
        try {
            URI(normalizedBase)
        } catch (_: Exception) {
            return ImageLoader.Builder(context.applicationContext).build()
        }
    val client =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val requestUrl = request.url
                val newRequest =
                    if (shouldAttachApiKey(requestUrl, jottyOrigin)) {
                        request.newBuilder().addHeader(HEADER_API_KEY, apiKey).build()
                    } else {
                        request
                    }
                val response = chain.proceed(newRequest)
                if (!response.isSuccessful && isJottyMediaPath(requestUrl.encodedPath)) {
                    AppLog.w(
                        "notes",
                        "Note image HTTP ${response.code} for ${requestUrl.redactApiKey()}" +
                            " (Jotty media routes may require a server with API-key image auth or SERVE_PUBLIC_IMAGES)",
                    )
                }
                response
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    val appCtx = context.applicationContext
    val loader =
        ImageLoader.Builder(appCtx)
            .okHttpClient(client)
            .memoryCache {
                MemoryCache.Builder(appCtx)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(appCtx.cacheDir.resolve("jotty_note_images_cache"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .respectCacheHeaders(true)
            .build()
    loaderCache[key] = loader
    return loader
}

private fun shouldAttachApiKey(
    requestUrl: okhttp3.HttpUrl,
    jottyOrigin: URI,
): Boolean {
    if (isJottyMediaPath(requestUrl.encodedPath)) return true
    val originHost = jottyOrigin.host ?: return false
    if (!requestUrl.host.equals(originHost, ignoreCase = true)) return false
    return requestUrl.port == effectivePort(jottyOrigin, requestUrl.scheme)
}

private fun effectivePort(
    origin: URI,
    schemeHint: String,
): Int {
    if (origin.port >= 0) return origin.port
    return if ((origin.scheme ?: schemeHint).equals("https", ignoreCase = true)) 443 else 80
}

private fun okhttp3.HttpUrl.redactApiKey(): String = toString()
