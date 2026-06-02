package com.jotty.android.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Stable
class MainTabTopBarState(
    val isOnline: Boolean,
    val isSyncing: Boolean,
    val lastSyncAttemptEpochMs: Long?,
    val lastSyncDurationText: String? = null,
    val lastSyncError: String? = null,
    val onRefresh: () -> Unit,
    val onAdd: () -> Unit,
    val showSyncStatus: Boolean = true,
)

class MainTabTopBarController {
    var state by mutableStateOf<MainTabTopBarState?>(null)

    /** When true, [com.jotty.android.ui.main.MainScreen] hides its tab TopAppBar (list/detail pane). */
    var suppressMainTopBar by mutableStateOf(false)
}

val LocalMainTabTopBarController =
    compositionLocalOf<MainTabTopBarController> {
        error("MainTabTopBarController not provided")
    }

@Composable
fun ProvideMainTabTopBarController(content: @Composable () -> Unit) {
    val controller = remember { MainTabTopBarController() }
    CompositionLocalProvider(LocalMainTabTopBarController provides controller) {
        content()
    }
}

/** Publishes list-tab actions into [MainScreen]'s shared TopAppBar; clears on dispose. */
@Composable
fun RegisterMainTabTopBar(
    state: MainTabTopBarState?,
    suppressMainTopBar: Boolean = false,
) {
    val controller = LocalMainTabTopBarController.current
    SideEffect {
        controller.state = state
        controller.suppressMainTopBar = suppressMainTopBar
    }
    DisposableEffect(Unit) {
        onDispose {
            controller.state = null
            controller.suppressMainTopBar = false
        }
    }
}

@Composable
private fun rememberSyncDetailText(
    lastSyncAttemptEpochMs: Long?,
    lastSyncDurationText: String?,
    lastSyncError: String?,
): String? {
    val lastSyncText =
        remember(lastSyncAttemptEpochMs) {
            lastSyncAttemptEpochMs?.let {
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))
            }
        }
    val syncDurationLabel = stringResource(R.string.sync_duration)
    val syncLastErrorLabel = stringResource(R.string.sync_last_error)
    val lastSyncAttemptTemplate = stringResource(R.string.last_sync_attempt_at)
    return remember(
        lastSyncText,
        lastSyncDurationText,
        lastSyncError,
        syncDurationLabel,
        syncLastErrorLabel,
        lastSyncAttemptTemplate,
    ) {
        when {
            lastSyncText != null || lastSyncDurationText != null || lastSyncError != null ->
                buildString {
                    lastSyncText?.let { append(lastSyncAttemptTemplate.format(it)) }
                    if (lastSyncDurationText != null) {
                        if (isNotEmpty()) append(" • ")
                        append("$syncDurationLabel: $lastSyncDurationText")
                    }
                    if (lastSyncError != null) {
                        if (isNotEmpty()) append(" • ")
                        append("$syncLastErrorLabel: $lastSyncError")
                    }
                }
            else -> null
        }
    }
}

