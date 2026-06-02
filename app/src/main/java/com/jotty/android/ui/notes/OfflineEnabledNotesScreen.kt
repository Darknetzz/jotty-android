package com.jotty.android.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import com.jotty.android.R
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import com.jotty.android.data.encryption.BiometricPassphraseStore
import com.jotty.android.data.local.NetworkConnectivityMonitor
import com.jotty.android.data.local.OfflineNotesRepository
import com.jotty.android.data.preferences.SettingsRepository
import com.jotty.android.ui.common.ConflictCopiesBanner
import com.jotty.android.ui.common.ListDetailContainer
import com.jotty.android.ui.common.ListScreenContent
import com.jotty.android.ui.common.rememberStaleListWhileRefresh
import com.jotty.android.ui.common.ListSortOption
import com.jotty.android.ui.common.SortMenuButton
import com.jotty.android.ui.common.sortedBy
import com.jotty.android.ui.common.MainNestedScaffoldContentWindowInsets
import com.jotty.android.ui.common.MainTabTopBarState
import com.jotty.android.ui.common.OfflineConnectivityBanner
import com.jotty.android.ui.common.RegisterMainTabTopBar
import com.jotty.android.ui.common.SwipeToDeleteContainer
import com.jotty.android.ui.common.mainScreenTabContentPadding
import com.jotty.android.ui.common.rememberListScreenState
import com.jotty.android.util.ApiErrorHelper
import kotlinx.coroutines.flow.first
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
    sharedText: String? = null,
    onSharedTextConsumed: () -> Unit = {},
    swipeToDeleteEnabled: Boolean = false,
    imageLoader: ImageLoader? = null,
    jottyServerUrl: String? = null,
    biometricStore: BiometricPassphraseStore? = null,
    tabReselectToken: Int = 0,
) {
    val contentPaddingMode by settingsRepository.contentPaddingMode.collectAsStateWithLifecycle(initialValue = "comfortable")
    val noteListPreviewEnabled by settingsRepository.noteListPreviewEnabled.collectAsStateWithLifecycle(initialValue = true)
    val biometricAutoUnlockEnabled by settingsRepository.biometricAutoUnlockEnabled.collectAsStateWithLifecycle(initialValue = true)
    val biometricSaveOfferEnabled by settingsRepository.biometricSaveOfferEnabled.collectAsStateWithLifecycle(initialValue = true)
    val contentVerticalDp = if (contentPaddingMode == "compact") 8 else 16

    val vm: OfflineEnabledNotesViewModel =
        viewModel {
            OfflineEnabledNotesViewModel(offlineRepository, api)
        }

    // Observe notes from local database
    val notes by offlineRepository.getNotesFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val conflictCopies by offlineRepository.getConflictCopiesFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val isOnline by NetworkConnectivityMonitor.isOnline.collectAsStateWithLifecycle()
    val isSyncing by offlineRepository.isSyncing.collectAsStateWithLifecycle()
    val conflictsDetected by offlineRepository.conflictsDetected.collectAsStateWithLifecycle()
    val dirtyNoteIds by offlineRepository.getDirtyNoteIdsFlow().collectAsStateWithLifecycle(initialValue = emptySet())
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
    val sortKey by settingsRepository.listSortOption.collectAsStateWithLifecycle(initialValue = "updated")
    val sortOption = ListSortOption.fromKey(sortKey)
    val sortedNotes = remember(filteredNotes, sortOption) { filteredNotes.sortedBy(sortOption) }
    var pullRefreshing by remember { mutableStateOf(false) }
    val listRefreshing = screenState.loading || isSyncing
    val noteListDisplay = rememberStaleListWhileRefresh(sortedNotes, listRefreshing)

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
    val pendingSyncLabel = stringResource(R.string.pending_sync)

    fun requestSync(
        showLoading: Boolean = true,
        fromPull: Boolean = false,
    ) {
        scope.launch {
            if (fromPull) pullRefreshing = true
            val showSkeleton = showLoading && sortedNotes.isEmpty()
            try {
                if (!isOnline) return@launch
                if (showSkeleton) screenState.loading = true
                screenState.errorMessage = null
                val result = offlineRepository.syncNotes()
                if (result.isFailure) {
                    val msg =
                        offlineRepository.lastSyncError.value?.takeIf { it.isNotBlank() }
                            ?: ApiErrorHelper.userMessage(
                                context,
                                result.exceptionOrNull() ?: Exception("Sync failed"),
                            )
                    if (notes.isEmpty()) {
                        screenState.errorMessage = msg
                    } else {
                        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
                    }
                }
            } finally {
                if (showSkeleton) screenState.loading = false
                pullRefreshing = false
            }
        }
    }

    LaunchedEffect(conflictsDetected) {
        if (conflictsDetected > 0) {
            scope.launch {
                val result =
                    snackbarHostState.showSnackbar(
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

    var pendingSharedText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            pendingSharedText = sharedText
            vm.setSelectedNote(null)
            vm.setShowCreateDialog(true)
            onSharedTextConsumed()
        }
    }

    // Restore the persisted category filter once, then persist subsequent changes.
    var filterRestored by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        settingsRepository.notesCategoryFilter.first()?.let { vm.setSelectedCategory(it) }
        filterRestored = true
    }
    LaunchedEffect(selectedCategory, filterRestored) {
        if (filterRestored) settingsRepository.setNotesCategoryFilter(selectedCategory)
    }

    BackHandler(enabled = selectedNote != null) { vm.setSelectedNote(null) }
    LaunchedEffect(tabReselectToken) {
        if (selectedNote != null) {
            vm.setSelectedNote(null)
        }
    }

    val inNoteDetail = selectedNote != null
    RegisterMainTabTopBar(
        state =
            if (!inNoteDetail) {
                MainTabTopBarState(
                    isOnline = isOnline,
                    isSyncing = isSyncing,
                    lastSyncAttemptEpochMs = lastSyncAttemptEpochMs,
                    lastSyncDurationText = lastSyncDurationText,
                    lastSyncError = lastSyncError,
                    onRefresh = { requestSync(showLoading = false) },
                    onAdd = { vm.setShowCreateDialog(true) },
                )
            } else {
                null
            },
        suppressMainTopBar = inNoteDetail,
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = MainNestedScaffoldContentWindowInsets,
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .mainScreenTabContentPadding(
                    topComfortDp = if (inNoteDetail) 0 else contentVerticalDp,
                    horizontal = if (inNoteDetail) 0.dp else 16.dp,
                    scaffoldInnerPadding = innerPadding,
                ),
        ) {
            ListDetailContainer(
                target = selectedNote,
                modifier = Modifier.fillMaxSize(),
                contentKey = { it?.id ?: "list" },
            ) { note ->
                if (note == null) {
                    Column(Modifier.fillMaxSize()) {
                    OfflineConnectivityBanner(
                        isOnline = isOnline,
                        onRetrySync = { requestSync(showLoading = true) },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    // Search bar + sort
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { vm.setSearchQuery(it) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.search_notes)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = stringResource(R.string.cd_search),
                                )
                            },
                            singleLine = true,
                        )
                        SortMenuButton(
                            current = sortOption,
                            onSelect = { scope.launch { settingsRepository.setListSortOption(it.key) } },
                        )
                    }

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
                        ConflictCopiesBanner(
                            conflictCopyCount = conflictCopies.size,
                            onViewCopies = { vm.applyConflictSearchFilter() },
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Notes list
                    ListScreenContent(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        showSkeleton = screenState.loading && noteListDisplay.showEmpty,
                        isRefreshing = pullRefreshing,
                        error = screenState.errorMessage,
                        isEmpty = noteListDisplay.showEmpty,
                        onRetry = {
                            requestSync(showLoading = noteListDisplay.showEmpty)
                        },
                        emptyIcon = Icons.AutoMirrored.Filled.Note,
                        emptyTitle = stringResource(R.string.no_notes_yet),
                        emptySubtitle = stringResource(R.string.tap_add_note),
                        onRefresh = {
                            requestSync(showLoading = false, fromPull = true)
                        },
                        content = {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(noteListDisplay.displayItems, key = { it.id }) { n ->
                                    val noteDeleteConfirm =
                                        stringResource(
                                            R.string.delete_note_confirm,
                                            n.title.ifBlank { stringResource(R.string.untitled) },
                                        )
                                    SwipeToDeleteContainer(
                                        enabled = swipeToDeleteEnabled,
                                        deleteConfirmMessage = noteDeleteConfirm,
                                        onDelete = {
                                            scope.launch {
                                                val result = offlineRepository.deleteNote(n.id)
                                                if (result.isFailure) {
                                                    snackbarHostState.showSnackbar(deleteFailedMsg)
                                                } else {
                                                    val snackbarResult =
                                                        snackbarHostState.showSnackbar(
                                                            message = noteDeletedMsg,
                                                            actionLabel = undoActionLabel,
                                                        )
                                                    if (snackbarResult == SnackbarResult.ActionPerformed) {
                                                        val undoResult =
                                                            offlineRepository.createNote(
                                                                title = n.title,
                                                                content = n.content,
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
                                            showPreview = noteListPreviewEnabled,
                                            syncStatusLabel = if (n.id in dirtyNoteIds) pendingSyncLabel else null,
                                        )
                                    }
                                }
                            }
                        },
                    )
                    }
                } else {
                    OfflineNoteDetailScreen(
                        note = note,
                        offlineRepository = offlineRepository,
                        onBack = { vm.setSelectedNote(null) },
                        onUpdate = { updatedNote ->
                            vm.setSelectedNote(updatedNote)
                        },
                        onDelete = {
                            vm.setSelectedNote(null)
                        },
                        onSaveFailed = { scope.launch { snackbarHostState.showSnackbar(saveFailedMsg) } },
                        onSavedLocally = { scope.launch { snackbarHostState.showSnackbar(savedLocallyMsg) } },
                        imageLoader = imageLoader,
                        jottyServerUrl = jottyServerUrl,
                        isOnline = isOnline,
                        onRetrySync = { requestSync(showLoading = true) },
                        biometricStore = biometricStore,
                        biometricAutoUnlockEnabled = biometricAutoUnlockEnabled,
                        biometricSaveOfferEnabled = biometricSaveOfferEnabled,
                        categorySuggestions = noteCategories,
                    )
                }
            }
        }
    }

    // Create note dialog
    if (showCreateDialog) {
        CreateNoteDialog(
            onDismiss = {
                vm.setShowCreateDialog(false)
                pendingSharedText = null
            },
            categorySuggestions = noteCategories,
            initialContent = pendingSharedText.orEmpty(),
            onCreate = { title, content, category ->
                scope.launch {
                    val result =
                        offlineRepository.createNote(
                            title = title,
                            content = content,
                            category = category,
                        )
                    if (result.isSuccess) {
                        pendingSharedText = null
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
        )
    }
}
