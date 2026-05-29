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
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import com.jotty.android.data.encryption.BiometricPassphraseStore
import com.jotty.android.data.preferences.SettingsRepository
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
import com.jotty.android.util.AppLog
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
    biometricStore: BiometricPassphraseStore? = null,
    tabReselectToken: Int = 0,
) {
    val application = LocalContext.current.applicationContext as Application
    val vm: NotesViewModel = viewModel(factory = NotesViewModel.Factory(application, api))

    val contentPaddingMode by settingsRepository.contentPaddingMode.collectAsStateWithLifecycle(initialValue = "comfortable")
    val noteListPreviewEnabled by settingsRepository.noteListPreviewEnabled.collectAsStateWithLifecycle(initialValue = true)
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

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailedMsg = stringResource(R.string.save_failed)
    val deleteFailedMsg = stringResource(R.string.delete_failed)
    val noteNotFoundMsg = stringResource(R.string.note_not_found)
    val noteDeletedMsg = stringResource(R.string.note_deleted)
    val undoActionLabel = stringResource(R.string.undo)

    LaunchedEffect(notes, initialNoteId) {
        val id = initialNoteId ?: return@LaunchedEffect
        notes.find { it.id == id }?.let { note ->
            vm.setSelectedNote(note)
            onDeepLinkConsumed()
        }
    }
    LaunchedEffect(notes, loading, initialNoteId) {
        if (!loading && initialNoteId != null && notes.isNotEmpty() && notes.none { it.id == initialNoteId }) {
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

    RegisterMainTabTopBar(
        if (selectedNote == null) {
            MainTabTopBarState(
                isOnline = true,
                isSyncing = loading,
                lastSyncAttemptEpochMs = null,
                onRefresh = { vm.loadNotes() },
                onAdd = { vm.setShowCreateDialog(true) },
                showSyncStatus = false,
            )
        } else {
            null
        },
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = MainNestedScaffoldContentWindowInsets,
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .mainScreenTabContentPadding(
                    topComfortDp = if (selectedNote != null) 4 else contentVerticalDp,
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
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { vm.setSearchQuery(it) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.search_notes)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search)) },
                            singleLine = true,
                        )
                        SortMenuButton(
                            current = sortOption,
                            onSelect = { scope.launch { settingsRepository.setListSortOption(it.key) } },
                        )
                    }
                    if (noteCategories.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedCategory == null,
                                    onClick = { vm.setSelectedCategory(null) },
                                    label = {
                                        Text(
                                            stringResource(R.string.category_all),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                )
                            }
                            items(noteCategories, key = { it }) { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat,
                                    onClick = { vm.setSelectedCategory(cat) },
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

                    ListScreenContent(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        loading = loading,
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
                                            val snapshot = n
                                            try {
                                                api.deleteNote(n.id)
                                                vm.removeNoteFromList(n.id)
                                                scope.launch {
                                                    val result =
                                                        snackbarHostState.showSnackbar(
                                                            message = noteDeletedMsg,
                                                            actionLabel = undoActionLabel,
                                                            duration = SnackbarDuration.Short,
                                                        )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        try {
                                                            val resp =
                                                                api.createNote(
                                                                    CreateNoteRequest(
                                                                        title = snapshot.title,
                                                                        content = snapshot.content,
                                                                        category = snapshot.category,
                                                                    ),
                                                                )
                                                            if (resp.success) {
                                                                vm.loadNotes()
                                                            } else {
                                                                snackbarHostState.showSnackbar(saveFailedMsg)
                                                            }
                                                        } catch (e: Exception) {
                                                            AppLog.e("notes", "Undo delete failed", e)
                                                            snackbarHostState.showSnackbar(saveFailedMsg)
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                AppLog.e("notes", "Delete note failed", e)
                                                scope.launch { snackbarHostState.showSnackbar(deleteFailedMsg) }
                                            }
                                        },
                                    ) {
                                        NoteCard(
                                            note = n,
                                            onClick = { vm.setSelectedNote(n) },
                                            showPreview = noteListPreviewEnabled,
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
                        biometricStore = biometricStore,
                        biometricAutoUnlockEnabled = biometricAutoUnlockEnabled,
                        biometricSaveOfferEnabled = biometricSaveOfferEnabled,
                        categorySuggestions = noteCategories,
                    )
                }
            }
        }
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
