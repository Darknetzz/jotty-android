package com.jotty.android.ui.notes

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jotty.android.R
import com.jotty.android.ui.common.CategorySelector
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WysiwygNoteEditor(
    title: String,
    onTitleChange: (String) -> Unit,
    content: String,
    onContentChange: (String) -> Unit,
    contentReloadKey: String,
    category: String = "",
    onCategoryChange: ((String) -> Unit)? = null,
    categorySuggestions: List<String> = emptyList(),
    showHtmlSaveHint: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.title)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
        if (onCategoryChange != null) {
            CategorySelector(
                category = category,
                onCategoryChange = onCategoryChange,
                suggestions = categorySuggestions,
            )
        }
        if (showHtmlSaveHint) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = stringResource(R.string.note_html_save_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        WysiwygWebEditor(
            content = content,
            contentReloadKey = contentReloadKey,
            onContentChange = onContentChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WysiwygWebEditor(
    content: String,
    contentReloadKey: String,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var skipNextReload by remember { mutableStateOf(false) }

    fun applyContent(view: WebView, html: String) {
        view.evaluateJavascript("setContent(${JSONObject.quote(html)});", null)
    }

    LaunchedEffect(contentReloadKey, loaded) {
        if (loaded && !skipNextReload) {
            webView?.let { applyContent(it, content) }
        }
        skipNextReload = false
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(
                            view: WebView?,
                            url: String?,
                        ) {
                            loaded = true
                            view?.let { applyContent(it, content) }
                        }
                    }
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onContentChanged(html: String) {
                            skipNextReload = true
                            onContentChange(html)
                        }
                    },
                    "AndroidBridge",
                )
                loadUrl("file:///android_asset/wysiwyg/editor.html")
                webView = this
            }
        },
        update = { view ->
            webView = view
        },
    )
}

fun getWysiwygContent(webView: WebView?, callback: (String) -> Unit) {
    webView?.evaluateJavascript("getContent();") { result ->
        val unquoted = result?.trim()?.removeSurrounding("\"")?.replace("\\n", "\n")?.replace("\\\"", "\"")
        callback(unquoted.orEmpty())
    }
}
