package com.jotty.android.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jotty.android.R
import com.jotty.android.data.local.NetworkConnectivityMonitor
import com.jotty.android.data.local.OfflineListSyncSource
import com.jotty.android.util.ApiErrorHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/*
 * Pieces shared by the offline-enabled notes and checklists list screens (and, for the category
 * filter, their online counterparts): sync state and user-triggered sync, the conflict snackbar,
 * the main tab top bar, and the banner + filter header above the list.
 */

/** Sync state of one offline list screen; see [rememberOfflineListSync]. */
@Stable
class OfflineListSync internal constructor(
    private val source: OfflineListSyncSource,
    private val scope: CoroutineScope,
    private val errorMessage: (Throwable) -> String,
    private val onSyncFailed: suspend (String) -> Unit,
    private val screenState: ListScreenState,
    private val isDisplayedListEmpty: State<() -> Boolean>,
    isOnlineState: State<Boolean>,
    isSyncingState: State<Boolean>,
    pendingIdsState: State<Set<String>>,
    conflictCopyCountState: State<Int>,
    private val lastAttemptState: State<Long?>,
    private val lastDurationState: State<String?>,
    private val lastErrorState: State<String?>,
) {
    val isOnline by isOnlineState
    val isSyncing by isSyncingState

    /** Ids with unsent changes, for "Pending sync" labels. */
    val pendingIds by pendingIdsState
    val conflictCopyCount by conflictCopyCountState

    /** True while a pull-to-refresh sync runs (drives the pull indicator). */
    var pullRefreshing by mutableStateOf(false)
        private set

    /**
     * Syncs now when online. [showLoading] shows the skeleton if the list is empty; a failure is
     * shown as the list error when there is nothing to show, otherwise as a snackbar.
     */
    fun requestSync(
        showLoading: Boolean = true,
        fromPull: Boolean = false,
    ) {
        scope.launch {
            if (fromPull) pullRefreshing = true
            val showSkeleton = showLoading && isDisplayedListEmpty.value()
            try {
                if (!isOnline) return@launch
                if (showSkeleton) screenState.loading = true
                screenState.errorMessage = null
                val result = source.syncNow()
                if (result.isFailure) {
                    val msg =
                        source.lastSyncError.value?.takeIf { it.isNotBlank() }
                            ?: errorMessage(result.exceptionOrNull() ?: Exception("Sync failed"))
                    onSyncFailed(msg)
                }
            } finally {
                if (showSkeleton) screenState.loading = false
                pullRefreshing = false
            }
        }
    }

    /** Top bar for the list (not the detail pane) of this tab. */
    fun topBarState(
        onManagePendingSync: () -> Unit,
        onManageCategories: () -> Unit,
        onAdd: () -> Unit,
        onNewFromTemplate: (() -> Unit)? = null,
    ): MainTabTopBarState =
        MainTabTopBarState(
            isOnline = isOnline,
            isSyncing = isSyncing,
            lastSyncAttemptEpochMs = lastAttemptState.value,
            lastSyncDurationText = lastDurationState.value,
            lastSyncError = lastErrorState.value,
            pendingSyncCount = pendingIds.size,
            onManagePendingSync = onManagePendingSync,
            onManageCategories = onManageCategories,
            onNewFromTemplate = onNewFromTemplate,
            onRefresh = { requestSync(showLoading = false) },
            onAdd = onAdd,
        )
}

/**
 * Collects [source]'s sync state and shows the conflict snackbar (whose action opens
 * [onOpenPendingSync], where conflicts are resolved).
 *
 * @param isDisplayedListEmpty whether the list on screen is empty (for the loading skeleton).
 * @param hasNoLocalItems whether there is nothing stored at all; a failed sync is then shown as the
 *   list error instead of a snackbar.
 */
