package com.jotty.android.ui.notes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.ImageLoader
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
    isOnline: Boolean = false,
    onRetrySync: () -> Unit = {},
    biometricStore: BiometricPassphraseStore? = null,
    biometricAutoUnlockEnabled: Boolean = true,
    biometricSaveOfferEnabled: Boolean = true,
    categorySuggestions: List<String> = emptyList(),
) {
    val scope = rememberCoroutineScope()
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
            note = note,
            actions = actions,
            modifier = Modifier.weight(1f, fill = true).fillMaxWidth(),
            onBack = onBack,
            onUpdate = onUpdate,
            onDelete = {
                scope.launch {
                    actions.deleteNote(note.id)
                    onDelete()
                }
            },
            onSaveFailed = onSaveFailed,
            imageLoader = imageLoader,
            biometricStore = biometricStore,
            biometricAutoUnlockEnabled = biometricAutoUnlockEnabled,
            biometricSaveOfferEnabled = biometricSaveOfferEnabled,
            categorySuggestions = categorySuggestions,
        )
    }
}
