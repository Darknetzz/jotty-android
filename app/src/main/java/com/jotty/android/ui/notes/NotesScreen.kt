package com.jotty.android.ui.notes

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
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
import com.jotty.android.ui.common.ListScreenContent
import com.jotty.android.ui.common.MainNestedScaffoldContentWindowInsets
import com.jotty.android.ui.common.SwipeToDeleteContainer
import com.jotty.android.ui.common.mainScreenTabContentPadding
import com.jotty.android.util.AppLog
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
    biometricStore: BiometricPassphraseStore? = null,
) {
    val application = LocalContext.current.applicationContext as Application
    val vm: NotesViewModel = viewModel(factory = NotesViewModel.Factory(application, api))

    val contentPaddingMode by settingsRepository.contentPaddingMode.collectAsStateWithLifecycle(initialValue = "comfortable")
    val contentVerticalDp = if (contentPaddingMode == "compact") 8 else 16

    val notes by vm.notes.collectAsStateWithLifecycle()
    val selectedNote by vm.selectedNote.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val showCreateDialog by vm.showCreateDialog.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by vm.selectedCategory.collectAsStateWithLifecycle()
    val noteCategories by vm.noteCategories.collectAsStateWithLifecycle()

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row {
                            IconButton(onClick = { vm.loadNotes() }) {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_refresh))
                            }
                            IconButton(onClick = { vm.setShowCreateDialog(true) }) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add))
                            }
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { vm.setSearchQuery(it) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        placeholder = { Text(stringResource(R.string.search_notes)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search)) },
                        singleLine = true,
                    )
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
                        loading = loading,
                        error = error,
                        isEmpty = notes.isEmpty(),
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
                                items(notes, key = { it.id }) { n ->
                                    SwipeToDeleteContainer(
                                        enabled = swipeToDeleteEnabled,
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
                                                            if (resp.success && resp.data != null) {
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
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
                else -> {
                    val debugLoggingEnabled by settingsRepository.debugLoggingEnabled.collectAsStateWithLifecycle(initialValue = false)
                    NoteDetailScreen(
                        note = note,
                        api = api,
                        onBack = { vm.setSelectedNote(null) },
                        onUpdate = {
                            vm.setSelectedNote(it)
                            vm.loadNotes()
                        },
                        onDelete = {
                            vm.setSelectedNote(null)
                            vm.loadNotes()
                        },
                        onSaveFailed = { scope.launch { snackbarHostState.showSnackbar(saveFailedMsg) } },
                        debugLoggingEnabled = debugLoggingEnabled,
                        imageLoader = imageLoader,
                        biometricStore = biometricStore,
                    )
                }
            }
        }
    }

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
                            try {
                                val created =
                                    api.createNote(
                                        CreateNoteRequest(
                                            title = title.ifBlank { untitled },
                                        ),
                                    )
                                if (created.success) {
                                    vm.loadNotes()
                                    vm.setSelectedNote(created.data)
                                    vm.setShowCreateDialog(false)
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
                TextButton(onClick = { vm.setShowCreateDialog(false) }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
