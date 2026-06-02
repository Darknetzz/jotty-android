package com.jotty.android.ui.checklists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jotty.android.R
import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.local.NetworkConnectivityMonitor
import com.jotty.android.data.local.OfflineChecklistsRepository
import com.jotty.android.data.preferences.SettingsRepository
import com.jotty.android.ui.common.ConfirmDeleteDialog
import com.jotty.android.ui.common.ConfirmDiscardPendingSyncDialog
import com.jotty.android.ui.common.ConflictCopiesBanner
import com.jotty.android.ui.common.DeleteDropdownMenuItem
import com.jotty.android.ui.common.EditDropdownMenuItem
import com.jotty.android.ui.common.ListDetailContainer
import com.jotty.android.ui.common.ListScreenContent
import com.jotty.android.ui.common.rememberStaleListWhileRefresh
import com.jotty.android.ui.common.ListSortOption
import com.jotty.android.ui.common.SortMenuButton
import com.jotty.android.ui.common.sortedBy
import com.jotty.android.util.moveChecklistItemDownRequest
import com.jotty.android.util.moveChecklistItemUpRequest
import com.jotty.android.ui.common.MainNestedScaffoldContentWindowInsets
import com.jotty.android.ui.common.MainTabTopBarState
import com.jotty.android.ui.common.OfflineConnectivityBanner
import com.jotty.android.ui.common.RegisterMainTabTopBar
import com.jotty.android.ui.common.SwipeToDeleteContainer
import com.jotty.android.ui.common.mainScreenTabContentPadding
import com.jotty.android.ui.common.rememberListScreenState
import com.jotty.android.util.ApiErrorHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OfflineEnabledChecklistsScreen(
    offlineRepository: OfflineChecklistsRepository,
    api: JottyApi,
    vmKey: String,
    settingsRepository: SettingsRepository,
    swipeToDeleteEnabled: Boolean = false,
    tabReselectToken: Int = 0,
) {
    val contentPaddingMode by settingsRepository.contentPaddingMode.collectAsStateWithLifecycle(initialValue = "comfortable")
    val contentVerticalDp = if (contentPaddingMode == "compact") 8 else 16

    val vm: OfflineEnabledChecklistsViewModel =
        viewModel(key = vmKey) {
            OfflineEnabledChecklistsViewModel(offlineRepository, api)
        }

    val checklists by offlineRepository.getChecklistsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val conflictCopies by offlineRepository.getConflictCopiesFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val isOnline by NetworkConnectivityMonitor.isOnline.collectAsStateWithLifecycle()
    val isSyncing by offlineRepository.isSyncing.collectAsStateWithLifecycle()
    val conflictsDetected by offlineRepository.conflictsDetected.collectAsStateWithLifecycle()
    val replayFailuresDetected by offlineRepository.replayFailuresDetected.collectAsStateWithLifecycle()
    val dirtyChecklistIds by offlineRepository.getDirtyChecklistIdsFlow().collectAsStateWithLifecycle(initialValue = emptySet())
    val lastSyncAttemptEpochMs by offlineRepository.lastSyncAttemptEpochMs.collectAsStateWithLifecycle()
    val lastSyncDurationText by offlineRepository.lastSyncDurationText.collectAsStateWithLifecycle()
    val lastSyncError by offlineRepository.lastSyncError.collectAsStateWithLifecycle()

    val selectedList by vm.selectedList.collectAsStateWithLifecycle()
    val showCreateDialog by vm.showCreateDialog.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by vm.selectedCategory.collectAsStateWithLifecycle()
    val checklistCategories by vm.checklistCategories.collectAsStateWithLifecycle()
    val filteredChecklists by vm.filteredChecklists.collectAsStateWithLifecycle()
    val sortKey by settingsRepository.listSortOption.collectAsStateWithLifecycle(initialValue = "updated")
    val sortOption = ListSortOption.fromKey(sortKey)
    val sortedChecklists = remember(filteredChecklists, sortOption) { filteredChecklists.sortedBy(sortOption) }

    val screenState = rememberListScreenState()
    var pullRefreshing by remember { mutableStateOf(false) }
    val listRefreshing = screenState.loading
    val checklistListDisplay = rememberStaleListWhileRefresh(sortedChecklists, listRefreshing)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailedMsg = stringResource(R.string.save_failed)
    val deleteFailedMsg = stringResource(R.string.delete_failed)
    val renameLeafOnlyMsg = stringResource(R.string.rename_leaf_only)
    val savedLocallyMsg = stringResource(R.string.saved_locally)
    val conflictMsg = stringResource(R.string.sync_conflicts_detected, conflictsDetected)
    val conflictActionLabel = stringResource(R.string.view_conflicts)
    val replayFailedMsg = stringResource(R.string.sync_replay_ops_failed, replayFailuresDetected)
    val checklistDeletedMsg = stringResource(R.string.checklist_deleted)
    val undoActionLabel = stringResource(R.string.undo)
    val pendingSyncLabel = stringResource(R.string.pending_sync)
    val discardPendingSyncDoneMsg = stringResource(R.string.discard_pending_sync_done)

    suspend fun offlineDeleteWithUndo(list: Checklist) {
        val snap = list
        val result = offlineRepository.deleteChecklist(snap.id)
        if (result.isFailure) {
            snackbarHostState.showSnackbar(deleteFailedMsg)
            return
        }
        if (selectedList?.id == snap.id) {
            vm.setSelectedList(null)
        }
        val snackbarResult =
            snackbarHostState.showSnackbar(
                message = checklistDeletedMsg,
                actionLabel = undoActionLabel,
                duration = SnackbarDuration.Short,
            )
        if (snackbarResult == SnackbarResult.ActionPerformed) {
            val undoResult = offlineRepository.recreateChecklistWithItems(snap)
            if (undoResult.isFailure) {
                snackbarHostState.showSnackbar(saveFailedMsg)
            } else if (!isOnline) {
                snackbarHostState.showSnackbar(savedLocallyMsg)
            }
        } else if (!isOnline) {
            snackbarHostState.showSnackbar(savedLocallyMsg)
        }
    }

    fun requestSync(
        showLoading: Boolean = true,
        fromPull: Boolean = false,
    ) {
        scope.launch {
            if (fromPull) pullRefreshing = true
            val showSkeleton = showLoading && sortedChecklists.isEmpty()
            try {
                if (!isOnline) return@launch
                if (showSkeleton) screenState.loading = true
                screenState.errorMessage = null
                val result = offlineRepository.syncChecklists(force = true)
                if (result.isFailure) {
                    val msg =
                        offlineRepository.lastSyncError.value?.takeIf { it.isNotBlank() }
                            ?: ApiErrorHelper.userMessage(
                                context,
                                result.exceptionOrNull() ?: Exception("Sync failed"),
                            )
                    if (checklists.isEmpty()) {
                        screenState.errorMessage = msg
                    } else {
                        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
                    }
                }
            } finally {
                if (showSkeleton) screenState.loading = false
                pullRefreshing = false
            }
        }
    }

    LaunchedEffect(conflictsDetected) {
        if (conflictsDetected > 0) {
            scope.launch {
                val result =
                    snackbarHostState.showSnackbar(
                        message = conflictMsg,
                        actionLabel = conflictActionLabel,
                        duration = SnackbarDuration.Long,
                    )
                if (result == SnackbarResult.ActionPerformed) vm.applyConflictSearchFilter()
                offlineRepository.clearConflictNotification()
            }
        }
    }

    LaunchedEffect(replayFailuresDetected) {
        if (replayFailuresDetected > 0) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = replayFailedMsg,
                    duration = SnackbarDuration.Long,
                )
                offlineRepository.clearReplayFailureNotification()
            }
        }
    }

    LaunchedEffect(isOnline, checklists) {
        vm.loadCategories(isOnline, checklists)
    }

    BackHandler(enabled = selectedList != null) { vm.setSelectedList(null) }
    LaunchedEffect(tabReselectToken) {
        if (selectedList != null) {
            vm.setSelectedList(null)
        }
    }

    // Restore the persisted category filter once, then persist subsequent changes.
    var filterRestored by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        settingsRepository.checklistsCategoryFilter.first()?.let { vm.setSelectedCategory(it) }
        filterRestored = true
    }
    LaunchedEffect(selectedCategory, filterRestored) {
        if (filterRestored) settingsRepository.setChecklistsCategoryFilter(selectedCategory)
    }

    val inChecklistDetail = selectedList != null
    RegisterMainTabTopBar(
        state =
            if (!inChecklistDetail) {
                MainTabTopBarState(
                    isOnline = isOnline,
                    isSyncing = isSyncing,
                    lastSyncAttemptEpochMs = lastSyncAttemptEpochMs,
                    lastSyncDurationText = lastSyncDurationText,
                    lastSyncError = lastSyncError,
                    onRefresh = { requestSync(showLoading = false) },
                    onAdd = { vm.setShowCreateDialog(true) },
                )
            } else {
                null
            },
        suppressMainTopBar = inChecklistDetail,
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = MainNestedScaffoldContentWindowInsets,
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .mainScreenTabContentPadding(
                    topComfortDp = if (inChecklistDetail) 0 else contentVerticalDp,
                    horizontal = if (inChecklistDetail) 0.dp else 16.dp,
                    scaffoldInnerPadding = innerPadding,
                ),
        ) {
            ListDetailContainer(
                target = selectedList,
                modifier = Modifier.fillMaxSize(),
                contentKey = { it?.id ?: "list" },
            ) { currentList ->
                if (currentList != null) {
                OfflineChecklistDetailContent(
                    checklist = currentList,
                    offlineRepository = offlineRepository,
                    categorySuggestions = checklistCategories,
                    isOnline = isOnline,
                    hasPendingSync = currentList.id in dirtyChecklistIds,
                    onRetrySync = { requestSync(showLoading = true) },
                    onBack = { vm.setSelectedList(null) },
                    onUpdate = { vm.setSelectedList(it) },
                    onDelete = {
                        scope.launch {
                            val result = offlineRepository.deleteChecklist(currentList.id)
                            if (result.isFailure) {
                                snackbarHostState.showSnackbar(deleteFailedMsg)
                            } else if (!isOnline) {
                                snackbarHostState.showSnackbar(savedLocallyMsg)
                            }
                            vm.setSelectedList(null)
                        }
                    },
                    onSaveFailed = { scope.launch { snackbarHostState.showSnackbar(saveFailedMsg) } },
                    onSavedLocally = { scope.launch { snackbarHostState.showSnackbar(savedLocallyMsg) } },
                    onRenameUnsupported = { scope.launch { snackbarHostState.showSnackbar(renameLeafOnlyMsg) } },
                    onDiscardPendingSyncFailed = {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                ApiErrorHelper.userMessage(context, it),
                            )
                        }
                    },
                    onDiscardPendingSyncDone = {
                        scope.launch { snackbarHostState.showSnackbar(discardPendingSyncDoneMsg) }
                    },
                )
                } else {
                    Column(Modifier.fillMaxSize()) {
                OfflineConnectivityBanner(
                    isOnline = isOnline,
                    onRetrySync = { requestSync(showLoading = true) },
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                // Search bar + sort
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { vm.setSearchQuery(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.search_checklists)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search)) },
                        singleLine = true,
                    )
                    SortMenuButton(
                        current = sortOption,
                        onSelect = { scope.launch { settingsRepository.setListSortOption(it.key) } },
                    )
                }

                // Category chips
                if (checklistCategories.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { vm.setSelectedCategory(null) },
                                label = {
                                    Text(
                                        stringResource(R.string.all_categories),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                        items(checklistCategories, key = { it }) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { vm.toggleCategoryChip(cat) },
                                label = {
                                    Text(cat, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                            )
                        }
                    }
                }

                ConflictCopiesBanner(
                    conflictCopyCount = conflictCopies.size,
                    onViewCopies = { vm.applyConflictSearchFilter() },
                )

                Spacer(modifier = Modifier.height(8.dp))

                ListScreenContent(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    showSkeleton = screenState.loading && checklistListDisplay.showEmpty,
                    isRefreshing = pullRefreshing,
                    error = screenState.errorMessage,
                    isEmpty = checklistListDisplay.showEmpty,
                    onRetry = { requestSync(showLoading = checklistListDisplay.showEmpty) },
                    emptyIcon = Icons.Default.Checklist,
                    emptyTitle = stringResource(R.string.no_checklists_yet),
                    emptySubtitle = stringResource(R.string.tap_add_checklist),
                    onRefresh = { requestSync(showLoading = false, fromPull = true) },
                    content = {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(checklistListDisplay.displayItems, key = { it.id }) { list ->
                                if (swipeToDeleteEnabled) {
                                    val swipeDeleteConfirm =
                                        stringResource(
                                            R.string.delete_checklist_confirm,
                                            list.title.ifBlank { stringResource(R.string.untitled) },
                                        )
                                    SwipeToDeleteContainer(
                                        enabled = true,
                                        onDelete = { offlineDeleteWithUndo(list) },
                                        deleteConfirmMessage = swipeDeleteConfirm,
                                    ) {
                                        OfflineChecklistCard(
                                            checklist = list,
                                            onClick = { vm.setSelectedList(list) },
                                            onDelete = { scope.launch { offlineDeleteWithUndo(list) } },
                                            syncStatusLabel = if (list.id in dirtyChecklistIds) pendingSyncLabel else null,
                                        )
                                    }
                                } else {
                                    OfflineChecklistCard(
                                        checklist = list,
                                        onClick = { vm.setSelectedList(list) },
                                        onDelete = { scope.launch { offlineDeleteWithUndo(list) } },
                                        syncStatusLabel = if (list.id in dirtyChecklistIds) pendingSyncLabel else null,
                                    )
                                }
                            }
                        }
                    },
                )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        ChecklistCreateDialog(
            categorySuggestions = checklistCategories,
            onDismiss = { vm.setShowCreateDialog(false) },
            onCreate = { newTitle, isProjectType, newCategory ->
                scope.launch {
                    val result =
                        offlineRepository.createChecklist(
                            title = newTitle,
                            type = if (isProjectType) "task" else "simple",
                            category = newCategory,
                        )
                    if (result.isSuccess) {
                        vm.setSelectedList(result.getOrNull())
                        vm.setShowCreateDialog(false)
                        if (!isOnline) snackbarHostState.showSnackbar(savedLocallyMsg)
                    } else {
                        snackbarHostState.showSnackbar(saveFailedMsg)
                    }
                }
            },
        )
    }
}

