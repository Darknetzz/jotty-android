package com.jotty.android.ui.checklists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.JottyApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistsScreen(api: JottyApi) {
    var checklists by remember { mutableStateOf<List<Checklist>>(emptyList()) }
    var selectedList by remember { mutableStateOf<Checklist?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadChecklists() {
        scope.launch {
            loading = true
            error = null
            try {
                checklists = api.getChecklists().checklists
            } catch (e: Exception) {
                error = e.message ?: "Failed to load"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadChecklists() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (selectedList != null) {
            ChecklistDetailScreen(
                checklist = selectedList!!,
                api = api,
                onBack = { selectedList = null },
                onUpdate = { loadChecklists(); selectedList = it },
                onDelete = { loadChecklists(); selectedList = null },
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Checklists",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "New checklist")
                }
            }

            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                checklists.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Checklist,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No checklists yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(checklists, key = { it.id }) { list ->
                            ChecklistCard(
                                checklist = list,
                                onClick = { selectedList = list },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var title by remember { mutableStateOf("") }
        var isProjectType by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New checklist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
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
                            "Task project (sub-tasks)",
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
                            try {
                                val created = api.createChecklist(
                                    com.jotty.android.data.api.CreateChecklistRequest(
                                        title = title.ifBlank { "Untitled" },
                                        type = if (isProjectType) "project" else "simple",
                                    ),
                                )
                                if (created.success) {
                                    loadChecklists()
                                    selectedList = created.data
                                    showCreateDialog = false
                                }
                            } catch (_: Exception) {}
                        }
                    },
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
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

    val isProject = checklist.type.equals("project", ignoreCase = true)
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
                        "Project",
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
                    text = "$completed / $total",
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
            } catch (_: Exception) {}
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                placeholder = { Text("Add item...") },
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
                            } catch (_: Exception) {}
                        }
                    }
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }

        val isProject = checklist.type.equals("project", ignoreCase = true)
        val flatItems = remember(items, isProject) {
            if (isProject) flattenWithDepth(items) else items.map { it to 0 }
        }
        val toDo = flatItems.filter { !it.first.completed }
        val completed = flatItems.filter { it.first.completed }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "header-todo") {
                SectionHeader(title = "To Do", count = toDo.size)
            }
            items(toDo, key = { "todo-${it.first.index}-${it.first.text}" }) { (item, depth) ->
                ChecklistItemRow(
                    item = item,
                    depth = depth,
                    isProject = isProject,
                    onCheck = {
                        scope.launch {
                            try {
                                api.checkItem(checklist.id, itemIndexForApi(item))
                                refresh()
                            } catch (_: Exception) {}
                        }
                    },
                    onUncheck = {
                        scope.launch {
                            try {
                                api.uncheckItem(checklist.id, itemIndexForApi(item))
                                refresh()
                            } catch (_: Exception) {}
                        }
                    },
                    onDelete = {
                        scope.launch {
                            try {
                                api.deleteItem(checklist.id, itemIndexForApi(item))
                                refresh()
                            } catch (_: Exception) {}
                        }
                    },
                    onAddSubItem = if (isProject) {
                        {
                            scope.launch {
                                try {
                                    api.addChecklistItem(
                                        checklist.id,
                                        com.jotty.android.data.api.AddItemRequest(
                                            text = "",
                                            parentIndex = "${item.index}",
                                        ),
                                    )
                                    refresh()
                                } catch (_: Exception) {}
                            }
                        }
                    } else null,
                )
            }
            item(key = "header-completed") {
                SectionHeader(title = "Completed", count = completed.size)
            }
            items(completed, key = { "done-${it.first.index}-${it.first.text}" }) { (item, depth) ->
                ChecklistItemRow(
                    item = item,
                    depth = depth,
                    isProject = isProject,
                    onCheck = {
                        scope.launch {
                            try {
                                api.checkItem(checklist.id, itemIndexForApi(item))
                                refresh()
                            } catch (_: Exception) {}
                        }
                    },
                    onUncheck = {
                        scope.launch {
                            try {
                                api.uncheckItem(checklist.id, itemIndexForApi(item))
                                refresh()
                            } catch (_: Exception) {}
                        }
                    },
                    onDelete = {
                        scope.launch {
                            try {
                                api.deleteItem(checklist.id, itemIndexForApi(item))
                                refresh()
                            } catch (_: Exception) {}
                        }
                    },
                    onAddSubItem = null,
                )
            }
        }
    }
}

/** Flatten checklist items with depth (0 = top-level, 1 = child, ...) for project type. */
private fun flattenWithDepth(items: List<ChecklistItem>, depth: Int = 0): List<Pair<ChecklistItem, Int>> {
    return items.flatMap { item ->
        listOf(item to depth) + flattenWithDepth(item.children.orEmpty(), depth + 1)
    }
}

/** API expects item index as string; for children might be "parent.child". */
private fun itemIndexForApi(item: ChecklistItem): String = "${item.index}"

@Composable
private fun SectionHeader(title: String, count: Int) {
    Text(
        text = "• $title ($count)",
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
    onAddSubItem: (() -> Unit)? = null,
) {
    val indent = (depth * 20).dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (item.completed) onUncheck() else onCheck()
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(indent))
        Checkbox(
            checked = item.completed,
            onCheckedChange = { if (it) onCheck() else onUncheck() },
        )
        Text(
            text = item.text.ifBlank { "..." },
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.completed) TextDecoration.LineThrough else null,
            color = if (item.completed) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (isProject && depth == 0 && onAddSubItem != null) {
            IconButton(
                onClick = onAddSubItem,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add sub-task",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
