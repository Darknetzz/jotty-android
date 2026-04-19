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
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.jotty.android.ui.common.MainNestedScaffoldContentWindowInsets
import com.jotty.android.ui.common.mainScreenTabContentPadding
import com.jotty.android.ui.common.SwipeToDeleteContainer
import com.jotty.android.util.ApiErrorHelper
import kotlinx.coroutines.launch

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

    val vm: OfflineEnabledNotesViewModel = viewModel {
        OfflineEnabledNotesViewModel(offlineRepository, api)
    }

    // Observe notes from local database
    val notes by offlineRepository.getNotesFlow().collectAsState(initial = emptyList())
    val isOnline by offlineRepository.isOnline.collectAsState()
    val isSyncing by offlineRepository.isSyncing.collectAsState()
    val conflictsDetected by offlineRepository.conflictsDetected.collectAsState()

    val selectedNote by vm.selectedNote.collectAsState()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val showCreateDialog by vm.showCreateDialog.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val selectedCategory by vm.selectedCategory.collectAsState()
    val noteCategories by vm.noteCategories.collectAsState()
    val filteredNotes by vm.filteredNotes.collectAsState()

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
                    vm.applyConflictSearchFilter()
                }
                offlineRepository.clearConflictNotification()
            }
        }
    }

    // Load categories
    LaunchedEffect(isOnline, notes) {
        vm.loadCategories(isOnline, notes)
    }

    // Handle deep link
    LaunchedEffect(filteredNotes, initialNoteId) {
        val id = initialNoteId ?: return@LaunchedEffect
        filteredNotes.find { it.id == id }?.let { note ->
            vm.setSelectedNote(note)
            onDeepLinkConsumed()
        }
    }

    LaunchedEffect(filteredNotes, loading, initialNoteId) {
        if (!loading && initialNoteId != null && filteredNotes.isNotEmpty() && filteredNotes.none { it.id == initialNoteId }) {
            scope.launch { snackbarHostState.showSnackbar(noteNotFoundMsg) }
            onDeepLinkConsumed()
        }
    }

    BackHandler(enabled = selectedNote != null) { vm.setSelectedNote(null) }

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
                            IconButton(onClick = { vm.setShowCreateDialog(true) }) {
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
                        onValueChange = { vm.setSearchQuery(it) },
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
                                onClick = { vm.setSelectedCategory(null) },
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
                                    onClick = { vm.toggleCategoryChip(cat) },
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
                                                if (selectedNote?.id == n.id) vm.setSelectedNote(null)
                                            }
                                        },
                                        scope = scope,
                                    ) {
                                        NoteCard(
                                            note = n,
                                            onClick = { vm.setSelectedNote(n) },
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
                        onBack = { vm.setSelectedNote(null) },
                        onUpdate = { updatedNote ->
                            vm.setSelectedNote(updatedNote)
                        },
                        onDelete = {
                            vm.setSelectedNote(null)
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
            onDismissRequest = { vm.setShowCreateDialog(false) },
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
                                vm.setSelectedNote(result.getOrNull())
                                vm.setShowCreateDialog(false)
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
                TextButton(onClick = { vm.setShowCreateDialog(false) }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
