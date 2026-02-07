package com.jotty.android.ui.checklists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.JottyApi
import com.jotty.android.ui.common.ListScreenContent
import com.jotty.android.ui.common.SwipeToDeleteContainer
import com.jotty.android.util.ApiErrorHelper
import com.jotty.android.util.AppLog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChecklistsScreen(api: JottyApi, swipeToDeleteEnabled: Boolean = false) {
    var checklists by remember { mutableStateOf<List<Checklist>>(emptyList()) }
    var selectedList by remember { mutableStateOf<Checklist?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailedMsg = stringResource(R.string.save_failed)
    val deleteFailedMsg = stringResource(R.string.delete_failed)

    fun loadChecklists() {
        scope.launch {
            loading = true
            error = null
            try {
                checklists = api.getChecklists().checklists
                AppLog.d("checklists", "Loaded ${checklists.size} checklists")
            } catch (e: Exception) {
                AppLog.e("checklists", "Load failed", e)
                error = ApiErrorHelper.userMessage(context, e)
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadChecklists() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
    Column(Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
        val currentList = selectedList
        if (currentList != null) {
            ChecklistDetailScreen(
                checklist = currentList,
                api = api,
                onBack = { selectedList = null },
                onUpdate = { loadChecklists(); selectedList = it },
                onDelete = { loadChecklists(); selectedList = null },
                onSaveFailed = { scope.launch { snackbarHostState.showSnackbar(saveFailedMsg) } },
                onDeleteFailed = { scope.launch { snackbarHostState.showSnackbar(deleteFailedMsg) } },
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.nav_checklists),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row {
                    IconButton(onClick = { loadChecklists() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_checklist))
                    }
                }
            }

            ListScreenContent(
                loading = loading,
                error = error,
                isEmpty = checklists.isEmpty(),
                onRetry = { loadChecklists() },
                emptyIcon = Icons.Default.Checklist,
                emptyTitle = stringResource(R.string.no_checklists_yet),
                emptySubtitle = stringResource(R.string.tap_add_checklist),
                onRefresh = { loadChecklists() },
                content = {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(checklists, key = { it.id }) { list ->
                            SwipeToDeleteContainer(
                                enabled = swipeToDeleteEnabled,
                                onDelete = {
                                    try {
                                        api.deleteChecklist(list.id)
                                        checklists = checklists.filter { it.id != list.id }
                                        if (selectedList?.id == list.id) selectedList = null
                                    } catch (e: Exception) {
                                        AppLog.e("checklists", "Delete checklist failed", e)
                                        scope.launch { snackbarHostState.showSnackbar(deleteFailedMsg) }
                                    }
                                },
                                scope = scope,
                            ) {
                                ChecklistCard(
                                    checklist = list,
                                    onClick = { selectedList = list },
                                )
                            }
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
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
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
                val untitled = stringResource(R.string.untitled)
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                val created = api.createChecklist(
                                    com.jotty.android.data.api.CreateChecklistRequest(
                                        title = title.ifBlank { untitled },
                                        type = if (isProjectType) "task" else "simple",
                                    ),
                                )
                                if (created.success) {
                                    loadChecklists()
                                    selectedList = created.data
                                    showCreateDialog = false
                                }
                            } catch (_: Exception) {
                                scope.launch { snackbarHostState.showSnackbar(saveFailedMsg) }
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ChecklistCard(
    checklist: Checklist,
    onClick: () -> Unit,
) {
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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = checklist.title,
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
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChecklistDetailScreen(
    checklist: Checklist,
    api: JottyApi,
    onBack: () -> Unit,
    onUpdate: (Checklist) -> Unit,
    onDelete: () -> Unit,
    onSaveFailed: () -> Unit = {},
    onDeleteFailed: () -> Unit = {},
) {
    var items by remember { mutableStateOf(checklist.items) }
    var newItemText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            try {
                val updated = api.getChecklists().checklists.find { it.id == checklist.id }
                if (updated != null) {
                    items = updated.items
                    onUpdate(updated)
                }
            } catch (_: Exception) { onSaveFailed() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(
                checklist.title,
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
                            } catch (_: Exception) { onSaveFailed() }
                        }
                    }
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
            }
        }

        val isProject = checklist.type.equals("project", ignoreCase = true) ||
                checklist.type.equals("task", ignoreCase = true)
        val flatItems = remember(items, isProject) {
            if (isProject) flattenWithDepth(items) else items.mapIndexed { index, item -> FlatItem(item, 0, "$index") }
        }
        val toDo = flatItems.filter { !it.item.completed }
        val completed = flatItems.filter { it.item.completed }
        val total = flatItems.size
        val doneCount = completed.size

        if (total > 0) {
            Text(
                text = stringResource(R.string.done_progress, doneCount, total),
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
                SectionHeader(title = stringResource(R.string.section_to_do, toDo.size))
            }
            items(toDo, key = { "todo-${it.apiPath}-${it.item.text}" }) { flat ->
                ChecklistItemRow(
                    item = flat.item,
                    depth = flat.depth,
                    isProject = isProject,
                    onCheck = {
                        scope.launch {
                            try {
                                api.checkItem(checklist.id, flat.apiPath)
                                refresh()
                            } catch (_: Exception) { onSaveFailed() }
                        }
                    },
                    onUncheck = {
                        scope.launch {
                            try {
                                api.uncheckItem(checklist.id, flat.apiPath)
                                refresh()
                            } catch (_: Exception) { onSaveFailed() }
                        }
                    },
                    onDelete = {
                        scope.launch {
                            try {
                                api.deleteItem(checklist.id, flat.apiPath)
                                refresh()
                            } catch (_: Exception) { onDeleteFailed() }
                        }
                    },
                    onUpdate = {
                        scope.launch {
                            try {
                                api.updateItem(
                                    checklist.id,
                                    flat.apiPath,
                                    com.jotty.android.data.api.UpdateItemRequest(text = it),
                                )
                                refresh()
                            } catch (_: Exception) { onSaveFailed() }
                        }
                    },
                    onAddSubItem = if (isProject && flat.depth == 0) {
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
                                } catch (_: Exception) { onSaveFailed() }
                            }
                        }
                    } else null,
                )
            }
            item(key = "header-completed") {
                SectionHeader(title = stringResource(R.string.section_completed, completed.size))
            }
            items(completed, key = { "done-${it.apiPath}-${it.item.text}" }) { flat ->
                ChecklistItemRow(
                    item = flat.item,
                    depth = flat.depth,
                    isProject = isProject,
                    onCheck = {
                        scope.launch {
                            try {
                                api.checkItem(checklist.id, flat.apiPath)
                                refresh()
                            } catch (_: Exception) { onSaveFailed() }
                        }
                    },
                    onUncheck = {
                        scope.launch {
                            try {
                                api.uncheckItem(checklist.id, flat.apiPath)
                                refresh()
                            } catch (_: Exception) { onSaveFailed() }
                        }
                    },
                    onDelete = {
                        scope.launch {
                            try {
                                api.deleteItem(checklist.id, flat.apiPath)
                                refresh()
                            } catch (_: Exception) { onDeleteFailed() }
                        }
                    },
                    onUpdate = {
                        scope.launch {
                            try {
                                api.updateItem(
                                    checklist.id,
                                    flat.apiPath,
                                    com.jotty.android.data.api.UpdateItemRequest(text = it),
                                )
                                refresh()
                            } catch (_: Exception) { onSaveFailed() }
                        }
                    },
                    onAddSubItem = null,
                )
            }
        }
    }
}

/** Item with display depth and API path (e.g. "0" or "0.0" for nested). */
private data class FlatItem(val item: ChecklistItem, val depth: Int, val apiPath: String)

/** Flatten checklist items with depth and API path for project/task type. */
private fun flattenWithDepth(items: List<ChecklistItem>, depth: Int = 0, parentPath: String = ""): List<FlatItem> {
    return items.flatMapIndexed { index, item ->
        val path = if (parentPath.isEmpty()) "$index" else "$parentPath.$index"
        listOf(FlatItem(item, depth, path)) + flattenWithDepth(item.children.orEmpty(), depth + 1, path)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChecklistItemRow(
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
            IconButton(
                onClick = onAddSubItem,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_sub_task),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete_task),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
