package com.jotty.android.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jotty.android.data.preferences.SettingsRepository
import com.jotty.android.util.resolveReducedMotionEnabled

val LocalReducedMotionEnabled =
    compositionLocalOf {
        false
    }

@Composable
fun ProvideReducedMotion(
    settingsRepository: SettingsRepository,
    content: @Composable () -> Unit,
) {
    val mode by settingsRepository.reducedMotionMode.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    val enabled = resolveReducedMotionEnabled(mode, context)
    CompositionLocalProvider(LocalReducedMotionEnabled provides enabled) {
        content()
    }
}
