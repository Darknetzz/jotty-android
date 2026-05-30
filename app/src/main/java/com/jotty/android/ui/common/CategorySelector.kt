package com.jotty.android.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jotty.android.R

/**
 * Reusable category input: a free-text field with a dropdown of existing [suggestions].
 * Users can pick an existing category or type a new one. Shared by notes and checklists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySelector(
    category: String,
    onCategoryChange: (String) -> Unit,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = category,
        onValueChange = onCategoryChange,
        label = { Text(stringResource(R.string.category)) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        trailingIcon = {
            if (suggestions.isNotEmpty()) {
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = stringResource(R.string.cd_choose_category),
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    suggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            text = { Text(suggestion) },
                            onClick = {
                                onCategoryChange(suggestion)
                                expanded = false
                            },
                        )
                    }
                }
            }
        },
    )
}
