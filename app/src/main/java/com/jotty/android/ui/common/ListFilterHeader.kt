package com.jotty.android.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.util.JOTTY_ARCHIVE_CATEGORY

@Composable
fun ListFilterHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchPlaceholderRes: Int,
    sortOption: ListSortOption,
    onSortSelect: (ListSortOption) -> Unit,
    categories: List<String>,
    selectedCategory: String?,
    onClearCategoryFilter: () -> Unit,
    onCategoryToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    categoryChipPadding: PaddingValues = PaddingValues(bottom = 8.dp),
) {
    val filterSelectedDesc = stringResource(R.string.cd_filter_selected)
    val filterNotSelectedDesc = stringResource(R.string.cd_filter_not_selected)

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(searchPlaceholderRes)) },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = stringResource(R.string.cd_search),
                )
            },
            singleLine = true,
        )
        SortMenuButton(
            current = sortOption,
            onSelect = onSortSelect,
        )
    }
    if (categories.isNotEmpty()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = categoryChipPadding,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                val allSelected = selectedCategory == null
                FilterChip(
                    selected = allSelected,
                    onClick = onClearCategoryFilter,
                    label = {
                        Text(
                            stringResource(R.string.all_categories),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier =
                        Modifier.semantics {
                            stateDescription =
                                if (allSelected) filterSelectedDesc else filterNotSelectedDesc
                        },
                )
            }
            item {
                val archivedSelected = selectedCategory == JOTTY_ARCHIVE_CATEGORY
                FilterChip(
                    selected = archivedSelected,
                    onClick = { onCategoryToggle(JOTTY_ARCHIVE_CATEGORY) },
                    label = {
                        Text(
                            stringResource(R.string.category_archived),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier =
                        Modifier.semantics {
                            stateDescription =
                                if (archivedSelected) filterSelectedDesc else filterNotSelectedDesc
                        },
                )
            }
            items(categories.filter { !it.equals(JOTTY_ARCHIVE_CATEGORY, true) }, key = { it }) { cat ->
                val catSelected = selectedCategory == cat
                FilterChip(
                    selected = catSelected,
                    onClick = { onCategoryToggle(cat) },
                    label = {
                        Text(
                            cat,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier =
                        Modifier.semantics {
                            stateDescription =
                                if (catSelected) filterSelectedDesc else filterNotSelectedDesc
                        },
                )
            }
        }
    }
}
