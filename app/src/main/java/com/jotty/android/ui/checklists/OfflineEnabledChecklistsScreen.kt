package com.jotty.android.ui.checklists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jotty.android.R
import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.local.OfflineChecklistsRepository
import com.jotty.android.data.preferences.SettingsRepository
import com.jotty.android.ui.common.ListScreenContent
import com.jotty.android.ui.common.MainNestedScaffoldContentWindowInsets
import com.jotty.android.ui.common.mainScreenTabContentPadding
import com.jotty.android.util.ApiErrorHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OfflineEnabledChecklistsScreen(
    offlineRepository: OfflineChecklistsRepository,
    api: JottyApi,
    vmKey: String,
    settingsRepository: SettingsRepository,
    swipeToDeleteEnabled: Boolean = false,
) {
    val contentPaddingMode by settingsRepository.contentPaddingMode.collectAsState(initial = "comfortable")
    val contentVerticalDp = if (contentPaddingMode == "compact") 8 else 16

    val vm: OfflineEnabledChecklistsViewModel = viewModel(key = vmKey) {
        OfflineEnabledChecklistsViewModel(offlineRepository, api)
    }

    val checklists by offlineRepository.getChecklistsFlow().collectAsState(initial = emptyList())
    val isOnline by offlineRepository.isOnline.collectAsState()
    val isSyncing by offlineRepository.isSyncing.collectAsState()
    val conflictsDetected by offlineRepository.conflictsDetected.collectAsState()

    val selectedList by vm.selectedList.collectAsState()
    val showCreateDialog by vm.showCreateDialog.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val selectedCategory by vm.selectedCategory.collectAsState()
    val checklistCategories by vm.checklistCategories.collectAsState()
    val filteredChecklists by vm.filteredChecklists.collectAsState()

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailedMsg = stringResource(R.string.save_failed)
    val deleteFailedMsg = stringResource(R.string.delete_failed)
    val savedLocallyMsg = stringResource(R.string.saved_locally)

    fun requestSync(showLoading: Boolean = true) {
        scope.launch {
            if (!isOnline) return@launch
            if (showLoading) loading = true
            error = null
            val result = offlineRepository.syncChecklists()
            if (result.isFailure) {
                error = ApiErrorHelper.userMessage(
                    context,
                    result.exceptionOrNull() ?: Exception("Sync failed"),
                )
            }
            if (showLoading) loading = false
        }
    }

    LaunchedEffect(conflictsDetected) {
        if (conflictsDetected > 0) {
            val msg = context.getString(R.string.sync_conflicts_detected, conflictsDetected)
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = msg,
                    actionLabel = context.getString(R.string.view_conflicts),
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) vm.applyConflictSearchFilter()
                offlineRepository.clearConflictNotification()
            }
        }
    }

    LaunchedEffect(isOnline, checklists) {
        vm.loadCategories(isOnline, checklists)
    }

    BackHandler(enabled = selectedList != null) { vm.setSelectedList(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = MainNestedScaffoldContentWindowInsets,
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .mainScreenTabContentPadding(
                    topComfortDp = contentVerticalDp,
                    scaffoldInnerPadding = innerPadding,
                ),
        ) {
            val currentList = selectedList
            if (currentList != null) {
                OfflineChecklistDetailContent(
                    checklist = currentList,
                    offlineRepository = offlineRepository,
                    isOnline = isOnline,
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
                )
            } else {
                // Header row: status + actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val statusText = when {
                            isSyncing -> stringResource(R.string.syncing)
                            isOnline -> stringResource(R.string.online)
                            else -> stringResource(R.string.offline)
                        }
                        when {
                            isSyncing -> Icon(
                                Icons.Default.CloudQueue, contentDescription = statusText,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            isOnline -> Icon(
                                Icons.Default.CloudDone, contentDescription = statusText,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            else -> Icon(
                                Icons.Default.CloudOff, contentDescription = statusText,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Text(
                            statusText,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row {
                        IconButton(
                            onClick = { requestSync(showLoading = false) },
                            enabled = isOnline && !isSyncing,
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_refresh))
                        }
                        IconButton(onClick = { vm.setShowCreateDialog(true) }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add))
                        }
                    }
                }

                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { vm.setSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.search_notes)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                )

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

                Spacer(modifier = Modifier.height(8.dp))

                ListScreenContent(
                    loading = loading,
                    error = error,
                    isEmpty = filteredChecklists.isEmpty(),
                    onRetry = { requestSync() },
                    emptyIcon = Icons.Default.Checklist,
                    emptyTitle = stringResource(R.string.no_checklists_yet),
                    emptySubtitle = stringResource(R.string.tap_add_checklist),
                    onRefresh = { requestSync() },
                    content = {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(filteredChecklists, key = { it.id }) { list ->
                                OfflineChecklistCard(
                                    checklist = list,
                                    onClick = { vm.setSelectedList(list) },
                                    onDelete = {
                                        scope.launch {
                                            val result = offlineRepository.deleteChecklist(list.id)
                                            if (result.isFailure) {
                                                snackbarHostState.showSnackbar(deleteFailedMsg)
                                            } else if (!isOnline) {
                                                snackbarHostState.showSnackbar(savedLocallyMsg)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    if (showCreateDialog) {
        var title by remember { mutableStateOf("") }
        var isProjectType by remember { mutableStateOf(false) }
        val untitled = stringResource(R.string.untitled)
        AlertDialog(
            onDismissRequest = { vm.setShowCreateDialog(false) },
            title = { Text(stringResource(R.string.new_checklist)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.title)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = isProjectType,
                            onCheckedChange = { isProjectType = it },
                        )
                        Text(
                            stringResource(R.string.task_project_sub_tasks),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val result = offlineRepository.createChecklist(
                                title = title.ifBlank { untitled },
                                type = if (isProjectType) "task" else "simple",
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
                ) { Text(stringResource(R.string.create)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.setShowCreateDialog(false) }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

// ─── Checklist card ──────────────────────────────────────────────────────────

@Composable
private fun OfflineChecklistCard(
    checklist: Checklist,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val completed = checklist.items.count { it.completed }
    val total = checklist.items.size
    val progress = if (total > 0) completed.toFloat() / total else 0f
    val isProject = checklist.type.equals("project", ignoreCase = true) ||
        checklist.type.equals("task", ignoreCase = true)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = checklist.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .pointerInput(Unit) {
                                detectTapGestures(onLongPress = { menuExpanded = true })
                            },
                    )
                    if (isProject) {
                        Text(
                            stringResource(R.string.project_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp),
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
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit)) },
                    onClick = { menuExpanded = false; onClick() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = { menuExpanded = false; onDelete() },
                )
            }
        }
    }
}

// ─── Detail screen ───────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OfflineChecklistDetailContent(
    checklist: Checklist,
    offlineRepository: OfflineChecklistsRepository,
    isOnline: Boolean,
    onBack: () -> Unit,
    onUpdate: (Checklist) -> Unit,
    onDelete: () -> Unit,
    onSaveFailed: () -> Unit,
    onSavedLocally: () -> Unit,
) {
    // Drive item list from the repository flow so offline mutations are reflected immediately.
    val allChecklists by offlineRepository.getChecklistsFlow().collectAsState(initial = emptyList())
    val liveChecklist = allChecklists.find { it.id == checklist.id } ?: checklist
    val items = liveChecklist.items

    var newItemText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val isProject = liveChecklist.type.equals("project", ignoreCase = true) ||
        liveChecklist.type.equals("task", ignoreCase = true)
    val flatItems = remember(items, isProject) {
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

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(
                liveChecklist.title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        }

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
                keyboardActions = KeyboardActions(
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

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "header-todo") {
                SectionLabel(stringResource(R.string.section_to_do, toDo.size))
            }
            items(toDo, key = { "todo-${it.apiPath}-${it.item.text}" }) { flat ->
                OfflineItemRow(
                    item = flat.item,
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
                            handleResult(offlineRepository.updateItem(liveChecklist.id, flat.apiPath, text))
                        }
                    },
                    onAddSubItem = if (isProject && flat.depth == 0) {
                        {
                            scope.launch {
                                handleResult(
                                    offlineRepository.addItem(liveChecklist.id, "", parentIndex = flat.apiPath),
                                )
                            }
                        }
                    } else null,
                )
            }
            item(key = "header-done") {
                SectionLabel(stringResource(R.string.section_completed, done.size))
            }
            items(done, key = { "done-${it.apiPath}-${it.item.text}" }) { flat ->
                OfflineItemRow(
                    item = flat.item,
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
                            handleResult(offlineRepository.updateItem(liveChecklist.id, flat.apiPath, text))
                        }
                    },
                    onAddSubItem = null,
                )
            }
        }
    }
}

// ─── Item row ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OfflineItemRow(
    item: ChecklistItem,
    depth: Int = 0,
    isProject: Boolean = false,
    onCheck: () -> Unit,
    onUncheck: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (String) -> Unit,
    onAddSubItem: (() -> Unit)? = null,
) {
    val indent = (depth * 20).dp
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember(item.text) { mutableStateOf(item.text) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(isEditing) {
        if (isEditing) focusRequester.requestFocus()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(indent))
        Checkbox(
            checked = item.completed,
            onCheckedChange = { if (it) onCheck() else onUncheck() },
        )
        if (isEditing) {
            OutlinedTextField(
                value = editText,
                onValueChange = { editText = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        val trimmed = editText.trim()
                        if (trimmed.isNotBlank()) onUpdate(trimmed)
                        isEditing = false
                    },
                ),
            )
        } else {
            Text(
                text = item.text.ifBlank { stringResource(R.string.item_placeholder) },
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.completed) TextDecoration.LineThrough else null,
                color = if (item.completed) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .clickable { isEditing = true; editText = item.text },
            )
        }
        if (isProject && depth == 0 && onAddSubItem != null) {
            IconButton(onClick = onAddSubItem, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_sub_task),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete_task),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

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
): List<FlatChecklistItem> = items.flatMapIndexed { index, item ->
    val path = if (parentPath.isEmpty()) "$index" else "$parentPath.$index"
    listOf(FlatChecklistItem(item, depth, path)) +
        flattenItems(item.children.orEmpty(), depth + 1, path)
}
