package com.jotty.android.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import com.jotty.android.R
import com.jotty.android.data.api.API_CATEGORY_UNCATEGORIZED
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import com.jotty.android.data.local.OfflineNotesRepository
import com.jotty.android.data.preferences.SettingsRepository
import com.jotty.android.ui.common.ListScreenContent
import com.jotty.android.ui.common.SwipeToDeleteContainer
import com.jotty.android.util.ApiErrorHelper
import com.jotty.android.util.AppLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SEARCH_DEBOUNCE_DELAY_MS = 400L

/**
 * Notes screen with offline support.
 * Uses OfflineNotesRepository for local storage and sync.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineEnabledNotesScreen(
    offlineRepository: OfflineNotesRepository,
    api: JottyApi,
    settingsRepository: SettingsRepository,
    initialNoteId: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    swipeToDeleteEnabled: Boolean = false,
    imageLoader: ImageLoader? = null,
) {
    val contentPaddingMode by settingsRepository.contentPaddingMode.collectAsState(initial = "comfortable")
    val contentVerticalDp = if (contentPaddingMode == "compact") 8 else 16
    
    // Observe notes from local database
    val notes by offlineRepository.getNotesFlow().collectAsState(initial = emptyList())
    val isOnline by offlineRepository.isOnline.collectAsState()
    val isSyncing by offlineRepository.isSyncing.collectAsState()
    val conflictsDetected by offlineRepository.conflictsDetected.collectAsState()
    
    var selectedNote by remember { mutableStateOf<Note?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var debouncedSearchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var noteCategories by remember { mutableStateOf<List<String>>(emptyList()) }
    var filteredNotes by remember { mutableStateOf<List<Note>>(emptyList()) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailedMsg = stringResource(R.string.save_failed)
    val deleteFailedMsg = stringResource(R.string.delete_failed)
    val noteNotFoundMsg = stringResource(R.string.note_not_found)
    val savedLocallyMsg = stringResource(R.string.saved_locally)
    
    // Show conflict notification when conflicts are detected
    LaunchedEffect(conflictsDetected) {
        if (conflictsDetected > 0) {
            val message = context.getString(R.string.sync_conflicts_detected, conflictsDetected)
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = context.getString(R.string.view_conflicts),
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    // Filter to show notes with "(Local copy)" in title
                    searchQuery = "(Local copy)"
                }
                offlineRepository.clearConflictNotification()
            }
        }
    }

    // Debounce search query
    LaunchedEffect(searchQuery) {
        delay(SEARCH_DEBOUNCE_DELAY_MS)
        debouncedSearchQuery = searchQuery
    }

    // Filter notes by search and category
    LaunchedEffect(notes, debouncedSearchQuery, selectedCategory) {
        filteredNotes = when {
            debouncedSearchQuery.isNotBlank() -> {
                offlineRepository.searchNotes(debouncedSearchQuery)
            }
            selectedCategory != null -> {
                offlineRepository.getNotesByCategory(selectedCategory!!)
            }
            else -> notes
        }
    }

    // Load categories
    LaunchedEffect(Unit) {
        try {
            if (isOnline) {
                noteCategories = api.getCategories().categories.notes.map { it.name }.distinct()
            } else {
                // Get categories from local notes
                noteCategories = notes.map { it.category }.distinct()
            }
        } catch (_: Exception) {
            noteCategories = notes.map { it.category }.distinct()
        }
    }

    // Handle deep link
    LaunchedEffect(filteredNotes, initialNoteId) {
        val id = initialNoteId ?: return@LaunchedEffect
        filteredNotes.find { it.id == id }?.let { note ->
            selectedNote = note
            onDeepLinkConsumed()
        }
    }

    LaunchedEffect(filteredNotes, loading, initialNoteId) {
        if (!loading && initialNoteId != null && filteredNotes.isNotEmpty() && filteredNotes.none { it.id == initialNoteId }) {
            scope.launch { snackbarHostState.showSnackbar(noteNotFoundMsg) }
            onDeepLinkConsumed()
        }
    }

    BackHandler(enabled = selectedNote != null) { selectedNote = null }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = contentVerticalDp.dp)
        ) {
            when (val note = selectedNote) {
                null -> {
                    // Header with title, sync status, and actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                stringResource(R.string.nav_notes),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            // Sync status indicator
                            when {
                                isSyncing -> {
                                    Icon(
                                        Icons.Default.CloudQueue,
                                        contentDescription = stringResource(R.string.syncing),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                isOnline -> {
                                    Icon(
                                        Icons.Default.CloudDone,
                                        contentDescription = stringResource(R.string.online),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                else -> {
                                    Icon(
                                        Icons.Default.CloudOff,
                                        contentDescription = stringResource(R.string.offline),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        Row {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        if (isOnline) {
                                            offlineRepository.syncNotes()
                                        }
                                    }
                                },
                                enabled = isOnline && !isSyncing
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.cd_refresh)
                                )
                            }
                            IconButton(onClick = { showCreateDialog = true }) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.cd_add)
                                )
                            }
                        }
                    }

                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        placeholder = { Text(stringResource(R.string.search_notes)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.cd_search)
                            )
                        },
                        singleLine = true,
                    )

                    // Category filter chips
                    if (noteCategories.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { selectedCategory = null },
                                label = {
                                    Text(
                                        stringResource(R.string.all_categories),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                            noteCategories.forEach { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat,
                                    onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                                    label = {
                                        Text(
                                            cat,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Notes list
                    ListScreenContent(
                        loading = loading,
                        error = error,
                        isEmpty = filteredNotes.isEmpty(),
                        onRetry = {
                            scope.launch {
                                if (isOnline) {
                                    offlineRepository.syncNotes()
                                }
                            }
                        },
                        emptyIcon = Icons.AutoMirrored.Filled.Note,
                        emptyTitle = stringResource(R.string.no_notes_yet),
                        emptySubtitle = stringResource(R.string.tap_add_note),
                        onRefresh = {
                            scope.launch {
                                if (isOnline) {
                                    offlineRepository.syncNotes()
                                }
                            }
                        },
                        content = {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(filteredNotes, key = { it.id }) { n ->
                                    SwipeToDeleteContainer(
                                        enabled = swipeToDeleteEnabled,
                                        onDelete = {
                                            scope.launch {
                                                val result = offlineRepository.deleteNote(n.id)
                                                if (result.isFailure) {
                                                    snackbarHostState.showSnackbar(deleteFailedMsg)
                                                } else if (!isOnline) {
                                                    snackbarHostState.showSnackbar(savedLocallyMsg)
                                                }
                                                if (selectedNote?.id == n.id) selectedNote = null
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
                    OfflineNoteDetailScreen(
                        note = note,
                        offlineRepository = offlineRepository,
                        api = api,
                        onBack = { selectedNote = null },
                        onUpdate = { updatedNote -> 
                            selectedNote = updatedNote
                            // Notes list auto-updates via Flow
                        },
                        onDelete = { 
                            selectedNote = null
                            // Notes list auto-updates via Flow
                        },
                        onSaveFailed = { scope.launch { snackbarHostState.showSnackbar(saveFailedMsg) } },
                        onSavedLocally = { scope.launch { snackbarHostState.showSnackbar(savedLocallyMsg) } },
                        debugLoggingEnabled = debugLoggingEnabled,
                        imageLoader = imageLoader,
                        isOnline = isOnline,
                    )
                }
            }
        }
    }

    // Create note dialog
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
                            val result = offlineRepository.createNote(
                                title = title.ifBlank { untitled },
                                content = "",
                                category = API_CATEGORY_UNCATEGORIZED
                            )
                            if (result.isSuccess) {
                                selectedNote = result.getOrNull()
                                showCreateDialog = false
                                if (!isOnline) {
                                    snackbarHostState.showSnackbar(savedLocallyMsg)
                                }
                            } else {
                                snackbarHostState.showSnackbar(saveFailedMsg)
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
