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
 * get the `x-api-key` header (and optional [customHeaders]) so server-hosted images can load
 * when the server or reverse proxy requires them.
 * Loaders are cached per (baseUrl, apiKey, customHeaders, capabilitiesKey) using application context.
 */
fun createNoteImageLoader(
    context: Context,
    baseUrl: String?,
    apiKey: String?,
    capabilitiesKey: String? = null,
    customHeaders: Map<String, String> = emptyMap(),
): ImageLoader {
    if (baseUrl.isNullOrBlank() || apiKey.isNullOrBlank()) {
        return ImageLoader.Builder(context.applicationContext).build()
    }
    val normalizedBase = ApiClient.normalizeBaseUrl(baseUrl)
    val headers = CustomHttpHeaders.normalize(customHeaders)
    val headersKey = headers.entries.joinToString("|") { "${it.key}=${it.value}" }
    val key = "$normalizedBase|$apiKey|${capabilitiesKey.orEmpty()}|$headersKey"
    loaderCache[key]?.let { return it }
    val client =
        createNoteImageAuthClient(baseUrl, apiKey, capabilitiesKey, headers)
            ?: return ImageLoader.Builder(context.applicationContext).build()
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

/** OkHttp client that attaches [x-api-key] and optional [customHeaders] for Jotty-hosted note images. */
fun createNoteImageAuthClient(
    baseUrl: String?,
    apiKey: String?,
    capabilitiesKey: String? = null,
    customHeaders: Map<String, String> = emptyMap(),
): OkHttpClient? {
    if (baseUrl.isNullOrBlank() || apiKey.isNullOrBlank()) return null
    val normalizedBase = ApiClient.normalizeBaseUrl(baseUrl)
    val headers = CustomHttpHeaders.normalize(customHeaders)
    val instanceKey = capabilitiesKey
    val jottyOrigin =
        try {
            URI(normalizedBase)
        } catch (_: Exception) {
            return null
        }
    return OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val requestUrl = request.url
            val newRequest =
                if (shouldAttachApiKey(requestUrl, jottyOrigin)) {
                    val builder = request.newBuilder().addHeader(HEADER_API_KEY, apiKey)
                    CustomHttpHeaders.applyTo(builder, headers)
                    builder.build()
                } else {
                    request
                }
            val response = chain.proceed(newRequest)
            if (isJottyMediaPath(requestUrl.encodedPath)) {
                if (response.isSuccessful) {
                    if (!instanceKey.isNullOrBlank()) {
                        ServerCapabilities.clearPrivateImagesAuthBlocked(instanceKey)
                    }
                } else if (
                    !instanceKey.isNullOrBlank() &&
                    ServerCapabilities.isPrivateImagesAuthBlockedHttpCode(response.code)
                ) {
                    ServerCapabilities.markPrivateImagesAuthBlocked(instanceKey)
                }
            }
            response
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
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
