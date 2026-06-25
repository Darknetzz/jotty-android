package com.jotty.android.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jotty.android.R

@Composable
fun ConfirmDeleteDialog(
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.confirm_delete_title),
) {
    val errorColor = MaterialTheme.colorScheme.error
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete), color = errorColor)
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
fun EditDropdownMenuItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.edit)
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(Icons.Default.Edit, contentDescription = label)
        },
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
fun RenameDropdownMenuItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.rename)
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(Icons.Default.Edit, contentDescription = label)
        },
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
fun DeleteDropdownMenuItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val errorColor = MaterialTheme.colorScheme.error
    val label = stringResource(R.string.delete)
    DropdownMenuItem(
        text = { Text(label, color = errorColor) },
        leadingIcon = {
            Icon(Icons.Default.Delete, contentDescription = label, tint = errorColor)
        },
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
fun ConfirmDiscardPendingSyncDialog(
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val errorColor = MaterialTheme.colorScheme.error
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.discard_pending_sync_title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.discard_pending_sync), color = errorColor)
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
fun DiscardPendingSyncDropdownMenuItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val errorColor = MaterialTheme.colorScheme.error
    val label = stringResource(R.string.discard_pending_sync)
    DropdownMenuItem(
        text = { Text(label, color = errorColor) },
        leadingIcon = {
            Icon(Icons.Default.SyncProblem, contentDescription = label, tint = errorColor)
        },
        onClick = onClick,
        modifier = modifier,
    )
}
