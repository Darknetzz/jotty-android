package com.jotty.android.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.data.api.CategoryInfo
import com.jotty.android.data.api.JottyApi
import com.jotty.android.ui.common.EmptyState
import com.jotty.android.ui.common.ErrorState
import com.jotty.android.ui.common.LoadingState
import com.jotty.android.ui.common.mainScreenTabContentPadding
import com.jotty.android.util.ApiErrorHelper
import com.jotty.android.util.isArchivedCategory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCategoriesScreen(
    api: JottyApi,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var noteCategories by remember { mutableStateOf<List<CategoryInfo>>(emptyList()) }
    var checklistCategories by remember { mutableStateOf<List<CategoryInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val pullState = rememberPullToRefreshState()

    fun load(showFullLoading: Boolean) {
        scope.launch {
            if (showFullLoading) loading = true else refreshing = true
            error = null
            try {
                val response = api.getCategories()
                noteCategories =
                    response.categories.notes
                        .filterNot { isArchivedCategory(it.path) || isArchivedCategory(it.name) }
                        .sortedBy { it.path.lowercase() }
                checklistCategories =
                    response.categories.checklists
                        .filterNot { isArchivedCategory(it.path) || isArchivedCategory(it.name) }
                        .sortedBy { it.path.lowercase() }
            } catch (e: Exception) {
                error = ApiErrorHelper.userMessage(context, e)
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    LaunchedEffect(api) {
        load(showFullLoading = true)
    }

    val categories = if (selectedTab == 0) noteCategories else checklistCategories

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .mainScreenTabContentPadding(topComfortDp = 8),
    ) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.nav_notes)) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.nav_checklists)) },
            )
        }

        Text(
            text = stringResource(R.string.manage_categories_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        when {
            loading && categories.isEmpty() && error == null -> {
                LoadingState(modifier = Modifier.fillMaxSize())
            }
            error != null && categories.isEmpty() -> {
                ErrorState(
                    message = error.orEmpty(),
                    onRetry = { load(showFullLoading = true) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { load(showFullLoading = false) },
                    state = pullState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (categories.isEmpty()) {
                        EmptyState(
                            icon = Icons.Outlined.Category,
                            title = stringResource(R.string.manage_categories_empty),
                            subtitle = stringResource(R.string.manage_categories_hint),
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            items(categories, key = { it.path.ifBlank { it.name } }) { category ->
                                CategoryBrowseRow(category = category)
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBrowseRow(
    category: CategoryInfo,
    modifier: Modifier = Modifier,
) {
    val label = category.path.ifBlank { category.name }
    val indent = (category.level.coerceAtLeast(0) * 16).dp
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Text(
                stringResource(R.string.manage_categories_count, category.count),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = indent),
    )
}
