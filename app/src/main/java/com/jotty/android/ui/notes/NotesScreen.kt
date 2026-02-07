package com.jotty.android.ui.notes

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import com.jotty.android.data.encryption.NoteDecryptionSession
import com.jotty.android.data.encryption.NoteEncryption
import com.jotty.android.data.encryption.ParsedNoteContent
import com.jotty.android.data.encryption.XChaCha20Decryptor
import com.jotty.android.data.encryption.XChaCha20Encryptor
import com.jotty.android.util.AppLog
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    api: JottyApi,
    initialNoteId: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var selectedNote by remember { mutableStateOf<Note?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var noteCategories by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun loadNotes() {
        scope.launch {
            loading = true
            error = null
            try {
                notes = api.getNotes(
                    category = selectedCategory,
                    search = searchQuery.takeIf { it.isNotBlank() },
                ).notes
                AppLog.d("notes", "Loaded ${notes.size} notes")
            } catch (e: Exception) {
                AppLog.e("notes", "Load failed", e)
                error = e.message ?: "Failed to load. Check connection and try again."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(selectedCategory, searchQuery) { loadNotes() }
    LaunchedEffect(Unit) {
        try {
            noteCategories = api.getCategories().categories.notes.map { it.name }.distinct()
        } catch (_: Exception) { noteCategories = emptyList() }
    }
    LaunchedEffect(notes, initialNoteId) {
        val id = initialNoteId ?: return@LaunchedEffect
        notes.find { it.id == id }?.let { note ->
            selectedNote = note
            onDeepLinkConsumed()
        }
    }

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
                    Row {
                        IconButton(onClick = { loadNotes() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "New note")
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    placeholder = { Text("Search notes") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                )
                if (noteCategories.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null; loadNotes() },
                            label = { Text("All") },
                        )
                        noteCategories.take(5).forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat; loadNotes() },
                                label = { Text(cat) },
                            )
                        }
                    }
                }

                when {
                    loading && notes.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    error != null -> {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = error!!,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { loadNotes() }) { Text("Retry") }
                        }
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
                                Text(
                                    "Tap + to add a note",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(notes, key = { it.id }) { n ->
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        if (value == SwipeToDismissBoxValue.EndToStart) {
                                            scope.launch {
                                                try {
                                                    api.deleteNote(n.id)
                                                    notes = notes.filter { it.id != n.id }
                                                    if (selectedNote?.id == n.id) selectedNote = null
                                                } catch (e: Exception) {
                                                    AppLog.e("notes", "Delete note failed", e)
                                                }
                                            }
                                            true
                                        } else false
                                    },
                                )
                                SwipeToDismissBox(
                                    state = dismissState,
                                    modifier = Modifier.fillMaxWidth(),
                                    enableDismissFromStartToEnd = false,
                                    backgroundContent = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.error)
                                                .padding(horizontal = 20.dp),
                                            contentAlignment = Alignment.CenterEnd,
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.onError,
                                            )
                                        }
                                    },
                                ) {
                                    NoteCard(
                                        note = n,
                                        onClick = { selectedNote = n },
                                    )
                                }
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
    val isEncrypted = note.encrypted == true || NoteEncryption.isEncrypted(note.content)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (isEncrypted) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Encrypted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (note.content.isNotBlank()) {
                Text(
                    text = note.content.take(100) + if (note.content.length > 100) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 2,
                )
            }
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (note.category.isNotBlank() && note.category != "Uncategorized") {
                    Text(
                        text = note.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (note.updatedAt.isNotBlank()) {
                    Text(
                        text = formatNoteDate(note.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatNoteDate(updatedAt: String): String {
    return try {
        val iso = updatedAt.replace("Z", "+00:00")
        val i = iso.indexOf('T')
        if (i > 0) iso.substring(0, i) else updatedAt.take(10)
    } catch (_: Exception) { updatedAt.take(10) }
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
    var decryptedContent by remember { mutableStateOf<String?>(null) }
    var showDecryptDialog by remember { mutableStateOf(false) }
    var showEncryptDialog by remember { mutableStateOf(false) }
    var decryptError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val parsed = remember(content) { NoteEncryption.parse(content) }
    val isEncryptedByContent = parsed is ParsedNoteContent.Encrypted
    val isEncrypted = note.encrypted == true || isEncryptedByContent
    val displayContent = when {
        isEncrypted && decryptedContent != null -> decryptedContent!!
        isEncrypted -> null
        else -> content
    }

    LaunchedEffect(note) {
        title = note.title
        content = note.content
        decryptedContent = NoteDecryptionSession.get(note.id)
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
                val ctx = LocalContext.current
                if (!isEditing && (displayContent != null || content.isNotBlank())) {
                    IconButton(
                        onClick = {
                            val text = (displayContent ?: content).trim()
                            val shareText = if (text.isNotBlank()) "# $title\n\n$text" else title
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TITLE, title)
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            ctx.startActivity(Intent.createChooser(intent, "Export note"))
                        },
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Export / Share")
                    }
                }
                if (isEncrypted && decryptedContent == null && isEncryptedByContent) {
                    IconButton(onClick = { showDecryptDialog = true }) {
                        Icon(Icons.Default.Lock, contentDescription = "Decrypt")
                    }
                } else if (isEditing) {
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
                } else if (!isEncrypted) {
                    IconButton(onClick = { showEncryptDialog = true }) {
                        Icon(Icons.Default.Lock, contentDescription = "Encrypt")
                    }
                    IconButton(onClick = { isEditing = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                }
            },
        )

        when {
            isEncrypted && decryptedContent == null -> EncryptedNotePlaceholder(
                encryptionMethod = (parsed as? ParsedNoteContent.Encrypted)?.encryptionMethod ?: "xchacha",
                canDecryptInApp = isEncryptedByContent,
                onDecryptClick = { showDecryptDialog = true },
            )
            isEditing -> NoteEditor(
                title = title,
                onTitleChange = { title = it },
                content = content,
                onContentChange = { content = it },
            )
            else -> NoteView(
                title = title,
                content = displayContent ?: "",
            )
        }
    }

    if (showEncryptDialog) {
        EncryptNoteDialog(
            onDismiss = { showEncryptDialog = false },
            onEncrypt = { passphrase ->
                val body = XChaCha20Encryptor.encrypt(displayContent ?: content, passphrase)
                if (body != null) {
                    val fullContent = XChaCha20Encryptor.wrapWithFrontmatter(note.id, title, note.category, body)
                    scope.launch {
                        try {
                            val updated = api.updateNote(
                                note.id,
                                com.jotty.android.data.api.UpdateNoteRequest(
                                    title = title,
                                    content = fullContent,
                                    originalCategory = note.category,
                                ),
                            )
                            if (updated.success) {
                                onUpdate(updated.data)
                                showEncryptDialog = false
                            }
                        } catch (_: Exception) {}
                    }
                }
            },
        )
    }
    if (showDecryptDialog && parsed is ParsedNoteContent.Encrypted) {
        DecryptNoteDialog(
            encryptionMethod = parsed.encryptionMethod,
            encryptedBody = parsed.encryptedBody,
            onDismiss = {
                showDecryptDialog = false
                decryptError = null
            },
            onDecrypted = {
                decryptedContent = it
                NoteDecryptionSession.put(note.id, it)
                showDecryptDialog = false
                decryptError = null
            },
            decryptError = decryptError,
            onDecryptError = { decryptError = it },
        )
    }
}

@Composable
private fun EncryptedNotePlaceholder(
    encryptionMethod: String,
    canDecryptInApp: Boolean,
    onDecryptClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "This note is encrypted",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when {
                encryptionMethod == "pgp" -> "PGP decryption is not supported in the app. Use the Jotty web app to decrypt."
                canDecryptInApp -> "Enter your passphrase to view the content."
                else -> "Use the Jotty web app to decrypt this note."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (canDecryptInApp && encryptionMethod == "xchacha") {
            Button(onClick = onDecryptClick) {
                Text("Decrypt note")
            }
        }
    }
}

@Composable
private fun EncryptNoteDialog(
    onDismiss: () -> Unit,
    onEncrypt: (String) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Encrypt note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Choose a passphrase. You'll need it to decrypt this note. Store it safely.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it; error = null },
                    label = { Text("Passphrase") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; error = null },
                    label = { Text("Confirm passphrase") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        passphrase.length < 12 -> error = "Use at least 12 characters"
                        passphrase != confirm -> error = "Passphrases don't match"
                        else -> onEncrypt(passphrase)
                    }
                },
            ) {
                Text("Encrypt")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DecryptNoteDialog(
    encryptionMethod: String,
    encryptedBody: String,
    onDismiss: () -> Unit,
    onDecrypted: (String) -> Unit,
    decryptError: String?,
    onDecryptError: (String?) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    if (encryptionMethod != "xchacha") {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Decrypt note") },
            text = { Text("PGP decryption is not supported in the app. Use the Jotty web app.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Decrypt note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter the passphrase you used to encrypt this note.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = {
                        passphrase = it
                        onDecryptError(null)
                    },
                    label = { Text("Passphrase") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = decryptError != null,
                    supportingText = decryptError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val decrypted = XChaCha20Decryptor.decrypt(encryptedBody, passphrase)
                    if (decrypted != null) {
                        onDecrypted(decrypted)
                    } else {
                        onDecryptError("Wrong passphrase or invalid format")
                    }
                },
            ) {
                Text("Decrypt")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
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
