package com.jotty.android.ui.checklists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.data.api.isCompletedForApi
import com.jotty.android.util.ChecklistAddItemAction
import com.jotty.android.util.filterChecklistItemsByQuery
import com.jotty.android.util.resolveChecklistAddItemAction

@Composable
fun ChecklistAddItemField(
    value: String,
    onValueChange: (String) -> Unit,
    existingItems: List<ChecklistFlatItem>,
    itemSearchEnabled: Boolean,
    onAddItem: (String) -> Unit,
    onUncheckItem: (apiPath: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val matches =
        remember(value, existingItems, itemSearchEnabled) {
            if (itemSearchEnabled) {
                filterChecklistItemsByQuery(existingItems, value)
            } else {
                emptyList()
            }
        }
    val placeholderRes =
        if (itemSearchEnabled) {
            R.string.checklist_add_item_find_hint
        } else {
            R.string.add_item
        }

    fun submit() {
        if (value.isBlank()) return
        if (itemSearchEnabled) {
            val (action, match) = resolveChecklistAddItemAction(existingItems, value)
            when (action) {
                ChecklistAddItemAction.UncheckExisting -> {
                    onValueChange("")
                    match?.let { onUncheckItem(it.apiPath) }
                }
                ChecklistAddItemAction.AlreadyExists -> onValueChange("")
                ChecklistAddItemAction.AddNew -> {
                    val text = value.trim()
                    onValueChange("")
                    onAddItem(text)
                }
            }
        } else {
            val text = value.trim()
            onValueChange("")
            onAddItem(text)
        }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(stringResource(placeholderRes)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            IconButton(
                onClick = { submit() },
                enabled = value.isNotBlank(),
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
            }
        }

        if (matches.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 1.dp,
            ) {
                Column {
                    matches.forEachIndexed { index, flat ->
                        val completed = flat.item.isCompletedForApi()
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onValueChange("")
                                        if (completed) {
                                            onUncheckItem(flat.apiPath)
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector =
                                    if (completed) {
                                        Icons.Default.CheckCircle
                                    } else {
                                        Icons.Outlined.RadioButtonUnchecked
                                    },
                                contentDescription = null,
                                tint =
                                    if (completed) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                            Text(
                                text = flat.item.text,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style =
                                    if (completed) {
                                        MaterialTheme.typography.bodyMedium.copy(
                                            textDecoration = TextDecoration.LineThrough,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        MaterialTheme.typography.bodyMedium
                                    },
                            )
                            if (completed) {
                                Text(
                                    text = stringResource(R.string.checklist_add_item_match_uncheck),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.checklist_add_item_match_on_list),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (index < matches.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
