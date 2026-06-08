package com.jotty.android.ui.notes

import android.annotation.SuppressLint
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jotty.android.R
import com.jotty.android.ui.common.CategorySelector
import com.jotty.android.util.prepareNoteContentForWysiwyg
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bridges note HTML into the WebView without embedding it in a JavaScript string.
 * Must be public to the JVM (not private) so [WebView.addJavascriptInterface] can invoke it.
 */
internal class WysiwygEditorBridge(
    private val onContentChanged: (String) -> Unit,
) {
    @Volatile
    var pendingHtml: String = ""

    private val acceptChanges = AtomicBoolean(false)
    private val userEdited = AtomicBoolean(false)

    fun beginLoad() {
        acceptChanges.set(false)
        userEdited.set(false)
    }

    fun endLoad() {
        acceptChanges.set(true)
    }

    @JavascriptInterface
    fun getInitialHtml(): String = pendingHtml

    @JavascriptInterface
    fun onContentChanged(html: String) {
        if (!acceptChanges.get()) return
        // Ignore the empty editor firing before initial content is injected.
        if (!userEdited.get() && html.isBlank() && pendingHtml.isNotBlank()) return
        userEdited.set(true)
        onContentChanged(html)
    }
}

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
    var editorWebView by remember { mutableStateOf<WebView?>(null) }
    // Snapshot HTML when entering visual mode; do not recompute when WYSIWYG sync updates [content].
    val editorHtml = remember(contentReloadKey) { prepareNoteContentForWysiwyg(content) }
    val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val borderColor = MaterialTheme.colorScheme.outlineVariant.toArgb()

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
        WysiwygFormatToolbar(
            onCommand = { script ->
                editorWebView?.evaluateJavascript(script, null)
            },
        )
        WysiwygWebEditor(
            htmlContent = editorHtml,
            contentReloadKey = contentReloadKey,
            backgroundColor = surfaceColor,
            textColor = textColor,
            borderColor = borderColor,
            onContentChange = onContentChange,
            onWebViewReady = { editorWebView = it },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        )
    }
}

@Composable
private fun WysiwygFormatToolbar(
    onCommand: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ToolbarButton("cmd('bold')", "B", onCommand)
        ToolbarButton("cmd('italic')", "I", onCommand)
        ToolbarButton("cmd('insertUnorderedList')", "•", onCommand)
        ToolbarButton("cmd('insertOrderedList')", "1.", onCommand)
        ToolbarButton("insertLink()", stringResource(R.string.wysiwyg_link), onCommand)
        ToolbarButton("insertTable()", stringResource(R.string.wysiwyg_table), onCommand)
        ToolbarButton("cmd('formatBlock','H2')", "H2", onCommand)
    }
}

@Composable
private fun ToolbarButton(
    script: String,
    label: String,
    onCommand: (String) -> Unit,
) {
    OutlinedButton(
        onClick = { onCommand(script) },
        modifier = Modifier.height(36.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WysiwygWebEditor(
    htmlContent: String,
    contentReloadKey: String,
    backgroundColor: Int,
    textColor: Int,
    borderColor: Int,
    onContentChange: (String) -> Unit,
    onWebViewReady: (WebView) -> Unit,
    modifier: Modifier = Modifier,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageReady by remember { mutableStateOf(false) }
    var skipNextReload by remember { mutableStateOf(false) }
    val bridge =
        remember(contentReloadKey) {
            WysiwygEditorBridge { html ->
                skipNextReload = true
                onContentChange(html)
            }
        }

    fun applyThemeAndContent(view: WebView) {
        bridge.pendingHtml = htmlContent
        bridge.beginLoad()
        view.evaluateJavascript(
            "setEditorTheme($backgroundColor,$textColor,$borderColor);loadInitialContent();",
        ) {
            bridge.endLoad()
        }
    }

    LaunchedEffect(contentReloadKey, htmlContent) {
        bridge.pendingHtml = htmlContent
    }

    LaunchedEffect(contentReloadKey) {
        pageReady = false
        bridge.beginLoad()
    }

    LaunchedEffect(contentReloadKey, pageReady) {
        if (pageReady && !skipNextReload) {
            webView?.let { applyThemeAndContent(it) }
        }
        skipNextReload = false
    }

    key(contentReloadKey) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(backgroundColor)
                    overScrollMode = View.OVER_SCROLL_NEVER
                    isVerticalScrollBarEnabled = false
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = false
                    bridge.pendingHtml = htmlContent
                    webViewClient =
                        object : WebViewClient() {
                            override fun onPageFinished(
                                view: WebView?,
                                url: String?,
                            ) {
                                pageReady = true
                                view?.let { applyThemeAndContent(it) }
                            }
                        }
                    addJavascriptInterface(bridge, "AndroidBridge")
                    loadUrl("file:///android_asset/wysiwyg/editor.html")
                    webView = this
                    onWebViewReady(this)
                }
            },
            update = { view ->
                webView = view
                view.setBackgroundColor(backgroundColor)
                onWebViewReady(view)
            },
        )
    }
}

fun getWysiwygContent(webView: WebView?, callback: (String) -> Unit) {
    webView?.evaluateJavascript("getContent();") { result ->
        val unquoted = result?.trim()?.removeSurrounding("\"")?.replace("\\n", "\n")?.replace("\\\"", "\"")
        callback(unquoted.orEmpty())
    }
}
