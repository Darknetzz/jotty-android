package com.jotty.android.ui.notes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch

@Composable
fun NotesScreen(api: JottyApi) {
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var selectedNote by remember { mutableStateOf<Note?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadNotes() {
        scope.launch {
            loading = true
            error = null
            try {
                notes = api.getNotes().notes
            } catch (e: Exception) {
                error = e.message ?: "Failed to load"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadNotes() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        when (val note = selectedNote) {
            null -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Notes",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "New note")
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
                    notes.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Note,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.outline,
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No notes yet",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(notes, key = { it.id }) { n ->
                                NoteCard(
                                    note = n,
                                    onClick = { selectedNote = n },
                                )
                            }
                        }
                    }
                }
            }
            else -> NoteDetailScreen(
                note = note,
                api = api,
                onBack = { selectedNote = null },
                onUpdate = { selectedNote = it; loadNotes() },
                onDelete = { selectedNote = null; loadNotes() },
            )
        }
    }

    if (showCreateDialog) {
        var title by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New note") },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                val created = api.createNote(
                                    com.jotty.android.data.api.CreateNoteRequest(
                                        title = title.ifBlank { "Untitled" },
                                    ),
                                )
                                if (created.success) {
                                    loadNotes()
                                    selectedNote = created.data
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
private fun NoteCard(note: Note, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (note.content.isNotBlank()) {
                Text(
                    text = note.content.take(100) + if (note.content.length > 100) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 2,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteDetailScreen(
    note: Note,
    api: JottyApi,
    onBack: () -> Unit,
    onUpdate: (Note) -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember { mutableStateOf(note.title) }
    var content by remember { mutableStateOf(note.content) }
    var isEditing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(note) {
        title = note.title
        content = note.content
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (isEditing) {
                    if (saving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(12.dp))
                    } else {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    saving = true
                                    try {
                                        val updated = api.updateNote(
                                            note.id,
                                            com.jotty.android.data.api.UpdateNoteRequest(
                                                title = title,
                                                content = content,
                                                originalCategory = note.category,
                                            ),
                                        )
                                        if (updated.success) {
                                            onUpdate(updated.data)
                                            isEditing = false
                                        }
                                    } catch (_: Exception) {}
                                    saving = false
                                }
                            },
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Save")
                        }
                    }
                } else {
                    IconButton(onClick = { isEditing = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                }
            },
        )

        if (isEditing) {
            NoteEditor(
                title = title,
                onTitleChange = { title = it },
                content = content,
                onContentChange = { content = it },
            )
        } else {
            NoteView(
                title = title,
                content = content,
            )
        }
    }
}

@Composable
private fun NoteView(
    title: String,
    content: String,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        if (content.isNotBlank()) {
            MarkdownText(
                markdown = content,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )
        } else {
            Text(
                text = "No content",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun NoteEditor(
    title: String,
    onTitleChange: (String) -> Unit,
    content: String,
    onContentChange: (String) -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
        OutlinedTextField(
            value = content,
            onValueChange = onContentChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp),
            placeholder = { Text("Write your note...") },
            supportingText = {
                Text(
                    "Supports **bold**, *italic*, # headings, lists, [links](url)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            minLines = 12,
            shape = RoundedCornerShape(12.dp),
        )
    }
}
