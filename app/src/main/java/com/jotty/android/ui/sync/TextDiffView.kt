package com.jotty.android.ui.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.util.DiffKind
import com.jotty.android.util.DiffLine
import com.jotty.android.util.computeLineDiff

/** Line + word highlight colors for one diff side. */
private data class DiffColors(
    val line: Color,
    val word: Color,
)

@Composable
fun TextDiffView(
    oldText: String,
    newText: String,
    modifier: Modifier = Modifier,
) {
    val lines = remember(oldText, newText) { computeLineDiff(oldText, newText) }
    // Fixed green/red so additions read as "added" regardless of the accent (primary may be purple).
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val added =
        if (dark) {
            DiffColors(line = Color(0xFF1B4332), word = Color(0xFF2D6A4F))
        } else {
            DiffColors(line = Color(0xFFDDF4E4), word = Color(0xFFA7E3B8))
        }
    val removed =
        if (dark) {
            DiffColors(line = Color(0xFF4A1C1C), word = Color(0xFF7A2E2E))
        } else {
            DiffColors(line = Color(0xFFFBE1E1), word = Color(0xFFF2B3B3))
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
    ) {
        if (lines.isEmpty()) {
            Text(
                text = stringResource(R.string.sync_diff_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
        } else {
            lines.forEach { line ->
                val colors =
                    when (line.kind) {
                        DiffKind.ADDED -> added
                        DiffKind.REMOVED -> removed
                        DiffKind.EQUAL -> null
                    }
                Text(
                    text = diffLineText(line, colors?.word),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color =
                        if (line.kind == DiffKind.EQUAL) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(colors?.line ?: MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

private fun diffLineText(
    line: DiffLine,
    wordHighlight: Color?,
): AnnotatedString {
    val prefix =
        when (line.kind) {
            DiffKind.ADDED -> "+ "
            DiffKind.REMOVED -> "- "
            DiffKind.EQUAL -> "  "
        }
    val segments = line.segments
    if (segments == null || wordHighlight == null) return AnnotatedString(prefix + line.text)
    return buildAnnotatedString {
        append(prefix)
        segments.forEach { seg ->
            if (seg.changed) {
                withStyle(SpanStyle(background = wordHighlight, fontWeight = FontWeight.Bold)) { append(seg.text) }
            } else {
                append(seg.text)
            }
        }
    }
}
