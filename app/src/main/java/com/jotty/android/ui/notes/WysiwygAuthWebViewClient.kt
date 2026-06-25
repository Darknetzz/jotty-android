package com.jotty.android.ui.notes

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.jotty.android.util.createNoteImageAuthClient
import okhttp3.Request

/**
 * Loads same-host / Jotty media images in the WYSIWYG WebView with [x-api-key] auth,
 * matching [com.jotty.android.util.createNoteImageLoader] behavior for note view mode.
 */
internal class WysiwygAuthWebViewClient(
    baseUrl: String?,
    apiKey: String?,
    capabilitiesKey: String?,
    private val onPageFinished: (WebView?) -> Unit,
) : WebViewClient() {
    private val authClient = createNoteImageAuthClient(baseUrl, apiKey, capabilitiesKey)

    override fun onPageFinished(
        view: WebView?,
        url: String?,
    ) {
        onPageFinished(view)
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val client = authClient ?: return super.shouldInterceptRequest(view, request)
        if (!request.isForMainFrame && request.url.scheme?.startsWith("http") == true) {
            return runCatching {
                val response =
                    client.newCall(
                        Request.Builder().url(request.url.toString()).build(),
                    ).execute()
                if (!response.isSuccessful) return super.shouldInterceptRequest(view, request)
                val body = response.body ?: return super.shouldInterceptRequest(view, request)
                val mime = response.header("Content-Type")?.substringBefore(';')?.trim()
                WebResourceResponse(
                    mime,
                    response.header("Content-Encoding"),
                    body.byteStream(),
                )
            }.getOrNull() ?: super.shouldInterceptRequest(view, request)
        }
        return super.shouldInterceptRequest(view, request)
    }
}
