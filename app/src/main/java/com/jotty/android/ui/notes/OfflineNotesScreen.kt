package com.jotty.android.ui.notes

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.local.JottyDatabase
import com.jotty.android.data.local.OfflineNotesRepository
import com.jotty.android.data.preferences.SettingsRepository
import kotlinx.coroutines.launch

/**
 * Wrapper for NotesScreen that adds offline support.
 * Switches between online API calls and offline repository based on settings.
 */
@Composable
fun OfflineNotesScreen(
    api: JottyApi,
    settingsRepository: SettingsRepository,
    instanceId: String,
    initialNoteId: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    swipeToDeleteEnabled: Boolean = false,
    imageLoader: ImageLoader? = null,
) {
    val context = LocalContext.current
    val offlineModeEnabled by settingsRepository.offlineModeEnabled.collectAsState(initial = true)
    
    // Create offline repository (use applicationContext for lifecycle safety)
    val offlineRepository = remember(instanceId) {
        val appContext = context.applicationContext
        val database = JottyDatabase.getDatabase(appContext)
        OfflineNotesRepository(appContext, database, instanceId, api)
    }

    val scope = rememberCoroutineScope()
    
    // Initial sync when offline mode is enabled
    LaunchedEffect(offlineModeEnabled, instanceId) {
        if (offlineModeEnabled) {
            scope.launch {
                offlineRepository.syncNotes()
            }
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
        )
    } else {
        // Use original online-only screen
        NotesScreen(
            api = api,
            settingsRepository = settingsRepository,
            initialNoteId = initialNoteId,
            onDeepLinkConsumed = onDeepLinkConsumed,
            swipeToDeleteEnabled = swipeToDeleteEnabled,
            imageLoader = imageLoader,
        )
    }
}
