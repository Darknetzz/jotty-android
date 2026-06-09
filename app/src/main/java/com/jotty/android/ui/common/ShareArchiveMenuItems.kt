package com.jotty.android.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jotty.android.R

@Composable
fun ShareDropdownMenuItem(
    onClick: () -> Unit,
    labelRes: Int = R.string.share_checklist,
) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
fun ArchiveDropdownMenuItem(
    isArchived: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                if (isArchived) {
                    stringResource(R.string.unarchive)
                } else {
                    stringResource(R.string.archive)
                },
            )
        },
        leadingIcon = {
            Icon(
                if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                contentDescription = null,
            )
        },
        onClick = onClick,
    )
}
