package com.jotty.android.ui.notes

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.encryption.BiometricPassphraseStore
import com.jotty.android.data.preferences.SettingsRepository

/**
 * Wrapper for NotesScreen that adds offline support.
 * Switches between online API calls and offline repository based on settings.
 *
 * [OfflineNotesViewModel] owns the [OfflineNotesRepository] so the network callback
 * and coroutine scope are properly cleaned up in ViewModel.onCleared(), not leaked
 * across recompositions.
 */
@Composable
fun OfflineNotesScreen(
    api: JottyApi,
    settingsRepository: SettingsRepository,
    instanceId: String,
    authFingerprint: String,
    initialNoteId: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    swipeToDeleteEnabled: Boolean = false,
    imageLoader: ImageLoader? = null,
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val biometricStore = remember { BiometricPassphraseStore(context.applicationContext) }
    val vm: OfflineNotesViewModel = viewModel(
        key = "$instanceId|$authFingerprint",
        factory = OfflineNotesViewModel.Factory(application, instanceId, api),
    )
    val offlineRepository = vm.repository
    val offlineModeEnabled by settingsRepository.offlineModeEnabled.collectAsStateWithLifecycle(initialValue = true)

    LaunchedEffect(offlineModeEnabled, instanceId) {
        if (offlineModeEnabled) {
            offlineRepository.syncNotes()
        }
    }

    if (offlineModeEnabled) {
        OfflineEnabledNotesScreen(
            offlineRepository = offlineRepository,
            api = api,
            settingsRepository = settingsRepository,
            initialNoteId = initialNoteId,
            onDeepLinkConsumed = onDeepLinkConsumed,
            swipeToDeleteEnabled = swipeToDeleteEnabled,
            imageLoader = imageLoader,
            biometricStore = biometricStore,
        )
    } else {
        NotesScreen(
            api = api,
            settingsRepository = settingsRepository,
            initialNoteId = initialNoteId,
            onDeepLinkConsumed = onDeepLinkConsumed,
            swipeToDeleteEnabled = swipeToDeleteEnabled,
            imageLoader = imageLoader,
            biometricStore = biometricStore,
        )
    }
}
