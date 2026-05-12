package com.jotty.android.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
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
import com.jotty.android.data.encryption.BiometricPassphraseStore
import com.jotty.android.data.local.OfflineNotesRepository
import com.jotty.android.data.preferences.SettingsRepository
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jotty.android.ui.common.ListScreenContent
import com.jotty.android.ui.common.OfflineSyncStatusRow
import com.jotty.android.ui.common.MainNestedScaffoldContentWindowInsets
import com.jotty.android.ui.common.rememberListScreenState
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
    biometricStore: BiometricPassphraseStore? = null,
) {
    val contentPaddingMode by settingsRepository.contentPaddingMode.collectAsStateWithLifecycle(initialValue = "comfortable")
    val contentVerticalDp = if (contentPaddingMode == "compact") 8 else 16

    val vm: OfflineEnabledNotesViewModel = viewModel {
        OfflineEnabledNotesViewModel(offlineRepository, api)
    }

    // Observe notes from local database
    val notes by offlineRepository.getNotesFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val conflictCopies by offlineRepository.getConflictCopiesFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val isOnline by offlineRepository.isOnline.collectAsStateWithLifecycle()
    val isSyncing by offlineRepository.isSyncing.collectAsStateWithLifecycle()
    val conflictsDetected by offlineRepository.conflictsDetected.collectAsStateWithLifecycle()
    val lastSyncAttemptEpochMs by offlineRepository.lastSyncAttemptEpochMs.collectAsStateWithLifecycle()
    val lastSyncDurationText by offlineRepository.lastSyncDurationText.collectAsStateWithLifecycle()
    val lastSyncError by offlineRepository.lastSyncError.collectAsStateWithLifecycle()

    val selectedNote by vm.selectedNote.collectAsStateWithLifecycle()
    val screenState = rememberListScreenState()
    val showCreateDialog by vm.showCreateDialog.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by vm.selectedCategory.collectAsStateWithLifecycle()
    val noteCategories by vm.noteCategories.collectAsStateWithLifecycle()
    val filteredNotes by vm.filteredNotes.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailedMsg = stringResource(R.string.save_failed)
    val deleteFailedMsg = stringResource(R.string.delete_failed)
    val noteNotFoundMsg = stringResource(R.string.note_not_found)
    val savedLocallyMsg = stringResource(R.string.saved_locally)
    val noteDeletedMsg = stringResource(R.string.note_deleted)
    val undoActionLabel = stringResource(R.string.undo)
    val conflictMsg = stringResource(R.string.sync_conflicts_detected, conflictsDetected)
    val conflictActionLabel = stringResource(R.string.view_conflicts)
    val syncDurationLabel = stringResource(R.string.sync_duration)
    val syncLastErrorLabel = stringResource(R.string.sync_last_error)

    fun requestSync(showLoading: Boolean = true) {
        scope.launch {
            if (!isOnline) return@launch
            if (showLoading) screenState.loading = true
            screenState.errorMessage = null
            val result = offlineRepository.syncNotes()
            if (result.isFailure) {
                screenState.errorMessage = ApiErrorHelper.userMessage(
                    context,
                    result.exceptionOrNull() ?: Exception("Sync failed"),
                )
            }
            if (showLoading) screenState.loading = false
        }
    }
    
    LaunchedEffect(conflictsDetected) {
        if (conflictsDetected > 0) {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = conflictMsg,
                    actionLabel = conflictActionLabel,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) vm.applyConflictSearchFilter()
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

    LaunchedEffect(filteredNotes, screenState.loading, initialNoteId) {
        if (!screenState.loading && initialNoteId != null && filteredNotes.isNotEmpty() && filteredNotes.none { it.id == initialNoteId }) {
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
                    // Header with sync status and actions; the app bar owns the screen title.
                    OfflineSyncStatusRow(
                        isOnline = isOnline,
                        isSyncing = isSyncing,
                        lastSyncAttemptEpochMs = lastSyncAttemptEpochMs,
                        onRefresh = { requestSync(showLoading = false) },
                        trailingActions = {
                            IconButton(onClick = { vm.setShowCreateDialog(true) }) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.cd_add),
                                )
                            }
                        },
                    )
                    if (lastSyncDurationText != null || lastSyncError != null) {
                        Text(
                            text = buildString {
                                if (lastSyncDurationText != null) append("$syncDurationLabel: $lastSyncDurationText")
                                if (lastSyncError != null) {
                                    if (isNotEmpty()) append(" • ")
                                    append("$syncLastErrorLabel: $lastSyncError")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
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
                            items(noteCategories, key = { it }) { cat ->
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

                    if (conflictCopies.isNotEmpty()) {
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Text(
                                    text = if (conflictCopies.size == 1) {
                                        stringResource(R.string.conflict_copy_pending)
                                    } else {
                                        stringResource(R.string.conflict_copies_pending, conflictCopies.size)
                                    },
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                TextButton(onClick = { vm.applyConflictSearchFilter() }) {
                                    Text(stringResource(R.string.view_conflicts))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Notes list
                    ListScreenContent(
                        loading = screenState.loading,
                        error = screenState.errorMessage,
                        isEmpty = filteredNotes.isEmpty(),
                        onRetry = {
                            requestSync()
                        },
                        emptyIcon = Icons.AutoMirrored.Filled.Note,
                        emptyTitle = stringResource(R.string.no_notes_yet),
                        emptySubtitle = stringResource(R.string.tap_add_note),
                        onRefresh = {
                            requestSync()
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
                                                } else {
                                                    val snackbarResult = snackbarHostState.showSnackbar(
                                                        message = noteDeletedMsg,
                                                        actionLabel = undoActionLabel,
                                                    )
                                                    if (snackbarResult == SnackbarResult.ActionPerformed) {
                                                        val undoResult = offlineRepository.createNote(
                                                            title = n.title,
                                                            content = n.content ?: "",
                                                            category = n.category,
                                                        )
                                                        if (undoResult.isFailure) {
                                                            snackbarHostState.showSnackbar(saveFailedMsg)
                                                        } else if (!isOnline) {
                                                            snackbarHostState.showSnackbar(savedLocallyMsg)
                                                        }
                                                    } else if (!isOnline) {
                                                        snackbarHostState.showSnackbar(savedLocallyMsg)
                                                    }
                                                }
                                                if (selectedNote?.id == n.id) vm.setSelectedNote(null)
                                            }
                                        },
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
                    val debugLoggingEnabled by settingsRepository.debugLoggingEnabled.collectAsStateWithLifecycle(initialValue = false)
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
                        biometricStore = biometricStore,
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
