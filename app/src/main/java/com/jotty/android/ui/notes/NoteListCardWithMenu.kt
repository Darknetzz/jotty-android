package com.jotty.android.ui.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jotty.android.R
import com.jotty.android.data.api.Note
import com.jotty.android.ui.common.ArchiveDropdownMenuItem
import com.jotty.android.ui.common.ConfirmDeleteDialog
import com.jotty.android.ui.common.DeleteDropdownMenuItem
import com.jotty.android.ui.common.ShareDropdownMenuItem
import com.jotty.android.util.isArchivedCategory
import com.jotty.android.util.ListDateFormat

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NoteListCardWithMenu(
    note: Note,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    showShare: Boolean = false,
    onShare: () -> Unit = {},
    showPreview: Boolean = true,
    previewMaxLines: Int = 2,
    showDates: Boolean = true,
    showCategories: Boolean = true,
    listDateFormat: ListDateFormat = ListDateFormat.DATE,
    showPendingSync: Boolean = false,
    isUnlockedInSession: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val displayTitle = note.title.ifBlank { stringResource(R.string.untitled) }
    val isArchived = isArchivedCategory(note.category)

    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            message = stringResource(R.string.delete_note_confirm, displayTitle),
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
        )
    }

    Box(modifier = modifier.fillMaxWidth()) {
        NoteCard(
            note = note,
            onClick = onClick,
            onLongClick = { menuExpanded = true },
            showPreview = showPreview,
            previewMaxLines = previewMaxLines,
            showDates = showDates,
            showCategories = showCategories,
            listDateFormat = listDateFormat,
            showPendingSync = showPendingSync,
            isUnlockedInSession = isUnlockedInSession,
        )
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            if (showShare) {
                ShareDropdownMenuItem(
                    labelRes = R.string.share_note,
                    onClick = {
                        menuExpanded = false
                        onShare()
                    },
                )
            }
            ArchiveDropdownMenuItem(
                isArchived = isArchived,
                onClick = {
                    menuExpanded = false
                    onArchive()
                },
            )
            DeleteDropdownMenuItem(
                onClick = {
                    menuExpanded = false
                    showDeleteConfirm = true
                },
            )
        }
    }
}
