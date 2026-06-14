package com.jotty.android.ui.checklists

import android.app.Application
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jotty.android.R
import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.DEFAULT_TASK_STATUSES
import com.jotty.android.data.api.isCompletedForApi
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.TaskStatus
import com.jotty.android.data.api.UpdateChecklistRequest
import com.jotty.android.data.api.UpdateTaskItemStatusRequest
import com.jotty.android.data.local.itemAtPath
import com.jotty.android.data.preferences.SettingsRepository
import com.jotty.android.ui.common.ConfirmDeleteDialog
import com.jotty.android.ui.common.DeleteDropdownMenuItem
import com.jotty.android.ui.common.EditDropdownMenuItem
import com.jotty.android.ui.common.ListDetailContainer
import com.jotty.android.ui.common.ListScreenContent
import com.jotty.android.ui.common.ListSortOption
import com.jotty.android.ui.common.SortMenuButton
import com.jotty.android.ui.common.sortedBy
import com.jotty.android.ui.common.MainNestedScaffoldContentWindowInsets
import com.jotty.android.ui.common.MainTabTopBarState
import com.jotty.android.ui.common.RegisterMainTabTopBar
import com.jotty.android.ui.common.SwipeToDeleteContainer
import com.jotty.android.ui.common.mainScreenTabContentPadding
import com.jotty.android.util.KanbanItemFieldsProbe
import com.jotty.android.util.ServerCapabilities
import android.content.Intent
import com.jotty.android.ui.common.ShareServerDialog
import com.jotty.android.util.JOTTY_ARCHIVE_CATEGORY
import com.jotty.android.util.defaultUnarchiveCategory
import com.jotty.android.util.exportChecklistAsPlainText
import com.jotty.android.util.isArchived
import com.jotty.android.util.buildKanbanColumns
import com.jotty.android.util.defaultKanbanItemStatus
import com.jotty.android.util.kanbanCardReorderRequest
import com.jotty.android.util.moveKanbanCardInColumnRequest
import com.jotty.android.util.moveChecklistItemDownRequest
import com.jotty.android.util.moveChecklistItemUpRequest
import com.jotty.android.util.visibleKanbanColumns
import com.jotty.android.util.updateChecklistItemText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.HttpException

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChecklistsScreen(
    api: JottyApi,
    settingsRepository: SettingsRepository,
    swipeToDeleteEnabled: Boolean = false,
    serverCapabilitiesKey: String? = null,
    tabReselectToken: Int = 0,
) {
    val contentPaddingMode by settingsRepository.contentPaddingMode.collectAsStateWithLifecycle(initialValue = "comfortable")
    val contentVerticalDp = if (contentPaddingMode == "compact") 8 else 16
    val application = LocalContext.current.applicationContext as Application
    val vm: ChecklistsViewModel = viewModel { ChecklistsViewModel(application, api) }
    val checklists by vm.checklists.collectAsStateWithLifecycle()
    val filteredChecklists by vm.filteredChecklists.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by vm.selectedCategory.collectAsStateWithLifecycle()
    val checklistCategories by vm.checklistCategories.collectAsStateWithLifecycle()
    val checklistDragReorderEnabled by settingsRepository.checklistDragReorderEnabled.collectAsStateWithLifecycle(initialValue = true)
    val showChecklistEmojis by settingsRepository.showChecklistEmojis.collectAsStateWithLifecycle(initialValue = true)
    val kanbanHideEmptyColumns by settingsRepository.kanbanHideEmptyColumns.collectAsStateWithLifecycle(initialValue = false)
    val sortKey by settingsRepository.listSortOption.collectAsStateWithLifecycle(initialValue = "updated")
    val sortOption = ListSortOption.fromKey(sortKey)
    val sortedChecklists = remember(filteredChecklists, sortOption) { filteredChecklists.sortedBy(sortOption) }
    val selectedList by vm.selectedList.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val showCreateDialog by vm.showCreateDialog.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailedMsg = stringResource(R.string.save_failed)
    val deleteFailedMsg = stringResource(R.string.delete_failed)
    val renameLeafOnlyMsg = stringResource(R.string.rename_leaf_only)
    val checklistDeletedMsg = stringResource(R.string.checklist_deleted)
    val undoLabel = stringResource(R.string.undo)

    suspend fun deleteWithUndoForList(list: Checklist) {
        try {
            vm.deleteChecklistSuspend(list.id)
            val result =
                snackbarHostState.showSnackbar(
                    message = checklistDeletedMsg,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Short,
                )
            if (result == SnackbarResult.ActionPerformed) {
                if (!vm.recreateChecklistAfterUndo(list)) {
                    snackbarHostState.showSnackbar(saveFailedMsg)
                }
            }
        } catch (_: Exception) {
            snackbarHostState.showSnackbar(deleteFailedMsg)
        }
    }

    LaunchedEffect(Unit) { vm.loadChecklists() }

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
                    isOnline = true,
                    isSyncing = loading,
                    lastSyncAttemptEpochMs = null,
                    onRefresh = { vm.loadChecklists() },
                    onAdd = { vm.setShowCreateDialog(true) },
                    showSyncStatus = false,
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
                ChecklistDetailScreen(
                    checklist = currentList,
                    api = api,
                    categorySuggestions = checklistCategories,
                    dragReorderEnabled = checklistDragReorderEnabled,
                    showChecklistEmojis = showChecklistEmojis,
                    kanbanHideEmptyColumns = kanbanHideEmptyColumns,
                    onBack = { vm.setSelectedList(null) },
                    onUpdate = {
                        vm.loadChecklists()
                        vm.setSelectedList(it)
                    },
                    onDelete = { scope.launch { deleteWithUndoForList(currentList) } },
                    onSaveFailed = { scope.launch { snackbarHostState.showSnackbar(saveFailedMsg) } },
                    onDeleteFailed = { scope.launch { snackbarHostState.showSnackbar(deleteFailedMsg) } },
                    onRenameUnsupported = {
                        serverCapabilitiesKey?.let { ServerCapabilities.markItemPatchLimited(it) }
                        scope.launch { snackbarHostState.showSnackbar(renameLeafOnlyMsg) }
                    },
                    serverCapabilitiesKey = serverCapabilitiesKey,
                )
                } else {
                    Column(Modifier.fillMaxSize()) {
                if (checklists.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { vm.setSearchQuery(it) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.search_checklists)) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search))
                            },
                            singleLine = true,
                        )
                        SortMenuButton(
                            current = sortOption,
                            onSelect = { scope.launch { settingsRepository.setListSortOption(it.key) } },
                        )
                    }
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
                                    onClick = { vm.toggleCategoryChip(JOTTY_ARCHIVE_CATEGORY) },
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
                                    onClick = { vm.toggleCategoryChip(cat) },
                                    label = { Text(cat, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                ListScreenContent(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    showSkeleton = loading && sortedChecklists.isEmpty(),
                    isRefreshing = loading && sortedChecklists.isNotEmpty(),
                    error = error,
                    isEmpty = sortedChecklists.isEmpty(),
                    onRetry = { vm.loadChecklists() },
                    emptyIcon = Icons.Default.Checklist,
                    emptyTitle = stringResource(R.string.no_checklists_yet),
                    emptySubtitle = stringResource(R.string.tap_add_checklist),
                    onRefresh = { vm.loadChecklists() },
                    content = {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(sortedChecklists, key = { it.id }) { list ->
                                if (swipeToDeleteEnabled) {
                                    val swipeDeleteConfirm =
                                        stringResource(
                                            R.string.delete_checklist_confirm,
                                            list.title.ifBlank { stringResource(R.string.untitled) },
                                        )
                                    SwipeToDeleteContainer(
                                        enabled = true,
                                        onDelete = { deleteWithUndoForList(list) },
                                        deleteConfirmMessage = swipeDeleteConfirm,
                                    ) {
                                        ChecklistCard(
                                            checklist = list,
                                            onClick = { vm.setSelectedList(list) },
                                            onDelete = { scope.launch { deleteWithUndoForList(list) } },
                                        )
                                    }
                                } else {
                                    ChecklistCard(
                                        checklist = list,
                                        onClick = { vm.setSelectedList(list) },
                                        onDelete = { scope.launch { deleteWithUndoForList(list) } },
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
                vm.createChecklist(
                    title = newTitle,
                    projectTaskType = isProjectType,
                    category = newCategory,
                    onFailure = { scope.launch { snackbarHostState.showSnackbar(saveFailedMsg) } },
                )
            },
        )
    }
}

@Composable
private fun ChecklistCard(
    checklist: Checklist,
    onClick: () -> Unit,
    onDelete: () -> Unit,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChecklistDetailScreen(
    checklist: Checklist,
    api: JottyApi,
    categorySuggestions: List<String> = emptyList(),
    dragReorderEnabled: Boolean = true,
    showChecklistEmojis: Boolean = true,
    kanbanHideEmptyColumns: Boolean = false,
    onBack: () -> Unit,
    onUpdate: (Checklist) -> Unit,
    onDelete: () -> Unit,
    onSaveFailed: () -> Unit = {},
    onDeleteFailed: () -> Unit = {},
    onRenameUnsupported: () -> Unit = {},
    serverCapabilitiesKey: String? = null,
) {
    val detailVm: ChecklistDetailViewModel =
        viewModel(key = checklist.id) {
            ChecklistDetailViewModel(checklist.id, api)
        }
    val items by detailVm.items.collectAsStateWithLifecycle()
    val displayTitle by detailVm.displayTitle.collectAsStateWithLifecycle()
    val showRenameDialog by detailVm.showRenameDialog.collectAsStateWithLifecycle()
    val showManageStatusesDialog by detailVm.showManageStatusesDialog.collectAsStateWithLifecycle()
    val editingItemKey by detailVm.editingItemKey.collectAsStateWithLifecycle()
    val taskStatuses by detailVm.taskStatuses.collectAsStateWithLifecycle()
    val canUseKanbanBoard by detailVm.canUseKanbanBoard.collectAsStateWithLifecycle()
    val projectView by detailVm.projectView.collectAsStateWithLifecycle()
    val selectedKanbanPath by detailVm.selectedKanbanPath.collectAsStateWithLifecycle()
    val showShareDialog by detailVm.showShareDialog.collectAsStateWithLifecycle()
    var richFieldsSupported by remember { mutableStateOf(false) }
    var newItemText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(checklist.id, checklist.title, checklist.items) {
        detailVm.syncFromChecklist(checklist)
    }

    fun refresh() {
        detailVm.refreshItemsFromServer(onUpdated = onUpdate, onFailed = onSaveFailed)
    }

    val isProject = isProjectChecklistType(checklist.type)
    val showItemEmojis = checklistAutoEmojiEnabled(showChecklistEmojis, checklist.type)

    LaunchedEffect(checklist.id, isProject) {
        detailVm.resetProjectState()
        if (isProject) detailVm.refreshTaskStatuses(isProject = true)
    }

    LaunchedEffect(serverCapabilitiesKey, checklist.id, items, selectedKanbanPath) {
        val key = serverCapabilitiesKey ?: return@LaunchedEffect
        KanbanItemFieldsProbe.markSupportedFromItems(key, items)
        if (KanbanItemFieldsProbe.isKanbanItemRichFieldsSupported(key)) {
            richFieldsSupported = true
            return@LaunchedEffect
        }
        val path = selectedKanbanPath
        if (path == null) {
            richFieldsSupported = false
            return@LaunchedEffect
        }
        richFieldsSupported =
            KanbanItemFieldsProbe.probeKanbanItemFields(
                api = api,
                taskId = checklist.id,
                itemPath = path,
                capabilitiesKey = key,
            )
    }

    if (showRenameDialog) {
        ChecklistRenameDialog(
            initialTitle = displayTitle,
            initialCategory = checklist.category,
            categorySuggestions = categorySuggestions,
            onDismiss = { detailVm.setShowRenameDialog(false) },
            onConfirm = { newTitle, newCategory ->
                detailVm.setShowRenameDialog(false)
                detailVm.renameChecklist(
                    currentCategory = checklist.category,
                    newTitle = newTitle,
                    newCategory = newCategory,
                    onUpdated = onUpdate,
                    onFailed = onSaveFailed,
                )
            },
        )
    }
    if (showManageStatusesDialog) {
        ManageTaskStatusesDialog(
            statuses = taskStatuses,
            onDismiss = { detailVm.setShowManageStatusesDialog(false) },
            onSave = { updated ->
                detailVm.setShowManageStatusesDialog(false)
                scope.launch {
                    try {
                        saveTaskStatuses(
                            api = api,
                            taskId = checklist.id,
                            previous = taskStatuses,
                            updated = updated,
                        )
                        detailVm.refreshTaskStatuses(isProject = true)
                        refresh()
                    } catch (_: Exception) {
                        onSaveFailed()
                    }
                }
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        ChecklistDetailHeader(
            title = displayTitle,
            onBack = onBack,
            onRename = { detailVm.setShowRenameDialog(true) },
            onDelete = onDelete,
            onShare = { detailVm.setShowShareDialog(true) },
            isArchived = checklist.isArchived(),
            onArchiveToggle = {
                scope.launch {
                    try {
                        val archived = checklist.isArchived()
                        val response =
                            api.updateChecklist(
                                checklist.id,
                                UpdateChecklistRequest(
                                    category =
                                        if (archived) {
                                            defaultUnarchiveCategory()
                                        } else {
                                            JOTTY_ARCHIVE_CATEGORY
                                        },
                                ),
                            )
                        onUpdate(response.data)
                        if (!archived) onBack()
                    } catch (_: Exception) {
                        onSaveFailed()
                    }
                }
            },
        )

        if (showShareDialog) {
            val exportTitle = stringResource(R.string.share_checklist)
            ShareServerDialog(
                itemType = "checklist",
                itemId = checklist.id,
                itemTitle = displayTitle,
                api = api,
                capabilitiesKey = serverCapabilitiesKey,
                onDismiss = { detailVm.setShowShareDialog(false) },
                onExportText = {
                    val text = exportChecklistAsPlainText(checklist.copy(items = items, title = displayTitle), taskStatuses)
                    val intent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                    context.startActivity(Intent.createChooser(intent, exportTitle))
                    detailVm.setShowShareDialog(false)
                },
            )
        }

        serverCapabilitiesKey?.let { key ->
            ChecklistPatchCapabilityBanner(
                capabilitiesKey = key,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        val flatItems =
            remember(items, isProject) {
                if (isProject) {
                    flattenChecklistItems(items)
                } else {
                    items.mapIndexed { index, item -> ChecklistFlatItem(item, 0, "$index") }
                }
            }

        Spacer(modifier = Modifier.height(8.dp))

        ChecklistAddItemField(
            value = newItemText,
            onValueChange = { newItemText = it },
            existingItems = flatItems,
            itemSearchEnabled = !isProject,
            modifier = Modifier.padding(horizontal = 16.dp),
            onAddItem = { text ->
                scope.launch {
                    try {
                        val defaultStatus =
                            if (isProject && canUseKanbanBoard) {
                                defaultKanbanItemStatus(taskStatuses)
                            } else {
                                null
                            }
                        api.addChecklistItem(
                            checklist.id,
                            com.jotty.android.data.api.AddItemRequest(
                                text = text,
                                status = defaultStatus,
                            ),
                        )
                        refresh()
                    } catch (_: Exception) {
                        onSaveFailed()
                    }
                }
            },
            onUncheckItem = { path ->
                scope.launch {
                    try {
                        api.uncheckItem(checklist.id, path)
                        refresh()
                    } catch (_: Exception) {
                        onSaveFailed()
                    }
                }
            },
        )

        val toDo = flatItems.filter { !it.item.isCompletedForApi() }
        val completed = flatItems.filter { it.item.isCompletedForApi() }
        val total = flatItems.size
        val doneCount = completed.size

        fun reorderItem(itemId: String?, up: Boolean) {
            val id = itemId ?: return
            val request =
                if (up) {
                    moveChecklistItemUpRequest(items, id)
                } else {
                    moveChecklistItemDownRequest(items, id)
                } ?: return
            scope.launch {
                try {
                    api.reorderItems(checklist.id, request)
                    refresh()
                } catch (_: Exception) {
                    onSaveFailed()
                }
            }
        }

        fun applyDragReorder(request: com.jotty.android.data.api.ReorderItemsRequest) {
            scope.launch {
                try {
                    api.reorderItems(checklist.id, request)
                    refresh()
                } catch (_: Exception) {
                    onSaveFailed()
                }
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
                    onViewChange = { detailVm.setProjectView(it) },
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { detailVm.setShowManageStatusesDialog(true) }) {
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
            TaskKanbanBoard(
                columns = columns,
                allStatuses = taskStatuses,
                moveEnabled = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                onMoveItem = { apiPath, newStatusId ->
                    scope.launch {
                        try {
                            api.updateTaskItemStatus(
                                taskId = checklist.id,
                                itemIndex = apiPath,
                                body = UpdateTaskItemStatusRequest(status = newStatusId),
                            )
                            refresh()
                        } catch (e: Exception) {
                            if (e is HttpException && e.code() in setOf(404, 405)) {
                                detailVm.setCanUseKanbanBoard(false)
                            }
                            onSaveFailed()
                        }
                    }
                },
                onDeleteItem = { apiPath ->
                    scope.launch {
                        try {
                            api.deleteItem(checklist.id, apiPath)
                            refresh()
                        } catch (_: Exception) {
                            onDeleteFailed()
                        }
                    }
                },
                onOpenItem = { card -> detailVm.setSelectedKanbanPath("${card.index}") },
                onAddToColumn = { statusId, text ->
                    scope.launch {
                        try {
                            api.addChecklistItem(
                                checklist.id,
                                com.jotty.android.data.api.AddItemRequest(text = text, status = statusId),
                            )
                            refresh()
                        } catch (_: Exception) {
                            onSaveFailed()
                        }
                    }
                },
                onMoveCardInColumn = { columnCards, cardIndex, up ->
                    val request = moveKanbanCardInColumnRequest(columnCards, cardIndex, up) ?: return@TaskKanbanBoard
                    applyDragReorder(request)
                },
                onReorderCardInColumn = { columnCards, fromIndex, toIndex ->
                    val request = kanbanCardReorderRequest(columnCards, fromIndex, toIndex) ?: return@TaskKanbanBoard
                    applyDragReorder(request)
                },
                onUpdateTitle = { apiPath, text ->
                    scope.launch {
                        try {
                            updateChecklistItemText(
                                api = api,
                                listId = checklist.id,
                                path = apiPath,
                                text = text,
                                items = items,
                                onPatchUnavailable = {
                                    serverCapabilitiesKey?.let { ServerCapabilities.markItemPatchLimited(it) }
                                },
                            )
                            refresh()
                        } catch (e: UnsupportedOperationException) {
                            onRenameUnsupported()
                        } catch (_: Exception) {
                            onSaveFailed()
                        }
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
                        statusMoveEnabled = true,
                        richFieldsSupported = richFieldsSupported,
                        actions =
                            apiKanbanItemActions(
                                api = api,
                                checklistId = checklist.id,
                                itemPath = path,
                                items = { items },
                                onRefresh = {
                                    detailVm.pullItemsFromServer(onUpdated = onUpdate, onFailed = onSaveFailed)
                                },
                                serverCapabilitiesKey = serverCapabilitiesKey,
                                richFieldsSupported = richFieldsSupported,
                                performMoveToStatus = { statusId ->
                                    try {
                                        api.updateTaskItemStatus(
                                            checklist.id,
                                            path,
                                            UpdateTaskItemStatusRequest(status = statusId),
                                        )
                                        detailVm.refreshItemsFromServer()?.let(onUpdate)
                                        Result.success(Unit)
                                    } catch (e: Exception) {
                                        Result.failure(e)
                                    }
                                },
                            ),
                        onDismiss = { detailVm.setSelectedKanbanPath(null) },
                        onDeleted = { detailVm.setSelectedKanbanPath(null) },
                        onError = onSaveFailed,
                        showChecklistEmojis = showItemEmojis,
                    )
                } else {
                    LaunchedEffect(path) { detailVm.setSelectedKanbanPath(null) }
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
                completed = completed,
                doneCount = doneCount,
                total = total,
                dragReorderEnabled = dragReorderEnabled,
                onReorder = ::applyDragReorder,
        ) { flat, reorderableScope, _, onDragStarted, onDragStopped ->
            ChecklistDetailItemRow(
                flat = flat,
                editingItemKey = editingItemKey,
                onEditingItemKeyChange = { detailVm.setEditingItemKey(it) },
                isProject = isProject,
                reorderableScope = reorderableScope,
                onDragStarted = onDragStarted,
                onDragStopped = onDragStopped,
                showChecklistEmojis = showItemEmojis,
                    onCheck = {
                        scope.launch {
                            try {
                                api.checkItem(checklist.id, flat.apiPath)
                                refresh()
                            } catch (_: Exception) {
                                onSaveFailed()
                            }
                        }
                    },
                    onUncheck = {
                        scope.launch {
                            try {
                                api.uncheckItem(checklist.id, flat.apiPath)
                                refresh()
                            } catch (_: Exception) {
                                onSaveFailed()
                            }
                        }
                    },
                    onDelete = {
                        scope.launch {
                            try {
                                api.deleteItem(checklist.id, flat.apiPath)
                                refresh()
                            } catch (_: Exception) {
                                onDeleteFailed()
                            }
                        }
                    },
                    onUpdate = { text ->
                        scope.launch {
                            try {
                                updateChecklistItemText(
                                    api = api,
                                    listId = checklist.id,
                                    path = flat.apiPath,
                                    text = text,
                                    items = items,
                                    onPatchUnavailable = {
                                        serverCapabilitiesKey?.let { ServerCapabilities.markItemPatchLimited(it) }
                                    },
                                )
                                refresh()
                            } catch (e: UnsupportedOperationException) {
                                onRenameUnsupported()
                            } catch (_: Exception) {
                                onSaveFailed()
                            }
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
                                    try {
                                        api.addChecklistItem(
                                            checklist.id,
                                            com.jotty.android.data.api.AddItemRequest(
                                                text = "",
                                                parentIndex = flat.apiPath,
                                            ),
                                        )
                                        refresh()
                                    } catch (_: Exception) {
                                        onSaveFailed()
                                    }
                                }
                            }
                        } else {
                            null
                        },
                )
            }
        }
    }
}
