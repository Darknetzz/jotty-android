package com.jotty.android.ui.notes

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jotty.android.R
import com.jotty.android.ui.common.CategorySelector
import com.jotty.android.util.prepareNoteContentForWysiwyg

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
                    .weight(1f)
                    .defaultMinSize(minHeight = 160.dp),
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
    val documentHtml =
        remember(contentReloadKey, htmlContent, backgroundColor, textColor, borderColor) {
            buildWysiwygEditorDocument(htmlContent, backgroundColor, textColor, borderColor)
        }
    val bridge =
        remember(contentReloadKey) {
            WysiwygEditorBridge { html -> onContentChange(html) }
        }

    key(contentReloadKey) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                WebView(context).apply {
                    layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    setBackgroundColor(backgroundColor)
                    overScrollMode = View.OVER_SCROLL_NEVER
                    isVerticalScrollBarEnabled = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = false
                    settings.allowFileAccess = true
                    bridge.attachTo(this)
                    webViewClient =
                        object : WebViewClient() {
                            override fun onPageFinished(
                                view: WebView?,
                                url: String?,
                            ) {
                                bridge.endLoad()
                                view?.requestFocus()
                            }
                        }
                    loadDataWithBaseURL(
                        "file:///android_asset/",
                        documentHtml,
                        "text/html",
                        "UTF-8",
                        null,
                    )
                    onWebViewReady(this)
                }
            },
            update = { view ->
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
