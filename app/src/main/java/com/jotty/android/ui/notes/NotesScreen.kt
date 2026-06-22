package com.jotty.android.ui.notes

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.jotty.android.data.api.CreateNoteRequest
import com.jotty.android.ui.common.ShareServerDialog
import com.jotty.android.util.JOTTY_ARCHIVE_CATEGORY
import com.jotty.android.util.defaultUnarchiveCategory
import com.jotty.android.util.isArchivedCategory
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import com.jotty.android.data.encryption.BiometricPassphraseStore
import com.jotty.android.data.encryption.NoteDecryptionSession
import com.jotty.android.data.encryption.NoteEncryption
import com.jotty.android.data.preferences.SettingsRepository
import com.jotty.android.ui.common.ListFilterHeader
import com.jotty.android.ui.common.ListDetailContainer
import com.jotty.android.ui.common.ListScreenContent
import com.jotty.android.ui.common.ListSortOption
import com.jotty.android.ui.common.SortMenuButton
import com.jotty.android.ui.common.sortedBy
import com.jotty.android.ui.common.MainNestedScaffoldContentWindowInsets
import com.jotty.android.ui.common.MainTabTopBarState
import com.jotty.android.ui.common.RegisterMainTabTopBar
import com.jotty.android.ui.common.SwipeToDeleteContainer
import com.jotty.android.ui.common.mainScreenTabContentPadding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
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
    val application = LocalContext.current.applicationContext as Application
    val context = LocalContext.current
    val vm: NotesViewModel = viewModel(factory = NotesViewModel.Factory(application, api))

    val contentPaddingMode by settingsRepository.contentPaddingMode.collectAsStateWithLifecycle(initialValue = "comfortable")
    val noteListPreviewEnabled by settingsRepository.noteListPreviewEnabled.collectAsStateWithLifecycle(initialValue = true)
    val richNoteEditorEnabled by settingsRepository.richNoteEditorEnabled.collectAsStateWithLifecycle(initialValue = false)
    val visualEditorSaveAsMarkdown by settingsRepository.visualEditorSaveAsMarkdownEnabled.collectAsStateWithLifecycle(initialValue = false)
    val noteSnapshotsEnabled by settingsRepository.noteSnapshotsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val biometricAutoUnlockEnabled by settingsRepository.biometricAutoUnlockEnabled.collectAsStateWithLifecycle(initialValue = true)
    val biometricSaveOfferEnabled by settingsRepository.biometricSaveOfferEnabled.collectAsStateWithLifecycle(initialValue = true)
    val contentVerticalDp = if (contentPaddingMode == "compact") 8 else 16

    val notes by vm.notes.collectAsStateWithLifecycle()
    val selectedNote by vm.selectedNote.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val showCreateDialog by vm.showCreateDialog.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by vm.selectedCategory.collectAsStateWithLifecycle()
    val noteCategories by vm.noteCategories.collectAsStateWithLifecycle()
    val sortKey by settingsRepository.listSortOption.collectAsStateWithLifecycle(initialValue = "updated")
    val sortOption = ListSortOption.fromKey(sortKey)
    val sortedNotes = remember(notes, sortOption) { notes.sortedBy(sortOption) }
    val sessionRevision by NoteDecryptionSession.revision.collectAsStateWithLifecycle()
    val unlockedNoteIds =
        remember(sortedNotes, sessionRevision) {
            sortedNotes
                .filter { note ->
                    note.encrypted == true || NoteEncryption.isEncrypted(note.content)
                }
                .filter { NoteDecryptionSession.isUnlocked(it.id) }
                .map { it.id }
                .toSet()
        }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailedMsg = stringResource(R.string.save_failed)
    val deleteFailedMsg = stringResource(R.string.delete_failed)
    val noteNotFoundMsg = stringResource(R.string.note_not_found)
    val noteDeletedMsg = stringResource(R.string.note_deleted)
    val undoActionLabel = stringResource(R.string.undo)

    var pendingArchiveNote by remember { mutableStateOf<Note?>(null) }
    var shareServerNote by remember { mutableStateOf<Note?>(null) }
    val shareEncryptedLockedMsg = stringResource(R.string.share_encrypted_note_locked)

    var deepLinkResolved by remember(initialNoteId) { mutableStateOf(false) }
    var deepLinkUnfilteredAttempted by remember(initialNoteId) { mutableStateOf(false) }

    LaunchedEffect(initialNoteId, notes, loading, deepLinkResolved, deepLinkUnfilteredAttempted) {
        val id = initialNoteId ?: return@LaunchedEffect
        if (deepLinkResolved) return@LaunchedEffect

        fun openNote(note: Note) {
            vm.setSelectedNote(note)
            onDeepLinkConsumed()
            deepLinkResolved = true
        }

        notes.find { it.id == id }?.let { openNote(it); return@LaunchedEffect }
        if (loading) return@LaunchedEffect

        if (!deepLinkUnfilteredAttempted) {
            deepLinkUnfilteredAttempted = true
            vm.resolveNoteForDeepLink(id)?.let { openNote(it); return@LaunchedEffect }
            vm.loadNotes()
            return@LaunchedEffect
        }

        vm.resolveNoteForDeepLink(id)?.let { openNote(it); return@LaunchedEffect }
        snackbarHostState.showSnackbar(noteNotFoundMsg)
        onDeepLinkConsumed()
        deepLinkResolved = true
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
                    isOnline = true,
                    isSyncing = loading,
                    lastSyncAttemptEpochMs = null,
                    onRefresh = { vm.loadNotes() },
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
                    ListFilterHeader(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { vm.setSearchQuery(it) },
                        searchPlaceholderRes = R.string.search_notes,
                        sortOption = sortOption,
                        onSortSelect = { scope.launch { settingsRepository.setListSortOption(it.key) } },
                        categories = noteCategories,
                        selectedCategory = selectedCategory,
                        onClearCategoryFilter = { vm.setSelectedCategory(null) },
                        onCategoryToggle = { vm.toggleCategoryChip(it) },
                    )

                    ListScreenContent(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        showSkeleton = loading && sortedNotes.isEmpty(),
                        isRefreshing = loading && sortedNotes.isNotEmpty(),
                        error = error,
                        isEmpty = sortedNotes.isEmpty(),
                        onRetry = { vm.loadNotes() },
                        emptyIcon = Icons.AutoMirrored.Filled.Note,
                        emptyTitle = stringResource(R.string.no_notes_yet),
                        emptySubtitle = stringResource(R.string.tap_add_note),
                        onRefresh = { vm.loadNotes() },
                        content = {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(sortedNotes, key = { it.id }) { n ->
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
                                                deleteOnlineNoteWithUndo(
                                                    note = n,
                                                    api = api,
                                                    snackbarHostState = snackbarHostState,
                                                    noteDeletedMsg = noteDeletedMsg,
                                                    undoActionLabel = undoActionLabel,
                                                    deleteFailedMsg = deleteFailedMsg,
                                                    saveFailedMsg = saveFailedMsg,
                                                    onRemovedFromList = { vm.removeNoteFromList(n.id) },
                                                    onReload = { vm.loadNotes() },
                                                )
                                            }
                                        },
                                    ) {
                                        NoteListCardWithMenu(
                                            note = n,
                                            onClick = { vm.setSelectedNote(n) },
                                            onDelete = {
                                                scope.launch {
                                                    deleteOnlineNoteWithUndo(
                                                        note = n,
                                                        api = api,
                                                        snackbarHostState = snackbarHostState,
                                                        noteDeletedMsg = noteDeletedMsg,
                                                        undoActionLabel = undoActionLabel,
                                                        deleteFailedMsg = deleteFailedMsg,
                                                        saveFailedMsg = saveFailedMsg,
                                                        onRemovedFromList = { vm.removeNoteFromList(n.id) },
                                                        onReload = { vm.loadNotes() },
                                                    )
                                                }
                                            },
                                            onArchive = { pendingArchiveNote = n },
                                            showShare = true,
                                            onShare = { shareServerNote = n },
                                            showPreview = noteListPreviewEnabled,
                                            isUnlockedInSession = n.id in unlockedNoteIds,
                                        )
                                    }
                                }
                            }
                        },
                    )
                    }
                } else {
                    val noteActions = remember(api) { ApiNoteDetailActions(api) }
                    NoteDetailScreen(
                        modifier = Modifier.fillMaxSize(),
                        note = note,
                        actions = noteActions,
                        onBack = { vm.setSelectedNote(null) },
                        onUpdate = {
                            vm.setSelectedNote(it)
                            vm.loadNotes()
                        },
                        onDelete = {
                            scope.launch {
                                noteActions.deleteNote(note.id)
                                vm.setSelectedNote(null)
                                vm.loadNotes()
                            }
                        },
                        onSaveFailed = { scope.launch { snackbarHostState.showSnackbar(saveFailedMsg) } },
                        imageLoader = imageLoader,
                        jottyServerUrl = jottyServerUrl,
                        apiKey = apiKey,
                        serverCapabilitiesKey = serverCapabilitiesKey,
                        biometricStore = biometricStore,
                        biometricAutoUnlockEnabled = biometricAutoUnlockEnabled,
                        biometricSaveOfferEnabled = biometricSaveOfferEnabled,
                        categorySuggestions = noteCategories,
                        richEditorEnabled = richNoteEditorEnabled,
                        visualEditorSaveAsMarkdown = visualEditorSaveAsMarkdown,
                        noteSnapshotsEnabled = noteSnapshotsEnabled,
                        api = api,
                    )
                }
            }
        }
    }

    val listNoteActions = remember(api) { ApiNoteDetailActions(api) }

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
                            listNoteActions
                                .updateNote(
                                    noteId = target.id,
                                    title = target.title,
                                    content = target.content,
                                    category = newCategory,
                                    originalCategory = target.category,
                                ).onSuccess {
                                    vm.loadNotes()
                                }.onFailure {
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

    shareServerNote?.let { shareNote ->
        val displayTitle = shareNote.title.ifBlank { stringResource(R.string.untitled) }
        val exportTitle = stringResource(R.string.export_note)
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
                        context = context,
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
                    try {
                        val created =
                            api.createNote(
                                CreateNoteRequest(
                                    title = title,
                                    content = content,
                                    category = category,
                                ),
                            )
                        if (created.success) {
                            pendingSharedText = null
                            vm.loadNotes()
                            vm.setSelectedNote(created.data)
                            vm.setShowCreateDialog(false)
                        }
                    } catch (_: Exception) {
                        scope.launch { snackbarHostState.showSnackbar(saveFailedMsg) }
                    }
                }
            },
        )
    }
}