// ─── Checklist card ───────────────────────────────────────────────────────────────────

@Composable
private fun OfflineChecklistCard(
    checklist: Checklist,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    syncStatusLabel: String? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val displayTitle = checklist.title.ifBlank { stringResource(R.string.untitled) }
    val completed = checklist.items.count { it.completed }
    val total = checklist.items.size
    val progress = if (total > 0) completed.toFloat() / total else 0f
    val isProject =
        checklist.type.equals("project", ignoreCase = true) ||
            checklist.type.equals("task", ignoreCase = true)

    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            message = stringResource(R.string.delete_checklist_confirm, displayTitle),
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
        )
    }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuExpanded = true },
                ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isProject) {
                        Text(
                            stringResource(R.string.project_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_options),
                        )
                    }
                }
                if (checklist.items.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                    )
                    Text(
                        text = stringResource(R.string.progress_fraction, completed, total),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (!syncStatusLabel.isNullOrBlank()) {
                    Text(
                        text = syncStatusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                EditDropdownMenuItem(
                    onClick = {
                        menuExpanded = false
                        onClick()
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
}

// ─── Detail screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OfflineChecklistDetailContent(
    checklist: Checklist,
    offlineRepository: OfflineChecklistsRepository,
    categorySuggestions: List<String> = emptyList(),
    isOnline: Boolean,
    hasPendingSync: Boolean = false,
    onRetrySync: () -> Unit,
    onBack: () -> Unit,
    onUpdate: (Checklist) -> Unit,
    onDelete: () -> Unit,
    onSaveFailed: () -> Unit,
    onSavedLocally: () -> Unit,
    onRenameUnsupported: () -> Unit,
    onDiscardPendingSyncFailed: (Throwable) -> Unit = {},
    onDiscardPendingSyncDone: () -> Unit = {},
) {
    // Drive item list from the repository flow so offline mutations are reflected immediately.
    val allChecklists by offlineRepository.getChecklistsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val liveChecklist = allChecklists.find { it.id == checklist.id } ?: checklist
    val items = liveChecklist.items

    var newItemText by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDiscardPendingSyncDialog by remember { mutableStateOf(false) }
    var discardConfirmIsLocalOnly by remember { mutableStateOf(false) }
    var editingItemKey by remember(liveChecklist.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val discardConfirmMessage =
        if (discardConfirmIsLocalOnly) {
            stringResource(R.string.discard_pending_sync_local_only_confirm)
        } else {
            stringResource(R.string.discard_pending_sync_confirm)
        }

    val isProject =
        liveChecklist.type.equals("project", ignoreCase = true) ||
            liveChecklist.type.equals("task", ignoreCase = true)
    val flatItems =
        remember(items, isProject) {
            if (isProject) flattenItems(items) else items.mapIndexed { i, item -> FlatChecklistItem(item, 0, "$i") }
        }
    val toDo = flatItems.filter { !it.item.completed }
    val done = flatItems.filter { it.item.completed }

    fun handleResult(result: Result<Checklist>) {
        result.onSuccess { updated ->
            onUpdate(updated)
        }.onFailure {
            if (!isOnline) onSavedLocally() else onSaveFailed()
        }
    }

    if (showDiscardPendingSyncDialog) {
        ConfirmDiscardPendingSyncDialog(
            message = discardConfirmMessage,
            onDismiss = { showDiscardPendingSyncDialog = false },
            onConfirm = {
                showDiscardPendingSyncDialog = false
                scope.launch {
                    val result = offlineRepository.discardPendingSync(liveChecklist.id)
                    result.onSuccess { restored ->
                        if (restored == null) {
                            onBack()
                        } else {
                            onUpdate(restored)
                        }
                        onDiscardPendingSyncDone()
                    }.onFailure { onDiscardPendingSyncFailed(it) }
                }
            },
        )
    }

    if (showRenameDialog) {
        ChecklistRenameDialog(
            initialTitle = liveChecklist.title,
            initialCategory = liveChecklist.category,
            categorySuggestions = categorySuggestions,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newTitle, newCategory ->
                showRenameDialog = false
                scope.launch {
                    handleResult(offlineRepository.updateChecklist(liveChecklist.id, newTitle, newCategory))
                }
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        ChecklistDetailHeader(
            title = liveChecklist.title,
            onBack = onBack,
            onRename = { showRenameDialog = true },
            onDelete = onDelete,
            onDiscardPendingSync =
                if (hasPendingSync) {
                    {
                        scope.launch {
                            discardConfirmIsLocalOnly =
                                offlineRepository.isLocalOnlyChecklist(liveChecklist.id)
                            showDiscardPendingSyncDialog = true
                        }
                    }
                } else {
                    null
                },
        )

        OfflineConnectivityBanner(
            isOnline = isOnline,
            onRetrySync = onRetrySync,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newItemText,
                onValueChange = { newItemText = it },
                placeholder = { Text(stringResource(R.string.add_item)) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            if (newItemText.isNotBlank()) {
                                val text = newItemText
                                newItemText = ""
                                scope.launch {
                                    handleResult(offlineRepository.addItem(liveChecklist.id, text))
                                }
                            }
                        },
                    ),
            )
            IconButton(
                onClick = {
                    if (newItemText.isNotBlank()) {
                        val text = newItemText
                        newItemText = ""
                        scope.launch {
                            handleResult(offlineRepository.addItem(liveChecklist.id, text))
                        }
                    }
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
            }
        }

        if (flatItems.isNotEmpty()) {
            Text(
                text = stringResource(R.string.done_progress, done.size, flatItems.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        fun reorderItem(itemId: String?, up: Boolean) {
            val id = itemId ?: return
            val request =
                if (up) {
                    moveChecklistItemUpRequest(items, id)
                } else {
                    moveChecklistItemDownRequest(items, id)
                } ?: return
            scope.launch {
                handleResult(offlineRepository.reorderItems(liveChecklist.id, request))
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "header-todo") {
                SectionLabel(stringResource(R.string.section_to_do, toDo.size))
            }
            items(toDo, key = { "todo-${it.apiPath}-${it.item.text}" }) { flat ->
                ChecklistItemRow(
                    item = flat.item,
                    itemKey = flat.apiPath,
                    editingItemKey = editingItemKey,
                    onEditingItemKeyChange = { editingItemKey = it },
                    depth = flat.depth,
                    isProject = isProject,
                    onCheck = {
                        scope.launch {
                            handleResult(offlineRepository.checkItem(liveChecklist.id, flat.apiPath))
                        }
                    },
                    onUncheck = {
                        scope.launch {
                            handleResult(offlineRepository.uncheckItem(liveChecklist.id, flat.apiPath))
                        }
                    },
                    onDelete = {
                        scope.launch {
                            handleResult(offlineRepository.deleteItem(liveChecklist.id, flat.apiPath))
                        }
                    },
                    onUpdate = { text ->
                        scope.launch {
                            handleResult(offlineRepository.updateItemText(liveChecklist.id, flat.apiPath, text))
                        }
                    },
                    onMoveUp =
                        flat.item.id?.let { itemId ->
                            moveChecklistItemUpRequest(items, itemId)?.let { { reorderItem(itemId, up = true) } }
                        },
                    onMoveDown =
                        flat.item.id?.let { itemId ->
                            moveChecklistItemDownRequest(items, itemId)?.let { { reorderItem(itemId, up = false) } }
                        },
                    onAddSubItem =
                        if (isProject && flat.depth == 0) {
                            {
                                scope.launch {
                                    handleResult(
                                        offlineRepository.addItem(liveChecklist.id, "", parentIndex = flat.apiPath),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    actionIconSize = 32.dp,
                    actionGlyphSize = 18.dp,
                )
            }
            item(key = "header-done") {
                SectionLabel(stringResource(R.string.section_completed, done.size))
            }
            items(done, key = { "done-${it.apiPath}-${it.item.text}" }) { flat ->
                ChecklistItemRow(
                    item = flat.item,
                    itemKey = flat.apiPath,
                    editingItemKey = editingItemKey,
                    onEditingItemKeyChange = { editingItemKey = it },
                    depth = flat.depth,
                    isProject = isProject,
                    onCheck = {
                        scope.launch {
                            handleResult(offlineRepository.checkItem(liveChecklist.id, flat.apiPath))
                        }
                    },
                    onUncheck = {
                        scope.launch {
                            handleResult(offlineRepository.uncheckItem(liveChecklist.id, flat.apiPath))
                        }
                    },
                    onDelete = {
                        scope.launch {
                            handleResult(offlineRepository.deleteItem(liveChecklist.id, flat.apiPath))
                        }
                    },
                    onUpdate = { text ->
                        scope.launch {
                            handleResult(offlineRepository.updateItemText(liveChecklist.id, flat.apiPath, text))
                        }
                    },
                    onMoveUp =
                        flat.item.id?.let { itemId ->
                            moveChecklistItemUpRequest(items, itemId)?.let { { reorderItem(itemId, up = true) } }
                        },
                    onMoveDown =
                        flat.item.id?.let { itemId ->
                            moveChecklistItemDownRequest(items, itemId)?.let { { reorderItem(itemId, up = false) } }
                        },
                    onAddSubItem = null,
                    actionIconSize = 32.dp,
                    actionGlyphSize = 18.dp,
                )
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
    )
}

private data class FlatChecklistItem(val item: ChecklistItem, val depth: Int, val apiPath: String)

private fun flattenItems(
    items: List<ChecklistItem>,
    depth: Int = 0,
    parentPath: String = "",
): List<FlatChecklistItem> =
    items.flatMapIndexed { index, item ->
        val path = if (parentPath.isEmpty()) "$index" else "$parentPath.$index"
        listOf(FlatChecklistItem(item, depth, path)) +
            flattenItems(item.children.orEmpty(), depth + 1, path)
    }
