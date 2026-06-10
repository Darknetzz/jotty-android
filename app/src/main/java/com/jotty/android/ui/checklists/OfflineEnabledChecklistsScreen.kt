package com.jotty.android.ui.checklists

import android.content.Intent
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
import com.jotty.android.data.api.isCompletedForApi
import com.jotty.android.data.api.DEFAULT_TASK_STATUSES
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.UpdateTaskItemStatusRequest
import com.jotty.android.data.local.NetworkConnectivityMonitor
import com.jotty.android.data.local.OfflineChecklistsRepository
import com.jotty.android.data.local.itemAtPath
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
import com.jotty.android.ui.common.PendingSyncBadge
import com.jotty.android.ui.common.RegisterMainTabTopBar
import com.jotty.android.ui.common.SwipeToDeleteContainer
import com.jotty.android.ui.common.mainScreenTabContentPadding
import com.jotty.android.ui.common.rememberListScreenState
import com.jotty.android.ui.common.ShareServerDialog
import com.jotty.android.util.ApiErrorHelper
import com.jotty.android.util.JOTTY_ARCHIVE_CATEGORY
import com.jotty.android.util.defaultUnarchiveCategory
import com.jotty.android.util.exportChecklistAsPlainText
import com.jotty.android.util.isArchived
import com.jotty.android.util.ServerCapabilities
import com.jotty.android.util.buildKanbanColumns
import com.jotty.android.util.defaultKanbanItemStatus
import com.jotty.android.util.kanbanCardReorderRequest
import com.jotty.android.util.moveKanbanCardInColumnRequest
import com.jotty.android.util.visibleKanbanColumns
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.HttpException

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OfflineEnabledChecklistsScreen(
    offlineRepository: OfflineChecklistsRepository,
    api: JottyApi,
    vmKey: String,
    serverCapabilitiesKey: String,
    settingsRepository: SettingsRepository,
    swipeToDeleteEnabled: Boolean = false,
    tabReselectToken: Int = 0,
) {
    val contentPaddingMode by settingsRepository.contentPaddingMode.collectAsStateWithLifecycle(initialValue = "comfortable")
    val checklistDragReorderEnabled by settingsRepository.checklistDragReorderEnabled.collectAsStateWithLifecycle(initialValue = true)
    val showChecklistEmojis by settingsRepository.showChecklistEmojis.collectAsStateWithLifecycle(initialValue = true)
    val kanbanHideEmptyColumns by settingsRepository.kanbanHideEmptyColumns.collectAsStateWithLifecycle(initialValue = false)
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

    LaunchedEffect(Unit) {
        vm.categoryFilterEmptyEvents.collect { category ->
            snackbarHostState.showSnackbar(
                message = context.getString(R.string.offline_category_filter_empty, category),
            )
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
    LaunchedEffect(selectedCategory, filterRestored, checklists) {
        if (filterRestored) {
            settingsRepository.setChecklistsCategoryFilter(selectedCategory)
            if (!isOnline) {
                vm.notifyCategoryFilterEmptyIfNeeded(selectedCategory, checklists)
            }
        }
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
                    api = api,
                    offlineRepository = offlineRepository,
                    categorySuggestions = checklistCategories,
                    dragReorderEnabled = checklistDragReorderEnabled,
                    showChecklistEmojis = showChecklistEmojis,
                    kanbanHideEmptyColumns = kanbanHideEmptyColumns,
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
                    onRenameUnsupported = {
                        ServerCapabilities.markItemPatchLimited(serverCapabilitiesKey)
                        scope.launch { snackbarHostState.showSnackbar(renameLeafOnlyMsg) }
                    },
                    serverCapabilitiesKey = serverCapabilitiesKey,
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
                        item {
                            FilterChip(
                                selected = selectedCategory == JOTTY_ARCHIVE_CATEGORY,
                                onClick = { vm.toggleCategoryChip(JOTTY_ARCHIVE_CATEGORY, checklists) },
                                label = {
                                    Text(
                                        stringResource(R.string.category_archived),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                        items(
                            checklistCategories.filter { !it.equals(JOTTY_ARCHIVE_CATEGORY, true) },
                            key = { it },
                        ) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { vm.toggleCategoryChip(cat, checklists) },
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
                                            showPendingSync = list.id in dirtyChecklistIds,
                                        )
                                    }
                                } else {
                                    OfflineChecklistCard(
                                        checklist = list,
                                        onClick = { vm.setSelectedList(list) },
                                        onDelete = { scope.launch { offlineDeleteWithUndo(list) } },
                                        showPendingSync = list.id in dirtyChecklistIds,
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
    showPendingSync: Boolean = false,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val displayTitle = checklist.title.ifBlank { stringResource(R.string.untitled) }
    val progressCounts = checklistProgressCounts(checklist)
    val completed = progressCounts.completed
    val total = progressCounts.total
    val progress = progressCounts.fraction
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
                ChecklistCardTitleRow(
                    title = displayTitle,
                    checklistType = checklist.type,
                    onMenuClick = { menuExpanded = true },
                )
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
                if (showPendingSync) {
                    PendingSyncBadge(modifier = Modifier.padding(top = 6.dp))
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
    api: JottyApi,
    offlineRepository: OfflineChecklistsRepository,
    categorySuggestions: List<String> = emptyList(),
    dragReorderEnabled: Boolean = true,
    showChecklistEmojis: Boolean = true,
    kanbanHideEmptyColumns: Boolean = false,
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
    serverCapabilitiesKey: String? = null,
) {
    // Drive item list from the repository flow so offline mutations are reflected immediately.
    val allChecklists by offlineRepository.getChecklistsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val liveChecklist =
        allChecklists.find { it.id == checklist.id }
            ?: offlineRepository.remappedChecklistId(checklist.id)?.let { serverId ->
                allChecklists.find { it.id == serverId }
            }
            ?: checklist
    val items = liveChecklist.items

    var newItemText by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showManageStatusesDialog by remember { mutableStateOf(false) }
    var showDiscardPendingSyncDialog by remember { mutableStateOf(false) }
    var discardConfirmIsLocalOnly by remember { mutableStateOf(false) }
    var editingItemKey by remember(liveChecklist.id) { mutableStateOf<String?>(null) }
    var taskStatuses by remember(liveChecklist.id) { mutableStateOf(DEFAULT_TASK_STATUSES) }
    var canUseKanbanBoard by remember(liveChecklist.id) { mutableStateOf(true) }
    var projectView by remember(liveChecklist.id) { mutableStateOf(KanbanProjectView.Board) }
    var selectedKanbanPath by remember(liveChecklist.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val exportShareTitle = stringResource(R.string.share_checklist)
    val discardConfirmMessage =
        if (discardConfirmIsLocalOnly) {
            stringResource(R.string.discard_pending_sync_local_only_confirm)
        } else {
            stringResource(R.string.discard_pending_sync_confirm)
        }

    val isProject = isProjectChecklistType(liveChecklist.type)
    val showItemEmojis = checklistAutoEmojiEnabled(showChecklistEmojis, liveChecklist.type)
    val flatItems =
        remember(items, isProject) {
            if (isProject) {
                flattenChecklistItems(items)
            } else {
                items.mapIndexed { i, item -> ChecklistFlatItem(item, 0, "$i") }
            }
        }
    val toDo = flatItems.filter { !it.item.isCompletedForApi() }
    val done = flatItems.filter { it.item.isCompletedForApi() }

    fun handleResult(result: Result<Checklist>) {
        result.onSuccess { updated ->
            onUpdate(updated)
        }.onFailure {
            if (!isOnline) onSavedLocally() else onSaveFailed()
        }
    }

    fun refreshTaskStatuses() {
        if (!isProject || !isOnline) return
        scope.launch {
            if (offlineRepository.isLocalOnlyChecklist(liveChecklist.id)) {
                taskStatuses = DEFAULT_TASK_STATUSES
                canUseKanbanBoard = true
                return@launch
            }
            val taskId = offlineRepository.remappedChecklistId(liveChecklist.id) ?: liveChecklist.id
            try {
                val statuses = api.getTaskStatuses(taskId).statuses.sortedBy { it.order }
                taskStatuses = if (statuses.isNotEmpty()) statuses else DEFAULT_TASK_STATUSES
                canUseKanbanBoard = true
            } catch (e: Exception) {
                if (e is HttpException && e.code() in setOf(404, 405)) {
                    canUseKanbanBoard = false
                    return@launch
                }
                canUseKanbanBoard = false
            }
        }
    }

    LaunchedEffect(liveChecklist.id, isProject, isOnline) {
        taskStatuses = DEFAULT_TASK_STATUSES
        canUseKanbanBoard = true
        if (isProject) refreshTaskStatuses()
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
    if (showManageStatusesDialog) {
        ManageTaskStatusesDialog(
            statuses = taskStatuses,
            onDismiss = { showManageStatusesDialog = false },
            onSave = { updated ->
                showManageStatusesDialog = false
                if (!isOnline) {
                    onSaveFailed()
                    return@ManageTaskStatusesDialog
                }
                scope.launch {
                    runCatching {
                        saveTaskStatuses(
                            api = api,
                            taskId = liveChecklist.id,
                            previous = taskStatuses,
                            updated = updated,
                        )
                    }.onSuccess {
                        val syncResult = offlineRepository.syncChecklists(force = true)
                        if (syncResult.isFailure) {
                            onSaveFailed()
                        } else {
                            refreshTaskStatuses()
                        }
                    }.onFailure {
                        onSaveFailed()
                    }
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
            onShare = { showShareDialog = true },
            isArchived = liveChecklist.isArchived(),
            onArchiveToggle = {
                scope.launch {
                    val archived = liveChecklist.isArchived()
                    handleResult(
                        offlineRepository.updateChecklist(
                            liveChecklist.id,
                            liveChecklist.title,
                            if (archived) {
                                defaultUnarchiveCategory()
                            } else {
                                JOTTY_ARCHIVE_CATEGORY
                            },
                        ),
                    )
                    if (!archived) onBack()
                }
            },
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

        if (showShareDialog) {
            ShareServerDialog(
                itemType = "checklist",
                itemId = liveChecklist.id,
                itemTitle = liveChecklist.title,
                api = api,
                capabilitiesKey = serverCapabilitiesKey,
                onDismiss = { showShareDialog = false },
                onExportText = {
                    val text =
                        exportChecklistAsPlainText(
                            liveChecklist.copy(items = items),
                            taskStatuses,
                        )
                    val intent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                    context.startActivity(Intent.createChooser(intent, exportShareTitle))
                    showShareDialog = false
                },
            )
        }

        OfflineConnectivityBanner(
            isOnline = isOnline,
            onRetrySync = onRetrySync,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        serverCapabilitiesKey?.let { key ->
            ChecklistPatchCapabilityBanner(
                capabilitiesKey = key,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        ChecklistAddItemField(
            value = newItemText,
            onValueChange = { newItemText = it },
            existingItems = flatItems,
            itemSearchEnabled = !isProject,
            modifier = Modifier.padding(horizontal = 16.dp),
            onAddItem = { text ->
                val defaultStatus =
                    if (isProject && canUseKanbanBoard) {
                        defaultKanbanItemStatus(taskStatuses)
                    } else {
                        null
                    }
                scope.launch {
                    handleResult(
                        offlineRepository.addItem(
                            liveChecklist.id,
                            text,
                            status = defaultStatus,
                        ),
                    )
                }
            },
            onUncheckItem = { path ->
                scope.launch {
                    handleResult(offlineRepository.uncheckItem(liveChecklist.id, path))
                }
            },
        )

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

        if (isProject && canUseKanbanBoard) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KanbanProjectViewToggle(
                    view = projectView,
                    onViewChange = { projectView = it },
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { showManageStatusesDialog = true },
                    enabled = isOnline,
                ) {
                    Text(stringResource(R.string.kanban_manage_statuses))
                }
            }
        }

        if (isProject && canUseKanbanBoard && projectView == KanbanProjectView.Board) {
            val columns =
                remember(items, taskStatuses, kanbanHideEmptyColumns) {
                    buildKanbanColumns(items = items, statuses = taskStatuses)
                        .visibleKanbanColumns(kanbanHideEmptyColumns)
                }
            if (!isOnline) {
                Text(
                    text = stringResource(R.string.kanban_move_online_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
            TaskKanbanBoard(
                columns = columns,
                allStatuses = taskStatuses,
                moveEnabled = isOnline,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                onMoveItem = { apiPath, newStatusId ->
                    if (!isOnline) return@TaskKanbanBoard
                    scope.launch {
                        runCatching {
                            api.updateTaskItemStatus(
                                taskId = liveChecklist.id,
                                itemIndex = apiPath,
                                body = UpdateTaskItemStatusRequest(status = newStatusId),
                            )
                        }.onSuccess {
                            val syncResult = offlineRepository.syncChecklists(force = true)
                            if (syncResult.isFailure) onSaveFailed()
                        }.onFailure { error ->
                            if (error is HttpException && error.code() in setOf(404, 405)) {
                                canUseKanbanBoard = false
                            }
                            onSaveFailed()
                        }
                    }
                },
                onDeleteItem = { apiPath ->
                    scope.launch {
                        handleResult(offlineRepository.deleteItem(liveChecklist.id, apiPath))
                    }
                },
                onOpenItem = { card -> selectedKanbanPath = "${card.index}" },
                onAddToColumn = { statusId, text ->
                    scope.launch {
                        handleResult(
                            offlineRepository.addItem(
                                liveChecklist.id,
                                text,
                                status = statusId,
                            ),
                        )
                    }
                },
                onMoveCardInColumn = { columnCards, cardIndex, up ->
                    val request = moveKanbanCardInColumnRequest(columnCards, cardIndex, up) ?: return@TaskKanbanBoard
                    scope.launch {
                        handleResult(offlineRepository.reorderItems(liveChecklist.id, request))
                    }
                },
                onReorderCardInColumn = { columnCards, fromIndex, toIndex ->
                    val request = kanbanCardReorderRequest(columnCards, fromIndex, toIndex) ?: return@TaskKanbanBoard
                    scope.launch {
                        handleResult(offlineRepository.reorderItems(liveChecklist.id, request))
                    }
                },
                onUpdateTitle = { apiPath, text ->
                    scope.launch {
                        handleResult(offlineRepository.updateItemText(liveChecklist.id, apiPath, text))
                    }
                },
                dragReorderEnabled = dragReorderEnabled,
                showChecklistEmojis = showItemEmojis,
            )
            selectedKanbanPath?.let { path ->
                val detailItem = itemAtPath(items, path)
                if (detailItem != null) {
                    KanbanItemDetailScreen(
                        item = detailItem,
                        itemPath = path,
                        taskStatuses = taskStatuses,
                        statusMoveEnabled = isOnline,
                        actions =
                            OfflineKanbanItemActions(
                                offlineRepository = offlineRepository,
                                checklistId = liveChecklist.id,
                                itemPath = path,
                                items = { items },
                                onChecklistUpdated = { updated -> onUpdate(updated) },
                                moveToStatusOnline =
                                    if (isOnline) {
                                        { statusId ->
                                            runCatching {
                                                api.updateTaskItemStatus(
                                                    liveChecklist.id,
                                                    path,
                                                    UpdateTaskItemStatusRequest(status = statusId),
                                                )
                                                offlineRepository.syncChecklists(force = true).getOrThrow()
                                            }
                                        }
                                    } else {
                                        null
                                    },
                            ),
                        onDismiss = { selectedKanbanPath = null },
                        onDeleted = { selectedKanbanPath = null },
                        onError = onSaveFailed,
                        showChecklistEmojis = showItemEmojis,
                    )
                } else {
                    LaunchedEffect(path) { selectedKanbanPath = null }
                }
            }
        }

        if (!isProject || !canUseKanbanBoard || projectView == KanbanProjectView.List) {
            if (isProject && !canUseKanbanBoard) {
                Text(
                    text = stringResource(R.string.kanban_not_supported_fallback),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            ChecklistDetailItemsList(
                treeItems = items,
                toDo = toDo,
                completed = done,
                doneCount = done.size,
                total = flatItems.size,
                dragReorderEnabled = dragReorderEnabled,
                onReorder = { request ->
                    scope.launch {
                        handleResult(offlineRepository.reorderItems(liveChecklist.id, request))
                    }
                },
        ) { flat, reorderableScope, _, onDragStarted, onDragStopped ->
            ChecklistDetailItemRow(
                flat = flat,
                editingItemKey = editingItemKey,
                onEditingItemKeyChange = { editingItemKey = it },
                isProject = isProject,
                reorderableScope = reorderableScope,
                onDragStarted = onDragStarted,
                onDragStopped = onDragStopped,
                showChecklistEmojis = showItemEmojis,
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
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────────────

