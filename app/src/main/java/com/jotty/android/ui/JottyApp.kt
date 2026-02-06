package com.jotty.android.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.jotty.android.data.preferences.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun JottyApp(settingsRepository: SettingsRepository) {
    var isConfigured by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
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
