package com.jotty.android.ui.checklists

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.preferences.SettingsRepository

/**
 * Wrapper that selects between [OfflineEnabledChecklistsScreen] (offline mode on) and
 * the plain [ChecklistsScreen] (offline mode off), mirroring the notes pattern.
 */
@Composable
fun OfflineChecklistsScreen(
    api: JottyApi,
    settingsRepository: SettingsRepository,
    instanceId: String,
    authFingerprint: String,
    swipeToDeleteEnabled: Boolean = false,
) {
    val application = LocalContext.current.applicationContext as Application
    val vm: OfflineChecklistsViewModel =
        viewModel(
            key = "$instanceId|$authFingerprint",
            factory = OfflineChecklistsViewModel.Factory(application, instanceId, api),
        )
    val offlineRepository = vm.repository
    val offlineModeEnabled by settingsRepository.offlineModeEnabled.collectAsStateWithLifecycle(initialValue = true)

    if (offlineModeEnabled) {
        OfflineEnabledChecklistsScreen(
            offlineRepository = offlineRepository,
            api = api,
            vmKey = "$instanceId|$authFingerprint",
            settingsRepository = settingsRepository,
            swipeToDeleteEnabled = swipeToDeleteEnabled,
        )
    } else {
        ChecklistsScreen(
            api = api,
            settingsRepository = settingsRepository,
            swipeToDeleteEnabled = swipeToDeleteEnabled,
        )
    }
}
