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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import com.jotty.android.data.encryption.NoteDecryptionSession
import com.jotty.android.data.encryption.NoteEncryption
import com.jotty.android.data.encryption.ParsedNoteContent
import com.jotty.android.data.encryption.XChaCha20Decryptor
import com.jotty.android.data.encryption.XChaCha20Encryptor
import com.jotty.android.ui.common.ListScreenContent
import com.jotty.android.ui.common.SwipeToDeleteContainer
import com.jotty.android.util.ApiErrorHelper
import com.jotty.android.util.AppLog
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    api: JottyApi,
    initialNoteId: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    swipeToDeleteEnabled: Boolean = false,
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
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailedMsg = stringResource(R.string.save_failed)
    val deleteFailedMsg = stringResource(R.string.delete_failed)

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
                error = ApiErrorHelper.userMessage(context, e)
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
    Column(Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
        when (val note = selectedNote) {
            null -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.nav_notes),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row {
                        IconButton(onClick = { loadNotes() }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_note))
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.search_notes)) },
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
                            label = { Text(stringResource(R.string.category_all)) },
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

                ListScreenContent(
                    loading = loading,
                    error = error,
                    isEmpty = notes.isEmpty(),
                    onRetry = { loadNotes() },
                    emptyIcon = Icons.Default.Note,
                    emptyTitle = stringResource(R.string.no_notes_yet),
                    emptySubtitle = stringResource(R.string.tap_add_note),
                    onRefresh = { loadNotes() },
                    content = {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(notes, key = { it.id }) { n ->
                                SwipeToDeleteContainer(
                                    enabled = swipeToDeleteEnabled,
                                    onDelete = {
                                        try {
                                            api.deleteNote(n.id)
                                            notes = notes.filter { it.id != n.id }
                                            if (selectedNote?.id == n.id) selectedNote = null
                                        } catch (e: Exception) {
                                            AppLog.e("notes", "Delete note failed", e)
                                            scope.launch { snackbarHostState.showSnackbar(deleteFailedMsg) }
                                        }
                                    },
                                    scope = scope,
                                ) {
                                    NoteCard(
                                        note = n,
                                        onClick = { selectedNote = n },
                                    )
                                }
                            }
                        }
                    },
                )
            }
            else -> NoteDetailScreen(
                note = note,
                api = api,
                onBack = { selectedNote = null },
                onUpdate = { selectedNote = it; loadNotes() },
                onDelete = { selectedNote = null; loadNotes() },
                onSaveFailed = { scope.launch { snackbarHostState.showSnackbar(saveFailedMsg) } },
            )
        }
    }
    }

    if (showCreateDialog) {
        var title by remember { mutableStateOf("") }
        val untitled = stringResource(R.string.untitled)
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.new_note)) },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.title)) },
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
                                        title = title.ifBlank { untitled },
                                    ),
                                )
                                if (created.success) {
                                    loadNotes()
                                    selectedNote = created.data
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
                        text = stringResource(R.string.encrypted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (note.content.isNotBlank()) {
                Text(
                    text = note.content.take(100) + if (note.content.length > 100) "\u2026" else "",
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
    onSaveFailed: () -> Unit = {},
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
        isEncrypted && decryptedContent != null -> decryptedContent
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
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                val ctx = LocalContext.current
                val exportTitle = stringResource(R.string.export_note)
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
                            ctx.startActivity(Intent.createChooser(intent, exportTitle))
                        },
                    ) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.export_share))
                    }
                }
                if (isEncrypted && decryptedContent == null && isEncryptedByContent) {
                    IconButton(onClick = { showDecryptDialog = true }) {
                        Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.decrypt))
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
                                    } catch (_: Exception) { onSaveFailed() }
                                    saving = false
                                }
                            },
                        ) {
                            Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save))
                        }
                    }
                } else if (!isEncrypted) {
                    IconButton(onClick = { showEncryptDialog = true }) {
                        Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.encrypt))
                    }
                    IconButton(onClick = { isEditing = true }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
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
                        } catch (_: Exception) { onSaveFailed() }
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
            text = stringResource(R.string.note_is_encrypted),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when {
                encryptionMethod == "pgp" -> stringResource(R.string.pgp_not_supported)
                canDecryptInApp -> stringResource(R.string.enter_passphrase_to_view)
                else -> stringResource(R.string.use_web_app_to_decrypt)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (canDecryptInApp && encryptionMethod == "xchacha") {
            Button(onClick = onDecryptClick) {
                Text(stringResource(R.string.decrypt_note))
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
    val errorShort = stringResource(R.string.error_passphrase_short)
    val errorMismatch = stringResource(R.string.error_passphrase_mismatch)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.encrypt_note)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.encrypt_passphrase_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it; error = null },
                    label = { Text(stringResource(R.string.passphrase)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; error = null },
                    label = { Text(stringResource(R.string.confirm_passphrase)) },
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
                        passphrase.length < 12 -> error = errorShort
                        passphrase != confirm -> error = errorMismatch
                        else -> onEncrypt(passphrase)
                    }
                },
            ) {
                Text(stringResource(R.string.encrypt))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
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
    val decryptFailedMsg = stringResource(R.string.error_decrypt_failed)
    if (encryptionMethod != "xchacha") {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.decrypt_note)) },
            text = { Text(stringResource(R.string.pgp_not_supported_short)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.decrypt_note)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.decrypt_passphrase_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = {
                        passphrase = it
                        onDecryptError(null)
                    },
                    label = { Text(stringResource(R.string.passphrase)) },
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
                        onDecryptError(decryptFailedMsg)
                    }
                },
            ) {
                Text(stringResource(R.string.decrypt))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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
                syntaxHighlightColor = MaterialTheme.colorScheme.surfaceVariant,
                syntaxHighlightTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(R.string.no_content),
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
            label = { Text(stringResource(R.string.title)) },
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
            placeholder = { Text(stringResource(R.string.write_your_note)) },
            supportingText = {
                Text(
                    stringResource(R.string.markdown_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            minLines = 12,
            shape = RoundedCornerShape(12.dp),
        )
    }
}
