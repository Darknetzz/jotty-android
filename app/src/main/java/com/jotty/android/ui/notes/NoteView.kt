package com.jotty.android.ui.notes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import com.jotty.android.R
import com.jotty.android.util.prepareNoteContentForDisplay
import dev.jeziellago.compose.markdowntext.MarkdownText

/** Reader text scale (font multiplier) for note content; provided from app-level settings. */
val LocalReaderTextScale = compositionLocalOf { 1.0f }

@Composable
internal fun NoteView(
    content: String,
    imageLoader: ImageLoader? = null,
    jottyServerUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current
    val textScale = LocalReaderTextScale.current.coerceIn(0.75f, 1.5f)
    val baseStyle = MaterialTheme.typography.bodyLarge
    val onSurface = MaterialTheme.colorScheme.onSurface
    val bodyStyle =
        remember(textScale, baseStyle, onSurface) {
            val fontSize =
                if (baseStyle.fontSize.isUnspecified) {
                    16.sp
                } else {
                    (baseStyle.fontSize.value * textScale).sp
                }
            val lineHeight =
                if (baseStyle.lineHeight.isUnspecified) {
                    fontSize * 1.5f
                } else {
                    (baseStyle.lineHeight.value * textScale).sp
                }
            baseStyle.copy(
                color = onSurface,
                fontSize = fontSize,
                lineHeight = lineHeight,
            )
        }
    val displayMarkdown =
        remember(content, jottyServerUrl) {
            prepareNoteContentForDisplay(content, jottyServerUrl)
        }
    val markdownImageLoaderKey = imageLoader.hashCode()
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        if (content.isNotBlank()) {
            key(markdownImageLoaderKey) {
                MarkdownText(
                    markdown = displayMarkdown,
                    modifier = Modifier.fillMaxWidth(),
                    style = bodyStyle,
                    syntaxHighlightColor = MaterialTheme.colorScheme.surfaceVariant,
                    syntaxHighlightTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    imageLoader = imageLoader,
                    isTextSelectable = true,
                    textSelectionColors = LocalTextSelectionColors.current,
                    onLinkClicked = { url -> uriHandler.openUri(url) },
                )
            }
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
