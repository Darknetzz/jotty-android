package com.jotty.android.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.data.api.API_CATEGORY_UNCATEGORIZED
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import com.jotty.android.data.preferences.SettingsRepository
import com.jotty.android.ui.common.ListScreenContent
import com.jotty.android.ui.common.SwipeToDeleteContainer
import com.jotty.android.util.ApiErrorHelper
import com.jotty.android.util.AppLog
import coil.ImageLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    api: JottyApi,
    settingsRepository: SettingsRepository,
    initialNoteId: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    swipeToDeleteEnabled: Boolean = false,
    imageLoader: ImageLoader? = null,
) {
    val contentPaddingMode by settingsRepository.contentPaddingMode.collectAsState(initial = "comfortable")
    val contentVerticalDp = if (contentPaddingMode == "compact") 8 else 16
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var selectedNote by remember { mutableStateOf<Note?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var debouncedSearchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var noteCategories by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailedMsg = stringResource(R.string.save_failed)
    val deleteFailedMsg = stringResource(R.string.delete_failed)
    val noteNotFoundMsg = stringResource(R.string.note_not_found)

    LaunchedEffect(searchQuery) {
        delay(400)
        debouncedSearchQuery = searchQuery
    }

    fun loadNotes() {
        scope.launch {
            loading = true
            error = null
            try {
                notes = api.getNotes(
                    category = selectedCategory,
                    search = debouncedSearchQuery.takeIf { it.isNotBlank() },
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

    LaunchedEffect(selectedCategory, debouncedSearchQuery) { loadNotes() }
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
    LaunchedEffect(notes, loading, initialNoteId) {
        if (!loading && initialNoteId != null && notes.isNotEmpty() && notes.none { it.id == initialNoteId }) {
            scope.launch { snackbarHostState.showSnackbar(noteNotFoundMsg) }
            onDeepLinkConsumed()
        }
    }

    BackHandler(enabled = selectedNote != null) { selectedNote = null }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
    Column(Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp, vertical = contentVerticalDp.dp)) {
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
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_refresh))
                        }
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add))
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.search_notes)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search)) },
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
                    emptyIcon = Icons.AutoMirrored.Filled.Note,
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
            else -> {
                val debugLoggingEnabled by settingsRepository.debugLoggingEnabled.collectAsState(initial = false)
                NoteDetailScreen(
                    note = note,
                    api = api,
                    onBack = { selectedNote = null },
                    onUpdate = { selectedNote = it; loadNotes() },
                    onDelete = { selectedNote = null; loadNotes() },
                    onSaveFailed = { scope.launch { snackbarHostState.showSnackbar(saveFailedMsg) } },
                    debugLoggingEnabled = debugLoggingEnabled,
                    imageLoader = imageLoader,
                )
            }
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

