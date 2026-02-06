package com.jotty.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.jotty.android.data.preferences.SettingsRepository
import com.jotty.android.ui.main.MainScreen
import com.jotty.android.ui.setup.SetupScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun JottyApp(settingsRepository: SettingsRepository) {
    var isConfigured by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        settingsRepository.migrateFromLegacyIfNeeded()
        isConfigured = settingsRepository.isConfigured.first()
    }

    AnimatedContent(
        targetState = isConfigured,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "nav",
    ) { configured ->
        when (configured) {
            true -> MainScreen(
                settingsRepository = settingsRepository,
                onDisconnect = { isConfigured = false },
            )
            false -> SetupScreen(
                settingsRepository = settingsRepository,
                onConfigured = { isConfigured = true },
            )
            null -> Box(Modifier.fillMaxSize()) { /* Loading */ }
        }
    }
}
