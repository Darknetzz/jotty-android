package com.jotty.android.ui.notes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.data.api.API_CATEGORY_UNCATEGORIZED
import com.jotty.android.data.api.Note
import com.jotty.android.data.encryption.NoteEncryption
import com.jotty.android.ui.common.NoteMetadataRow
import com.jotty.android.util.formatNoteDate
import com.jotty.android.util.stripInvisibleFromEdges

@Composable
internal fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    showPreview: Boolean = true,
    syncStatusLabel: String? = null,
) {
    val titleText = remember(note.title) { stripInvisibleFromEdges(note.title) }
    val strippedContent = remember(note.content) { stripInvisibleFromEdges(note.content) }
    val isEncrypted =
        remember(note.encrypted, note.content) {
            note.encrypted == true || NoteEncryption.isEncrypted(note.content)
        }
    val contentPreview =
        remember(strippedContent, isEncrypted) {
            if (isEncrypted || strippedContent.isBlank()) {
                null
            } else {
                strippedContent.take(100) + if (strippedContent.length > 100) "\u2026" else ""
            }
        }
    val updatedAtText = remember(note.updatedAt) { formatNoteDate(note.updatedAt) }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (isEncrypted) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = stringResource(R.string.encrypted),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.encrypted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (showPreview && contentPreview != null) {
                Text(
                    text = contentPreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 2,
                )
            }
            NoteMetadataRow(
                modifier = Modifier.padding(top = 6.dp),
                syncStatusLabel = syncStatusLabel,
                category =
                    note.category.takeIf {
                        it.isNotBlank() && it != API_CATEGORY_UNCATEGORIZED
                    },
                updatedAtText = note.updatedAt.takeIf { it.isNotBlank() }?.let { updatedAtText },
            )
        }
    }
}