@Composable
fun rememberOfflineListSync(
    source: OfflineListSyncSource,
    snackbarHostState: SnackbarHostState,
    screenState: ListScreenState,
    isDisplayedListEmpty: () -> Boolean,
    hasNoLocalItems: () -> Boolean,
    onOpenPendingSync: () -> Unit,
): OfflineListSync {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isOnline = NetworkConnectivityMonitor.isOnline.collectAsStateWithLifecycle()
    val isSyncing = source.isSyncing.collectAsStateWithLifecycle()
    val conflictsDetected by source.conflictsDetected.collectAsStateWithLifecycle()
    val pendingIdsFlow: Flow<Set<String>> = remember(source) { source.pendingSyncIdsFlow() }
    val pendingIds = pendingIdsFlow.collectAsStateWithLifecycle(initialValue = emptySet())
    val conflictCountFlow: Flow<Int> = remember(source) { source.conflictCopyCountFlow() }
    val conflictCopyCount = conflictCountFlow.collectAsStateWithLifecycle(initialValue = 0)
    val lastAttempt = source.lastSyncAttemptEpochMs.collectAsStateWithLifecycle()
    val lastDuration = source.lastSyncDurationText.collectAsStateWithLifecycle()
    val lastError = source.lastSyncError.collectAsStateWithLifecycle()
    val displayedEmpty = rememberUpdatedState(isDisplayedListEmpty)
    val noLocalItems by rememberUpdatedState(hasNoLocalItems)
    val openPendingSync by rememberUpdatedState(onOpenPendingSync)

    val conflictMsg = stringResource(R.string.sync_conflicts_detected, conflictsDetected)
    val conflictActionLabel = stringResource(R.string.view_conflicts)
    LaunchedEffect(conflictsDetected) {
        if (conflictsDetected > 0) {
            scope.launch {
                val result =
                    snackbarHostState.showSnackbar(
                        message = conflictMsg,
                        actionLabel = conflictActionLabel,
                        duration = SnackbarDuration.Long,
                    )
                if (result == SnackbarResult.ActionPerformed) openPendingSync()
                source.clearConflictNotification()
            }
        }
    }

    return remember(source, snackbarHostState, screenState) {
        OfflineListSync(
            source = source,
            scope = scope,
            errorMessage = { ApiErrorHelper.userMessage(context, it) },
            onSyncFailed = { msg ->
                if (noLocalItems()) {
                    screenState.errorMessage = msg
                } else {
                    snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
                }
            },
            screenState = screenState,
            isDisplayedListEmpty = displayedEmpty,
            isOnlineState = isOnline,
            isSyncingState = isSyncing,
            pendingIdsState = pendingIds,
            conflictCopyCountState = conflictCopyCount,
            lastAttemptState = lastAttempt,
            lastDurationState = lastDuration,
            lastErrorState = lastError,
        )
    }
}

/**
 * Restores the persisted category filter once ([load] → [onRestore]), then saves every later
 * change of [selectedCategory] with [save].
 */
@Composable
fun PersistedCategoryFilterEffect(
    selectedCategory: String?,
    load: suspend () -> String?,
    save: suspend (String?) -> Unit,
    onRestore: (String) -> Unit,
) {
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        load()?.let(onRestore)
        restored = true
    }
    LaunchedEffect(selectedCategory, restored) {
        if (restored) save(selectedCategory)
    }
}

/** Shows a snackbar for each category whose filter matched nothing stored offline. */
@Composable
fun CategoryFilterEmptySnackbarEffect(
    events: Flow<String>,
    snackbarHostState: SnackbarHostState,
) {
    val resources = LocalResources.current
    LaunchedEffect(events) {
        events.collect { category ->
            snackbarHostState.showSnackbar(resources.getString(R.string.offline_category_filter_empty, category))
        }
    }
}

/** Offline banner, search / sort / category header and conflict banner above an offline list. */
@Composable
fun OfflineListHeader(
    sync: OfflineListSync,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchPlaceholderRes: Int,
    sortOption: ListSortOption,
    onSortSelect: (ListSortOption) -> Unit,
    categories: List<String>,
    selectedCategory: String?,
    onClearCategoryFilter: () -> Unit,
    onCategoryToggle: (String) -> Unit,
    onOpenPendingSync: () -> Unit,
) {
    Column {
        OfflineConnectivityBanner(
            isOnline = sync.isOnline,
            onRetrySync = { sync.requestSync(showLoading = true) },
            modifier = Modifier.padding(bottom = 8.dp),
        )
        ListFilterHeader(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            searchPlaceholderRes = searchPlaceholderRes,
            sortOption = sortOption,
            onSortSelect = onSortSelect,
            categories = categories,
            selectedCategory = selectedCategory,
            onClearCategoryFilter = onClearCategoryFilter,
            onCategoryToggle = onCategoryToggle,
            categoryChipPadding = PaddingValues(vertical = 4.dp),
        )
        ConflictCopiesBanner(conflictCopyCount = sync.conflictCopyCount, onViewCopies = onOpenPendingSync)
        Spacer(modifier = Modifier.height(8.dp))
    }
}
