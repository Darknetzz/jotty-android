package com.jotty.android.ui.notes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import com.jotty.android.R
import com.jotty.android.util.convertHtmlColorSpans
import com.jotty.android.util.convertHtmlImagesToMarkdown
import com.jotty.android.util.convertHtmlTablesToGfm
import dev.jeziellago.compose.markdowntext.MarkdownText

/** Reader text scale (font multiplier) for note content; provided from app-level settings. */
val LocalReaderTextScale = compositionLocalOf { 1.0f }

@Composable
internal fun NoteView(
    content: String,
    imageLoader: ImageLoader? = null,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current
    val textScale = LocalReaderTextScale.current
    val displayMarkdown =
        remember(content) {
            convertHtmlTablesToGfm(
                convertHtmlImagesToMarkdown(
                    convertHtmlColorSpans(content),
                ),
            )
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        if (content.isNotBlank()) {
            MarkdownText(
                markdown = displayMarkdown,
                modifier = Modifier.fillMaxWidth(),
                style =
                    MaterialTheme.typography.bodyLarge.let { base ->
                        base.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = base.fontSize * textScale,
                            lineHeight = base.lineHeight * textScale,
                        )
                    },
                syntaxHighlightColor = MaterialTheme.colorScheme.surfaceVariant,
                syntaxHighlightTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                imageLoader = imageLoader,
                onLinkClicked = { url -> uriHandler.openUri(url) },
            )
        } else {
            Text(
                text = stringResource(R.string.no_content),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
