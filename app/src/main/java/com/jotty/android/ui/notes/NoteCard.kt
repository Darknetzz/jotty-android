package com.jotty.android.ui.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.data.api.API_CATEGORY_UNCATEGORIZED
import com.jotty.android.data.api.Note
import com.jotty.android.data.encryption.NoteEncryption
import com.jotty.android.ui.common.NoteMetadataRow
import com.jotty.android.util.ListDateFormat
import com.jotty.android.util.formatNoteListDate
import com.jotty.android.util.stripInvisibleFromEdges

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    showPreview: Boolean = true,
    previewMaxLines: Int = 2,
    showDates: Boolean = true,
    showCategories: Boolean = true,
    listDateFormat: ListDateFormat = ListDateFormat.DATE,
    showPendingSync: Boolean = false,
    isUnlockedInSession: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val titleText = remember(note.title) { stripInvisibleFromEdges(note.title) }
    val strippedContent = remember(note.content) { stripInvisibleFromEdges(note.content) }
    val isEncrypted =
        remember(note.encrypted, note.content) {
            note.encrypted == true || NoteEncryption.isEncrypted(note.content)
        }
    val effectivePreviewLines = previewMaxLines.coerceIn(0, 4)
    val previewCharLimit = (effectivePreviewLines * 50).coerceAtLeast(if (effectivePreviewLines > 0) 50 else 0)
    val contentPreview =
        remember(strippedContent, isEncrypted, previewCharLimit, effectivePreviewLines) {
            if (effectivePreviewLines <= 0 || isEncrypted || strippedContent.isBlank()) {
                null
            } else {
                strippedContent.take(previewCharLimit) +
                    if (strippedContent.length > previewCharLimit) "\u2026" else ""
            }
        }
    val updatedAtText =
        remember(note.updatedAt, listDateFormat) {
            formatNoteListDate(context, note.updatedAt, listDateFormat)
        }
    val cardModifier =
        modifier.fillMaxWidth().then(
            if (onLongClick != null) {
                Modifier.combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
            } else {
                Modifier
            },
        )

    if (onLongClick != null) {
        Card(
            modifier = cardModifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            NoteCardContent(
                titleText = titleText,
                isEncrypted = isEncrypted,
                isUnlockedInSession = isUnlockedInSession,
                showPreview = showPreview,
                previewMaxLines = effectivePreviewLines,
                contentPreview = contentPreview,
                showPendingSync = showPendingSync,
                note = note,
                updatedAtText = updatedAtText,
                showDates = showDates,
                showCategories = showCategories,
            )
        }
    } else {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            NoteCardContent(
                titleText = titleText,
                isEncrypted = isEncrypted,
                isUnlockedInSession = isUnlockedInSession,
                showPreview = showPreview,
                previewMaxLines = effectivePreviewLines,
                contentPreview = contentPreview,
                showPendingSync = showPendingSync,
                note = note,
                updatedAtText = updatedAtText,
                showDates = showDates,
                showCategories = showCategories,
            )
        }
    }
}

@Composable
private fun NoteCardContent(
    titleText: String,
    isEncrypted: Boolean,
    isUnlockedInSession: Boolean,
    showPreview: Boolean,
    previewMaxLines: Int,
    contentPreview: String?,
    showPendingSync: Boolean,
    note: Note,
    updatedAtText: String,
    showDates: Boolean,
    showCategories: Boolean,
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
                if (isUnlockedInSession) {
                    Icon(
                        Icons.Default.LockOpen,
                        contentDescription = stringResource(R.string.unlocked),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.unlocked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
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
            }
        } else if (showPreview && previewMaxLines > 0 && contentPreview != null) {
            Text(
                text = contentPreview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = previewMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        NoteMetadataRow(
            modifier = Modifier.padding(top = 6.dp),
            showPendingSync = showPendingSync,
            category =
                if (showCategories) {
                    note.category.takeIf {
                        it.isNotBlank() && it != API_CATEGORY_UNCATEGORIZED
                    }
                } else {
                    null
                },
            updatedAtText =
                if (showDates && note.updatedAt.isNotBlank()) {
                    updatedAtText
                } else {
                    null
                },
        )
    }
}
