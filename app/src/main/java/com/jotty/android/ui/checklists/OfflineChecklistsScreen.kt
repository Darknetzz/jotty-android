package com.jotty.android.ui.checklists

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.preferences.SettingsRepository
import com.jotty.android.ui.common.OfflineModeContent

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
    tabReselectToken: Int = 0,
) {
    val application = LocalContext.current.applicationContext as Application
    val vmFactory =
        remember(application, instanceId, api) {
            OfflineChecklistsViewModel.Factory(application, instanceId, api)
        }
    val vm: OfflineChecklistsViewModel =
        viewModel(
            key = "checklists_repo|$instanceId|$authFingerprint",
            factory = vmFactory,
        )
    val offlineRepository = vm.repository
    val offlineModeEnabled by settingsRepository.offlineModeEnabled.collectAsStateWithLifecycle(initialValue = true)

    OfflineModeContent(
        offlineModeEnabled = offlineModeEnabled,
        offlineContent = {
            OfflineEnabledChecklistsScreen(
                offlineRepository = offlineRepository,
                api = api,
                vmKey = "checklists_ui|$instanceId|$authFingerprint",
                settingsRepository = settingsRepository,
                swipeToDeleteEnabled = swipeToDeleteEnabled,
                tabReselectToken = tabReselectToken,
            )
        },
        onlineContent = {
            ChecklistsScreen(
                api = api,
                settingsRepository = settingsRepository,
                swipeToDeleteEnabled = swipeToDeleteEnabled,
                tabReselectToken = tabReselectToken,
            )
        },
    )
}
