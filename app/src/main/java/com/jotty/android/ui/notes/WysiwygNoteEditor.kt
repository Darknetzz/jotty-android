package com.jotty.android.ui.notes

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
    onEditorWebView: (android.webkit.WebView?) -> Unit = {},
    onEditorBridge: (WysiwygEditorBridge?) -> Unit = {},
    jottyServerUrl: String? = null,
    apiKey: String? = null,
    serverCapabilitiesKey: String? = null,
    modifier: Modifier = Modifier,
) {
    var editorWebView by remember { mutableStateOf<WebView?>(null) }
    var editorBridge by remember { mutableStateOf<WysiwygEditorBridge?>(null) }
    var formatState by remember(contentReloadKey) { mutableStateOf(WysiwygFormatState()) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showImageDialog by remember { mutableStateOf(false) }
    var showTableDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var tableRows by remember { mutableStateOf("2") }
    var tableCols by remember { mutableStateOf("2") }
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
            state = formatState,
            onCommand = { script ->
                editorWebView?.evaluateJavascript(script) {
                    refreshWysiwygFormatState(editorWebView) { formatState = it }
                }
            },
            onInsertTable = { showTableDialog = true },
        )
        WysiwygWebEditor(
            htmlContent = editorHtml,
            contentReloadKey = contentReloadKey,
            backgroundColor = surfaceColor,
            textColor = textColor,
            borderColor = borderColor,
            onContentChange = onContentChange,
            onFormatStateChange = { formatState = it },
            onInsertLinkRequested = {
                urlInput = ""
                showLinkDialog = true
            },
            onInsertImageRequested = {
                urlInput = ""
                showImageDialog = true
            },
            onWebViewReady = {
                editorWebView = it
                onEditorWebView(it)
            },
            onBridgeReady = {
                editorBridge = it
                onEditorBridge(it)
            },
            jottyServerUrl = jottyServerUrl,
            apiKey = apiKey,
            serverCapabilitiesKey = serverCapabilitiesKey,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .defaultMinSize(minHeight = 160.dp),
        )
    }

    if (showLinkDialog) {
        WysiwygUrlInsertDialog(
            titleRes = R.string.wysiwyg_insert_link_title,
            hintRes = R.string.wysiwyg_insert_link_hint,
            url = urlInput,
            onUrlChange = { urlInput = it },
            onDismiss = { showLinkDialog = false },
            onConfirm = { url ->
                showLinkDialog = false
                val escaped = escapeForJsString(url.trim())
                editorWebView?.evaluateJavascript("insertLinkWithUrl($escaped);", null)
            },
        )
    }
    if (showImageDialog) {
        WysiwygUrlInsertDialog(
            titleRes = R.string.wysiwyg_insert_image_title,
            hintRes = R.string.wysiwyg_insert_image_hint,
            url = urlInput,
            onUrlChange = { urlInput = it },
            onDismiss = { showImageDialog = false },
            onConfirm = { url ->
                showImageDialog = false
                val escaped = escapeForJsString(url.trim())
                editorWebView?.evaluateJavascript("insertImageWithUrl($escaped);", null)
            },
        )
    }
    if (showTableDialog) {
        WysiwygTableInsertDialog(
            rows = tableRows,
            cols = tableCols,
            onRowsChange = { tableRows = it },
            onColsChange = { tableCols = it },
            onDismiss = { showTableDialog = false },
            onConfirm = { rows, cols ->
                showTableDialog = false
                editorWebView?.evaluateJavascript("insertTable($rows,$cols);", null)
            },
        )
    }
}

