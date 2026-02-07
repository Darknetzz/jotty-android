package com.jotty.android.data.api

import com.jotty.android.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val HEADER_API_KEY = "x-api-key"

    fun create(baseUrl: String, apiKey: String): JottyApi {
        val normalizedBase = normalizeBaseUrl(baseUrl)

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader(HEADER_API_KEY, apiKey)
                        .build()
                )
            }
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BODY
                        }
                    )
                }
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("$normalizedBase/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(JottyApi::class.java)
    }

    /**
     * Normalizes base URL for API: trims trailing slash, adds https if no scheme.
     * Visible for testing.
     */
    internal fun normalizeBaseUrl(baseUrl: String): String =
        baseUrl.trimEnd('/').let {
            if (!it.startsWith("http")) "https://$it" else it
        }
}
