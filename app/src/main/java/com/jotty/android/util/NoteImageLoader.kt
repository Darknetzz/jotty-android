package com.jotty.android.util

import android.content.Context
import coil.ImageLoader
import com.jotty.android.data.api.ApiClient
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private val loaderCache = ConcurrentHashMap<String, ImageLoader>()

/**
 * Builds a Coil [ImageLoader] for use in note Markdown (e.g. `![alt](url)`).
 * When [baseUrl] and [apiKey] are set, requests to the same host as the Jotty server
 * get the `x-api-key` header so authenticated image URLs (e.g. Jotty attachments) load.
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
    val jottyHost = try {
        java.net.URL(normalizedBase).host
    } catch (_: Exception) {
        return ImageLoader.Builder(context.applicationContext).build()
    }
    val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            val newRequest = if (url.host == jottyHost) {
                request.newBuilder().addHeader("x-api-key", apiKey).build()
            } else {
                request
            }
            chain.proceed(newRequest)
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    val loader = ImageLoader.Builder(context.applicationContext)
        .okHttpClient(client)
        .build()
    loaderCache[key] = loader
    return loader
}