@Composable
private fun WysiwygTableInsertDialog(
    rows: String,
    cols: String,
    onRowsChange: (String) -> Unit,
    onColsChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val rowCount = rows.toIntOrNull()?.coerceIn(1, 10) ?: 2
    val colCount = cols.toIntOrNull()?.coerceIn(1, 8) ?: 2
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wysiwyg_insert_table_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = rows,
                    onValueChange = { onRowsChange(it.filter { ch -> ch.isDigit() }.take(2)) },
                    label = { Text(stringResource(R.string.wysiwyg_table_rows)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = cols,
                    onValueChange = { onColsChange(it.filter { ch -> ch.isDigit() }.take(1)) },
                    label = { Text(stringResource(R.string.wysiwyg_table_cols)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(rowCount, colCount) }) {
                Text(stringResource(R.string.wysiwyg_insert))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun WysiwygUrlInsertDialog(
    titleRes: Int,
    hintRes: Int,
    url: String,
    onUrlChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                label = { Text(stringResource(hintRes)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url) },
                enabled = url.isNotBlank(),
            ) {
                Text(stringResource(R.string.wysiwyg_insert))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun WysiwygFormatToolbar(
    state: WysiwygFormatState,
    onCommand: (String) -> Unit,
    onInsertTable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
        ) {
            WysiwygToolbarButton("undoEdit()", Icons.AutoMirrored.Filled.Undo, R.string.wysiwyg_undo, selected = false, onCommand)
            WysiwygToolbarButton("redoEdit()", Icons.AutoMirrored.Filled.Redo, R.string.wysiwyg_redo, selected = false, onCommand)
            WysiwygToolbarButton("cmd('bold')", Icons.Default.FormatBold, R.string.md_bold, state.bold, onCommand)
            WysiwygToolbarButton("cmd('italic')", Icons.Default.FormatItalic, R.string.md_italic, state.italic, onCommand)
            WysiwygToolbarButton("cmd('underline')", Icons.Default.FormatUnderlined, R.string.md_underline, state.underline, onCommand)
            WysiwygToolbarButton("cmd('strikeThrough')", Icons.Default.StrikethroughS, R.string.md_strikethrough, state.strikeThrough, onCommand)
            WysiwygToolbarButton("insertCode()", Icons.Default.Code, R.string.md_code, state.code, onCommand)
            WysiwygToolbarButton("cmd('formatBlock','H1')", Icons.Default.Title, R.string.md_heading_h1, state.heading1, onCommand)
            WysiwygToolbarButton("cmd('formatBlock','H2')", Icons.Default.Title, R.string.md_heading, state.heading2, onCommand)
            WysiwygToolbarButton("cmd('formatBlock','H3')", Icons.Default.Title, R.string.md_heading_h3, state.heading3, onCommand)
            WysiwygToolbarButton(
                "cmd('insertUnorderedList')",
                Icons.AutoMirrored.Filled.FormatListBulleted,
                R.string.md_list,
                state.unorderedList,
                onCommand,
            )
            WysiwygToolbarButton(
                "cmd('insertOrderedList')",
                Icons.Default.FormatListNumbered,
                R.string.md_numbered_list,
                state.orderedList,
                onCommand,
            )
            WysiwygToolbarButton("insertTaskList()", Icons.Default.CheckBox, R.string.md_task_list, selected = false, onCommand)
            WysiwygToolbarButton("cmd('formatBlock','blockquote')", Icons.Default.FormatQuote, R.string.md_quote, state.blockquote, onCommand)
            WysiwygToolbarButton("insertLink()", Icons.Default.Link, R.string.md_link, state.link, onCommand)
            WysiwygToolbarButton("insertImage()", Icons.Default.Image, R.string.md_image, selected = false, onCommand)
            val tableLabel = stringResource(R.string.md_table)
            IconButton(onClick = onInsertTable) {
                Icon(Icons.Default.TableChart, contentDescription = tableLabel)
            }
        }
    }
}

@Composable
private fun WysiwygToolbarButton(
    script: String,
    icon: ImageVector,
    contentDescriptionRes: Int,
    selected: Boolean,
    onCommand: (String) -> Unit,
) {
    val label = stringResource(contentDescriptionRes)
    val filterSelectedDesc = stringResource(R.string.cd_filter_selected)
    val filterNotSelectedDesc = stringResource(R.string.cd_filter_not_selected)
    val backgroundColor =
        if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        }
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    IconButton(
        onClick = {
            if (script.isNotBlank()) {
                onCommand(script)
            } else {
                onCommand("")
            }
        },
        modifier =
            Modifier
                .clip(CircleShape)
                .background(backgroundColor)
                .semantics {
                    stateDescription = if (selected) filterSelectedDesc else filterNotSelectedDesc
                },
        colors = IconButtonDefaults.iconButtonColors(contentColor = contentColor),
    ) {
        Icon(icon, contentDescription = label)
    }
}

private fun refreshWysiwygFormatState(
    webView: WebView?,
    onState: (WysiwygFormatState) -> Unit,
) {
    webView?.evaluateJavascript("getFormatState();") { result ->
        val json = parseWebViewJsonResult(result)
        if (json != null) {
            onState(parseWysiwygFormatStateJson(json))
        }
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
    onFormatStateChange: (WysiwygFormatState) -> Unit,
    onInsertLinkRequested: () -> Unit,
    onInsertImageRequested: () -> Unit,
    onWebViewReady: (WebView) -> Unit,
    onBridgeReady: (WysiwygEditorBridge) -> Unit,
    jottyServerUrl: String? = null,
    apiKey: String? = null,
    serverCapabilitiesKey: String? = null,
    modifier: Modifier = Modifier,
) {
    val documentHtml =
        remember(contentReloadKey, htmlContent, backgroundColor, textColor, borderColor) {
            buildWysiwygEditorDocument(htmlContent, backgroundColor, textColor, borderColor)
        }
    val bridge =
        remember(contentReloadKey) {
            WysiwygEditorBridge(
                object : WysiwygEditorBridge.Listener {
                    override fun onContentChanged(html: String) {
                        onContentChange(html)
                    }

                    override fun onFormatStateChanged(json: String) {
                        onFormatStateChange(parseWysiwygFormatStateJson(json))
                    }

                    override fun onInsertLinkRequested() {
                        onInsertLinkRequested()
                    }

                    override fun onInsertImageRequested() {
                        onInsertImageRequested()
                    }
                },
            )
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
                    onBridgeReady(bridge)
                    val pageFinished: (WebView?) -> Unit = { view ->
                        bridge.endLoad()
                        view?.requestFocus()
                    }
                    webViewClient =
                        if (!jottyServerUrl.isNullOrBlank() && !apiKey.isNullOrBlank()) {
                            WysiwygAuthWebViewClient(
                                baseUrl = jottyServerUrl,
                                apiKey = apiKey,
                                capabilitiesKey = serverCapabilitiesKey,
                                onPageFinished = pageFinished,
                            )
                        } else {
                            object : WebViewClient() {
                                override fun onPageFinished(
                                    view: WebView?,
                                    url: String?,
                                ) {
                                    pageFinished(view)
                                }
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

/**
 * Reads WYSIWYG HTML for save. Uses [WysiwygEditorBridge.deliverContentSnapshot] so large bodies
 * are not returned through evaluateJavascript JSON (which can mangle or truncate HTML).
 * When the user did not edit in the visual editor, [sessionContent] is returned unchanged.
 */
internal fun flushWysiwygContentForSave(
    webView: WebView?,
    bridge: WysiwygEditorBridge?,
    sessionContent: String,
    callback: (String) -> Unit,
) {
    if (webView == null || bridge == null || !bridge.wasEditedByUser()) {
        callback(sessionContent)
        return
    }
    bridge.requestContentSnapshot { html ->
        callback(html)
    }
    webView.evaluateJavascript("AndroidBridge.deliverContentSnapshot(getContent());", null)
}
