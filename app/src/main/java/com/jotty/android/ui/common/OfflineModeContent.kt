package com.jotty.android.ui.common

import androidx.compose.runtime.Composable

@Composable
fun OfflineModeContent(
    offlineModeEnabled: Boolean,
    offlineContent: @Composable () -> Unit,
    onlineContent: @Composable () -> Unit,
) {
    if (offlineModeEnabled) {
        offlineContent()
    } else {
        onlineContent()
    }
}
