package com.jotty.android.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ControlPointDuplicate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jotty.android.R
import com.jotty.android.data.api.API_CATEGORY_UNCATEGORIZED

@Composable
fun CloneDropdownMenuItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelRes: Int = R.string.clone,
) {
    val label = stringResource(labelRes)
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(
                Icons.Outlined.ControlPointDuplicate,
                contentDescription = label,
            )
        },
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
fun CloneCategoryDialog(
    initialCategory: String,
    categorySuggestions: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    title: String = stringResource(R.string.clone_category_title),
) {
    var category by remember(initialCategory) { mutableStateOf(initialCategory) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        modifier = modifier,
        title = { Text(title) },
        text = {
            CategorySelector(
                category = category,
                onCategoryChange = { category = it },
                suggestions = categorySuggestions,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(category.ifBlank { API_CATEGORY_UNCATEGORIZED })
                },
                enabled = !loading,
            ) {
                if (loading) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(R.string.clone))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !loading,
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