@Composable
fun OfflineSyncStatusIndicator(
    isOnline: Boolean,
    isSyncing: Boolean,
    lastSyncAttemptEpochMs: Long?,
    lastSyncDurationText: String? = null,
    lastSyncError: String? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    var showDetailsDialog by remember { mutableStateOf(false) }
    val statusText =
        when {
            isSyncing -> stringResource(R.string.syncing)
            isOnline -> stringResource(R.string.online)
            else -> stringResource(R.string.server_unreachable)
        }
    val syncDetailText =
        rememberSyncDetailText(
            lastSyncAttemptEpochMs = lastSyncAttemptEpochMs,
            lastSyncDurationText = lastSyncDurationText,
            lastSyncError = lastSyncError,
        )
    val offline = !isOnline && !isSyncing
    val iconSize = if (compact) 18.dp else 20.dp
    val nowMs by
        produceState(initialValue = System.currentTimeMillis(), key1 = lastSyncAttemptEpochMs) {
            if (lastSyncAttemptEpochMs == null) return@produceState
            while (true) {
                value = System.currentTimeMillis()
                delay(60_000)
            }
        }
    val relativeLastSyncText = lastSyncAttemptEpochMs?.let { formatRelativeTime(it, nowMs) }
    val compactStatusText =
        when {
            isSyncing -> statusText
            offline -> statusText
            relativeLastSyncText != null -> stringResource(R.string.last_sync_attempt_at, relativeLastSyncText)
            else -> statusText
        }
    val hasSyncDetails = syncDetailText != null

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
    ) {
        val iconTint =
            if (offline) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
        val iconModifier = Modifier.size(if (offline && !compact) 22.dp else iconSize)
        val icon: @Composable () -> Unit = {
            when {
                isSyncing ->
                    Icon(
                        Icons.Default.CloudQueue,
                        contentDescription = if (compact && hasSyncDetails) stringResource(R.string.sync_details) else statusText,
                        tint = iconTint,
                        modifier = iconModifier,
                    )
                isOnline ->
                    Icon(
                        Icons.Default.CloudDone,
                        contentDescription = if (compact && hasSyncDetails) stringResource(R.string.sync_details) else statusText,
                        tint = iconTint,
                        modifier = iconModifier,
                    )
                else ->
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = if (compact && hasSyncDetails) stringResource(R.string.sync_details) else statusText,
                        tint = iconTint,
                        modifier = iconModifier,
                    )
            }
        }
        if (compact && hasSyncDetails) {
            IconButton(
                onClick = { showDetailsDialog = true },
                modifier = Modifier.size(28.dp),
            ) {
                icon()
            }
        } else {
            icon()
        }
        Column {
            Text(
                text = if (compact) compactStatusText else statusText,
                style =
                    if (offline && !compact) {
                        MaterialTheme.typography.titleSmall
                    } else {
                        MaterialTheme.typography.labelMedium
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color =
                    if (offline) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            if (offline) {
                Text(
                    text = stringResource(R.string.connectivity_status_offline_hint),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!compact && syncDetailText != null) {
                Text(
                    text = syncDetailText,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showDetailsDialog && syncDetailText != null) {
        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            confirmButton = {
                TextButton(onClick = { showDetailsDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            },
            title = { Text(stringResource(R.string.sync_details)) },
            text = { Text(syncDetailText) },
        )
    }
}

@Composable
private fun formatRelativeTime(
    thenEpochMs: Long,
    nowEpochMs: Long,
): String {
    val diffSeconds = ((nowEpochMs - thenEpochMs).coerceAtLeast(0L)) / 1000L
    return when {
        diffSeconds < 60L -> stringResource(R.string.relative_time_just_now)
        diffSeconds < 3600L -> stringResource(R.string.relative_time_minutes_ago, diffSeconds / 60L)
        diffSeconds < 86_400L -> stringResource(R.string.relative_time_hours_ago, diffSeconds / 3600L)
        else -> stringResource(R.string.relative_time_days_ago, diffSeconds / 86_400L)
    }
}

@Composable
fun MainTabTopBarSyncSlot(
    isOnline: Boolean,
    isSyncing: Boolean,
    lastSyncAttemptEpochMs: Long?,
    lastSyncDurationText: String? = null,
    lastSyncError: String? = null,
    modifier: Modifier = Modifier,
) {
    OfflineSyncStatusIndicator(
        isOnline = isOnline,
        isSyncing = isSyncing,
        lastSyncAttemptEpochMs = lastSyncAttemptEpochMs,
        lastSyncDurationText = lastSyncDurationText,
        lastSyncError = lastSyncError,
        modifier = modifier,
        compact = true,
    )
}

@Composable
fun MainTabTopBarActions(state: MainTabTopBarState) {
    val refreshEnabled =
        if (state.showSyncStatus) {
            state.isOnline && !state.isSyncing
        } else {
            !state.isSyncing
        }
    IconButton(onClick = state.onRefresh, enabled = refreshEnabled) {
        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_refresh))
    }
    IconButton(onClick = state.onAdd) {
        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add))
    }
}
