package com.jotty.android.ui.common

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.jotty.android.R
import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.Note

/** Sort orders shared by the notes and checklists lists. */
enum class ListSortOption(val key: String, @StringRes val labelRes: Int) {
    UPDATED("updated", R.string.sort_updated),
    CREATED("created", R.string.sort_created),
    TITLE("title", R.string.sort_title),
    ;

    companion object {
        fun fromKey(key: String?): ListSortOption = entries.firstOrNull { it.key == key } ?: UPDATED
    }
}

/** ISO-8601 UTC timestamps sort correctly as plain strings; newest first for date sorts. */
@JvmName("sortNotes")
fun List<Note>.sortedBy(option: ListSortOption): List<Note> =
    when (option) {
        ListSortOption.UPDATED -> sortedByDescending { it.updatedAt }
        ListSortOption.CREATED -> sortedByDescending { it.createdAt }
        ListSortOption.TITLE -> sortedBy { it.title.lowercase() }
    }

@JvmName("sortChecklists")
fun List<Checklist>.sortedBy(option: ListSortOption): List<Checklist> =
    when (option) {
        ListSortOption.UPDATED -> sortedByDescending { it.updatedAt }
        ListSortOption.CREATED -> sortedByDescending { it.createdAt }
        ListSortOption.TITLE -> sortedBy { it.title.lowercase() }
    }

/** Compact sort control: an icon button that opens a single-choice menu of [ListSortOption]s. */
@Composable
fun SortMenuButton(
    current: ListSortOption,
    onSelect: (ListSortOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.cd_sort))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        ListSortOption.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(stringResource(option.labelRes)) },
                leadingIcon = {
                    RadioButton(selected = option == current, onClick = null)
                },
                onClick = {
                    onSelect(option)
                    expanded = false
                },
            )
        }
    }
}
