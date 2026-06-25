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
import com.jotty.android.data.encryption.NoteDecryptionSession
import com.jotty.android.data.encryption.NoteEncryption
import com.jotty.android.data.local.NetworkConnectivityMonitor
import com.jotty.android.data.local.OfflineNotesRepository
import com.jotty.android.data.preferences.SettingsRepository
import com.jotty.android.ui.common.ConflictCopiesBanner
import com.jotty.android.ui.common.CloneCategoryDialog
import com.jotty.android.ui.common.ShareServerDialog
import com.jotty.android.util.JOTTY_ARCHIVE_CATEGORY
import com.jotty.android.util.ListDateFormat
import com.jotty.android.util.defaultUnarchiveCategory
import com.jotty.android.util.isArchivedCategory
import com.jotty.android.ui.common.ListFilterHeader
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
import com.jotty.android.util.cloneNoteOffline
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
    apiKey: String? = null,
    serverCapabilitiesKey: String? = null,
    biometricStore: BiometricPassphraseStore? = null,
    tabReselectToken: Int = 0,
) {
    val contentPaddingMode by settingsRepository.contentPaddingMode.collectAsStateWithLifecycle(initialValue = "comfortable")
    val noteListPreviewEnabled by settingsRepository.noteListPreviewEnabled.collectAsStateWithLifecycle(initialValue = true)
    val notePreviewMaxLines by settingsRepository.notePreviewMaxLines.collectAsStateWithLifecycle(
        initialValue = SettingsRepository.DEFAULT_NOTE_PREVIEW_MAX_LINES,
    )
    val showNoteListDates by settingsRepository.showNoteListDates.collectAsStateWithLifecycle(initialValue = true)
    val showNoteListCategories by settingsRepository.showNoteListCategories.collectAsStateWithLifecycle(initialValue = true)
    val listDateFormatKey by settingsRepository.listDateFormat.collectAsStateWithLifecycle(initialValue = "date")
    val listDateFormat = remember(listDateFormatKey) { ListDateFormat.fromKey(listDateFormatKey) }
    val openNotesInEditMode by settingsRepository.openNotesInEditMode.collectAsStateWithLifecycle(initialValue = false)
    val defaultNoteEditMode by settingsRepository.defaultNoteEditMode.collectAsStateWithLifecycle(initialValue = "markdown")
    val markdownEditorMonospace by settingsRepository.markdownEditorMonospace.collectAsStateWithLifecycle(initialValue = false)
    val defaultNoteCategory by settingsRepository.defaultNoteCategory.collectAsStateWithLifecycle(initialValue = null)
    val richNoteEditorEnabled by settingsRepository.richNoteEditorEnabled.collectAsStateWithLifecycle(initialValue = false)
    val visualEditorSaveAsMarkdown by settingsRepository.visualEditorSaveAsMarkdownEnabled.collectAsStateWithLifecycle(initialValue = false)
    val noteSnapshotsEnabled by settingsRepository.noteSnapshotsEnabled.collectAsStateWithLifecycle(initialValue = true)
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
    val sessionRevision by NoteDecryptionSession.revision.collectAsStateWithLifecycle()
    val unlockedNoteIds =
        remember(noteListDisplay.displayItems, sessionRevision) {
            noteListDisplay.displayItems
                .filter { note ->
                    note.encrypted == true || NoteEncryption.isEncrypted(note.content)
                }
                .filter { NoteDecryptionSession.isUnlocked(it.id) }
                .map { it.id }
                .toSet()
        }

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

    var pendingArchiveNote by remember { mutableStateOf<Note?>(null) }
    var pendingCloneNote by remember { mutableStateOf<Note?>(null) }
    var cloneLoading by remember { mutableStateOf(false) }
    var shareServerNote by remember { mutableStateOf<Note?>(null) }
    val noteClonedMsg = stringResource(R.string.note_cloned)
    val cloneFailedMsg = stringResource(R.string.clone_failed)

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

    LaunchedEffect(Unit) {
        vm.categoryFilterEmptyEvents.collect { category ->
            snackbarHostState.showSnackbar(
                message = context.getString(R.string.offline_category_filter_empty, category),
            )
        }
    }

    // Load categories
    LaunchedEffect(isOnline, notes) {
        vm.loadCategories(isOnline, notes)
    }

    // Handle deep link (unfiltered local lookup, then sync when online)
    var deepLinkSyncAttempted by remember(initialNoteId) { mutableStateOf(false) }
    var deepLinkResolved by remember(initialNoteId) { mutableStateOf(false) }
    val noteNotSyncedYetMsg = stringResource(R.string.note_not_synced_yet)
    val shareEncryptedLockedMsg = stringResource(R.string.share_encrypted_note_locked)

    LaunchedEffect(
        initialNoteId,
        notes,
        listRefreshing,
        isOnline,
        deepLinkSyncAttempted,
        deepLinkResolved,
    ) {
        val id = initialNoteId ?: return@LaunchedEffect
        if (deepLinkResolved) return@LaunchedEffect

        suspend fun openIfFound(note: Note?) {
            if (note != null) {
                vm.setSelectedNote(note)
                onDeepLinkConsumed()
                deepLinkResolved = true
            }
        }

        openIfFound(offlineRepository.getNoteById(id))
        if (deepLinkResolved) return@LaunchedEffect

        if (listRefreshing) return@LaunchedEffect

        if (isOnline && !deepLinkSyncAttempted) {
            deepLinkSyncAttempted = true
            requestSync(showLoading = true)
            return@LaunchedEffect
        }

        if (!isOnline && !deepLinkSyncAttempted) {
            deepLinkSyncAttempted = true
            snackbarHostState.showSnackbar(noteNotSyncedYetMsg)
            onDeepLinkConsumed()
            deepLinkResolved = true
            return@LaunchedEffect
        }

        if (deepLinkSyncAttempted && !listRefreshing) {
            openIfFound(offlineRepository.getNoteById(id))
            if (!deepLinkResolved) {
                snackbarHostState.showSnackbar(
                    if (isOnline) noteNotFoundMsg else noteNotSyncedYetMsg,
                )
                onDeepLinkConsumed()
                deepLinkResolved = true
            }
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
    LaunchedEffect(selectedCategory, filterRestored, notes) {
        if (filterRestored) {
            settingsRepository.setNotesCategoryFilter(selectedCategory)
            if (!isOnline) {
                vm.notifyCategoryFilterEmptyIfNeeded(selectedCategory, notes)
            }
        }
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

                    // Search bar + sort + category filters
                    ListFilterHeader(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { vm.setSearchQuery(it) },
                        searchPlaceholderRes = R.string.search_notes,
                        sortOption = sortOption,
                        onSortSelect = { scope.launch { settingsRepository.setListSortOption(it.key) } },
                        categories = noteCategories,
                        selectedCategory = selectedCategory,
                        onClearCategoryFilter = { vm.setSelectedCategory(null) },
                        onCategoryToggle = { vm.toggleCategoryChip(it, notes) },
                        categoryChipPadding = PaddingValues(vertical = 4.dp),
                    )

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
                                                deleteOfflineNoteWithUndo(
                                                    note = n,
                                                    offlineRepository = offlineRepository,
                                                    snackbarHostState = snackbarHostState,
                                                    noteDeletedMsg = noteDeletedMsg,
                                                    undoActionLabel = undoActionLabel,
                                                    deleteFailedMsg = deleteFailedMsg,
                                                    saveFailedMsg = saveFailedMsg,
                                                    savedLocallyMsg = savedLocallyMsg,
                                                    isOnline = isOnline,
                                                    onClearSelectionIfNeeded = {
                                                        if (selectedNote?.id == n.id) vm.setSelectedNote(null)
                                                    },
                                                )
                                            }
                                        },
                                    ) {
                                        NoteListCardWithMenu(
                                            note = n,
                                            onClick = { vm.setSelectedNote(n) },
                                            onDelete = {
                                                scope.launch {
                                                    deleteOfflineNoteWithUndo(
                                                        note = n,
                                                        offlineRepository = offlineRepository,
                                                        snackbarHostState = snackbarHostState,
                                                        noteDeletedMsg = noteDeletedMsg,
                                                        undoActionLabel = undoActionLabel,
                                                        deleteFailedMsg = deleteFailedMsg,
                                                        saveFailedMsg = saveFailedMsg,
                                                        savedLocallyMsg = savedLocallyMsg,
                                                        isOnline = isOnline,
                                                        onClearSelectionIfNeeded = {
                                                            if (selectedNote?.id == n.id) vm.setSelectedNote(null)
                                                        },
                                                    )
                                                }
                                            },
                                            onArchive = { pendingArchiveNote = n },
                                            onClone = { pendingCloneNote = n },
                                            showShare = api != null,
                                            onShare = { shareServerNote = n },
                                            showPreview = noteListPreviewEnabled,
                                            previewMaxLines = notePreviewMaxLines,
                                            showDates = showNoteListDates,
                                            showCategories = showNoteListCategories,
                                            listDateFormat = listDateFormat,
                                            showPendingSync = n.id in dirtyNoteIds,
                                            isUnlockedInSession = n.id in unlockedNoteIds,
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
                        apiKey = apiKey,
                        serverCapabilitiesKey = serverCapabilitiesKey,
                        isOnline = isOnline,
                        onRetrySync = { requestSync(showLoading = true) },
                        biometricStore = biometricStore,
                        biometricAutoUnlockEnabled = biometricAutoUnlockEnabled,
                        biometricSaveOfferEnabled = biometricSaveOfferEnabled,
                        categorySuggestions = noteCategories,
                        richEditorEnabled = richNoteEditorEnabled,
                        visualEditorSaveAsMarkdown = visualEditorSaveAsMarkdown,
                        noteSnapshotsEnabled = noteSnapshotsEnabled,
                        openNotesInEditMode = openNotesInEditMode,
                        defaultNoteEditMode = defaultNoteEditMode,
                        markdownEditorMonospace = markdownEditorMonospace,
                        api = api,
                        onClone = { pendingCloneNote = note },
                    )
                }
            }
        }
    }

    pendingArchiveNote?.let { archiveNote ->
        val archived = isArchivedCategory(archiveNote.category)
        AlertDialog(
            onDismissRequest = { pendingArchiveNote = null },
            title = { Text(stringResource(if (archived) R.string.unarchive else R.string.archive)) },
            text = { Text(stringResource(R.string.archive_note_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = archiveNote
                        pendingArchiveNote = null
                        scope.launch {
                            val newCategory =
                                if (archived) {
                                    defaultUnarchiveCategory()
                                } else {
                                    JOTTY_ARCHIVE_CATEGORY
                                }
                            offlineRepository
                                .updateNote(
                                    noteId = target.id,
                                    title = target.title,
                                    content = target.content,
                                    category = newCategory,
                                ).onFailure {
                                    snackbarHostState.showSnackbar(saveFailedMsg)
                                }
                        }
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingArchiveNote = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    pendingCloneNote?.let { sourceNote ->
        CloneCategoryDialog(
            initialCategory = sourceNote.category,
            categorySuggestions = noteCategories,
            loading = cloneLoading,
            onDismiss = {
                if (!cloneLoading) pendingCloneNote = null
            },
            onConfirm = { targetCategory ->
                scope.launch {
                    cloneLoading = true
                    cloneNoteOffline(offlineRepository, sourceNote, targetCategory)
                        .onSuccess { cloned ->
                            pendingCloneNote = null
                            vm.setSelectedNote(cloned)
                            snackbarHostState.showSnackbar(
                                if (isOnline) noteClonedMsg else savedLocallyMsg,
                            )
                        }
                        .onFailure { error ->
                            snackbarHostState.showSnackbar(
                                "$cloneFailedMsg: ${ApiErrorHelper.userMessage(context, error)}",
                            )
                        }
                    cloneLoading = false
                }
            },
        )
    }

    shareServerNote?.let { shareNote ->
        if (api != null) {
            val displayTitle = shareNote.title.ifBlank { stringResource(R.string.untitled) }
            val exportTitle = stringResource(R.string.export_note)
            val ctx = LocalContext.current
            ShareServerDialog(
                itemType = "note",
                itemId = shareNote.id,
                itemTitle = displayTitle,
                api = api,
                capabilitiesKey = serverCapabilitiesKey,
                onDismiss = { shareServerNote = null },
                onExportText = {
                    val exported =
                        shareNoteTextExport(
                            context = ctx,
                            note = shareNote,
                            chooserTitle = exportTitle,
                        )
                    if (!exported) {
                        scope.launch { snackbarHostState.showSnackbar(shareEncryptedLockedMsg) }
                    }
                    shareServerNote = null
                },
            )
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
            initialCategory = defaultNoteCategory.orEmpty(),
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
