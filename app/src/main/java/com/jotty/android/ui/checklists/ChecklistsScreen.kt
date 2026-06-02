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
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.TaskStatus
import com.jotty.android.data.api.UpdateChecklistRequest
import com.jotty.android.data.api.UpdateTaskItemStatusRequest
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
import com.jotty.android.util.buildKanbanColumns
import com.jotty.android.util.moveChecklistItemDownRequest
import com.jotty.android.util.moveChecklistItemUpRequest
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
                    onBack = { vm.setSelectedList(null) },
                    onUpdate = {
                        vm.loadChecklists()
                        vm.setSelectedList(it)
                    },
                    onDelete = { scope.launch { deleteWithUndoForList(currentList) } },
                    onSaveFailed = { scope.launch { snackbarHostState.showSnackbar(saveFailedMsg) } },
                    onDeleteFailed = { scope.launch { snackbarHostState.showSnackbar(deleteFailedMsg) } },
                    onRenameUnsupported = { scope.launch { snackbarHostState.showSnackbar(renameLeafOnlyMsg) } },
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
                            items(checklistCategories, key = { it }) { cat ->
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
    val completed = checklist.items.count { it.completed }
    val total = checklist.items.size
    val progress = if (total > 0) completed.toFloat() / total else 0f

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
                    Spacer(modifier = Modifier.width(8.dp))
                    ChecklistTypeBadge(type = checklist.type)
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
    onBack: () -> Unit,
    onUpdate: (Checklist) -> Unit,
    onDelete: () -> Unit,
    onSaveFailed: () -> Unit = {},
    onDeleteFailed: () -> Unit = {},
    onRenameUnsupported: () -> Unit = {},
) {
    var items by remember { mutableStateOf(checklist.items) }
    var displayTitle by remember { mutableStateOf(checklist.title) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showManageStatusesDialog by remember { mutableStateOf(false) }
    var newItemText by remember { mutableStateOf("") }
    var editingItemKey by remember(checklist.id) { mutableStateOf<String?>(null) }
    var taskStatuses by remember(checklist.id) { mutableStateOf(DEFAULT_TASK_STATUSES) }
    var canUseKanbanBoard by remember(checklist.id) { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(checklist.id, checklist.title, checklist.items) {
        displayTitle = checklist.title
        items = checklist.items
        editingItemKey = null
    }

    fun refresh() {
        scope.launch {
            try {
                val updated = api.getChecklists().checklists.find { it.id == checklist.id }
                if (updated != null) {
                    items = updated.items
                    onUpdate(updated)
                }
            } catch (_: Exception) {
                onSaveFailed()
            }
        }
    }

    val isProject = isProjectChecklistType(checklist.type)

    fun refreshTaskStatuses() {
        if (!isProject) return
        scope.launch {
            try {
                val statuses = api.getTaskStatuses(checklist.id).statuses.sortedBy { it.order }
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

    LaunchedEffect(checklist.id, isProject) {
        taskStatuses = DEFAULT_TASK_STATUSES
        canUseKanbanBoard = true
        if (isProject) refreshTaskStatuses()
    }

    if (showRenameDialog) {
        ChecklistRenameDialog(
            initialTitle = displayTitle,
            initialCategory = checklist.category,
            categorySuggestions = categorySuggestions,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newTitle, newCategory ->
                showRenameDialog = false
                scope.launch {
                    try {
                        val response =
                            api.updateChecklist(
                                checklist.id,
                                UpdateChecklistRequest(title = newTitle, category = newCategory),
                            )
                        if (response.success) {
                            displayTitle = response.data.title
                            onUpdate(response.data)
                        } else {
                            onSaveFailed()
                        }
                    } catch (_: Exception) {
                        onSaveFailed()
                    }
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
                scope.launch {
                    try {
                        saveTaskStatuses(
                            api = api,
                            taskId = checklist.id,
                            previous = taskStatuses,
                            updated = updated,
                        )
                        refreshTaskStatuses()
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
            onRename = { showRenameDialog = true },
            onDelete = onDelete,
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
            )
            IconButton(
                onClick = {
                    if (newItemText.isNotBlank()) {
                        scope.launch {
                            try {
                                api.addChecklistItem(
                                    checklist.id,
                                    com.jotty.android.data.api.AddItemRequest(text = newItemText),
                                )
                                newItemText = ""
                                refresh()
                            } catch (_: Exception) {
                                onSaveFailed()
                            }
                        }
                    }
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
            }
        }

        val flatItems =
            remember(items, isProject) {
                if (isProject) {
                    flattenChecklistItems(items)
                } else {
                    items.mapIndexed { index, item -> ChecklistFlatItem(item, 0, "$index") }
                }
            }
        val toDo = flatItems.filter { !it.item.completed }
        val completed = flatItems.filter { it.item.completed }
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
            val columns = remember(items, taskStatuses) { buildKanbanColumns(items = items, statuses = taskStatuses) }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.kanban_board),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showManageStatusesDialog = true }) {
                    Text(stringResource(R.string.kanban_manage_statuses))
                }
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
                                canUseKanbanBoard = false
                            }
                            onSaveFailed()
                        }
                    }
                },
            )
        } else {
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
                onEditingItemKeyChange = { editingItemKey = it },
                isProject = isProject,
                reorderableScope = reorderableScope,
                onDragStarted = onDragStarted,
                onDragStopped = onDragStopped,
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
