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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.util.DiffKind
import com.jotty.android.util.computeLineDiff

@Composable
fun TextDiffView(
    oldText: String,
    newText: String,
    modifier: Modifier = Modifier,
) {
    val lines = remember(oldText, newText) { computeLineDiff(oldText, newText) }
    val addedBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    val removedBg = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
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
                val prefix =
                    when (line.kind) {
                        DiffKind.ADDED -> "+ "
                        DiffKind.REMOVED -> "- "
                        DiffKind.EQUAL -> "  "
                    }
                val bg =
                    when (line.kind) {
                        DiffKind.ADDED -> addedBg
                        DiffKind.REMOVED -> removedBg
                        DiffKind.EQUAL -> MaterialTheme.colorScheme.surface
                    }
                Text(
                    text = prefix + line.text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(bg)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}
