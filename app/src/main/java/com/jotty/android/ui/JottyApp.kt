package com.jotty.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.jotty.android.data.preferences.SettingsRepository
import com.jotty.android.ui.common.LoadingState
import com.jotty.android.ui.main.MainScreen
import com.jotty.android.ui.setup.SetupScreen
import kotlinx.coroutines.flow.first

/**
 * Root composable that decides between [SetupScreen] and [MainScreen].
 *
 * Named `JottyAppContent` to distinguish from [com.jotty.android.JottyApp] (the [android.app.Application]).
 */
@Composable
fun JottyAppContent(
    settingsRepository: SettingsRepository,
    deepLinkNoteId: MutableState<String?>? = null,
) {
    var rootPhase by rememberSaveable { mutableStateOf("loading") }

    LaunchedEffect(Unit) {
        if (rootPhase == "loading") {
            settingsRepository.migrateFromLegacyIfNeeded()
            settingsRepository.migrateThemeToModeAndColorIfNeeded()
            settingsRepository.migrateApiKeysToEncryptedStoreIfNeeded()
            val currentId = settingsRepository.currentInstanceId.first()
            val defaultId = settingsRepository.defaultInstanceId.first()
            if (currentId == null && defaultId != null) {
                settingsRepository.setCurrentInstanceId(defaultId)
            }
            rootPhase = if (settingsRepository.isConfigured.first()) "main" else "setup"
        }
    }

    AnimatedContent(
        targetState = rootPhase,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "nav",
    ) { phase ->
        when (phase) {
            "main" -> MainScreen(
                settingsRepository = settingsRepository,
                onDisconnect = { rootPhase = "setup" },
                deepLinkNoteId = deepLinkNoteId,
            )
            "setup" -> SetupScreen(
                settingsRepository = settingsRepository,
                onConfigured = { rootPhase = "main" },
            )
            else -> LoadingState(Modifier.fillMaxSize())
        }
    }
}
