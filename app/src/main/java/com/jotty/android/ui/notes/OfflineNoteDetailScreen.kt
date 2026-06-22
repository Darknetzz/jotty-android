package com.jotty.android.ui.notes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import com.jotty.android.data.encryption.BiometricPassphraseStore
import com.jotty.android.data.local.OfflineNotesRepository
import com.jotty.android.ui.common.OfflineConnectivityBanner
import kotlinx.coroutines.launch

/**
 * Wrapper for [NoteDetailScreen] that uses [OfflineNotesRepository] via [OfflineNoteDetailActions].
 */
@Composable
fun OfflineNoteDetailScreen(
    note: Note,
    offlineRepository: OfflineNotesRepository,
    onBack: () -> Unit,
    onUpdate: (Note) -> Unit,
    onDelete: () -> Unit,
    onSaveFailed: () -> Unit = {},
    onSavedLocally: () -> Unit = {},
    imageLoader: ImageLoader? = null,
    jottyServerUrl: String? = null,
    serverCapabilitiesKey: String? = null,
    isOnline: Boolean = false,
    onRetrySync: () -> Unit = {},
    biometricStore: BiometricPassphraseStore? = null,
    biometricAutoUnlockEnabled: Boolean = true,
    biometricSaveOfferEnabled: Boolean = true,
    categorySuggestions: List<String> = emptyList(),
    richEditorEnabled: Boolean = false,
    visualEditorSaveAsMarkdown: Boolean = false,
    noteSnapshotsEnabled: Boolean = true,
    api: JottyApi? = null,
) {
    val scope = rememberCoroutineScope()
    val allNotes by offlineRepository.getNotesFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val liveNote =
        allNotes.find { it.id == note.id }
            ?: offlineRepository.remappedNoteId(note.id)?.let { serverId ->
                allNotes.find { it.id == serverId }
            }
            ?: note
    val actions =
        remember(offlineRepository, isOnline) {
            OfflineNoteDetailActions(
                offlineRepository = offlineRepository,
                isOnline = { isOnline },
                onSavedLocally = onSavedLocally,
            )
        }

    Column(Modifier.fillMaxSize()) {
        OfflineConnectivityBanner(
            isOnline = isOnline,
            onRetrySync = onRetrySync,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        NoteDetailScreen(
            note = liveNote,
            actions = actions,
            modifier = Modifier.weight(1f, fill = true).fillMaxWidth(),
            onBack = onBack,
            onUpdate = onUpdate,
            onDelete = {
                scope.launch {
                    actions.deleteNote(liveNote.id)
                    onDelete()
                }
            },
            onSaveFailed = onSaveFailed,
            imageLoader = imageLoader,
            jottyServerUrl = jottyServerUrl,
            serverCapabilitiesKey = serverCapabilitiesKey,
            biometricStore = biometricStore,
            biometricAutoUnlockEnabled = biometricAutoUnlockEnabled,
            biometricSaveOfferEnabled = biometricSaveOfferEnabled,
            categorySuggestions = categorySuggestions,
            richEditorEnabled = richEditorEnabled,
            visualEditorSaveAsMarkdown = visualEditorSaveAsMarkdown,
            noteSnapshotsEnabled = noteSnapshotsEnabled,
            api = api,
            isOnline = isOnline,
        )
    }
}
