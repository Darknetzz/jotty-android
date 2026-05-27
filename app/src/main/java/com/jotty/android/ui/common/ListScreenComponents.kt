package com.jotty.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Stable
class ListScreenState(
    loading: Boolean = false,
    errorMessage: String? = null,
) {
    var loading by mutableStateOf(loading)
    var errorMessage by mutableStateOf(errorMessage)
}

@Composable
fun rememberListScreenState(
    loading: Boolean = false,
    errorMessage: String? = null,
): ListScreenState = remember { ListScreenState(loading, errorMessage) }

/** Centered loading spinner for list screens. */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            if (message != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Prominent warning when the app cannot reach the server (shared connectivity monitor). */
@Composable
fun OfflineConnectivityBanner(
    isOnline: Boolean,
    onRetrySync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isOnline) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.connectivity_not_established_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.connectivity_not_established_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            TextButton(onClick = onRetrySync) {
                Text(
                    stringResource(R.string.connectivity_retry_sync),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
fun OfflineSyncStatusRow(
    isOnline: Boolean,
    isSyncing: Boolean,
    lastSyncAttemptEpochMs: Long?,
    onRefresh: () -> Unit,
    trailingActions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusText =
        when {
            isSyncing -> stringResource(R.string.syncing)
            isOnline -> stringResource(R.string.online)
            else -> stringResource(R.string.server_unreachable)
        }
    val lastSyncText =
        remember(lastSyncAttemptEpochMs) {
            lastSyncAttemptEpochMs?.let {
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))
            }
        }
    val offline = !isOnline && !isSyncing

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (offline) {
                        Modifier.background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                            MaterialTheme.shapes.small,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = if (offline) 8.dp else 0.dp, vertical = if (offline) 6.dp else 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                isSyncing ->
                    Icon(
                        Icons.Default.CloudQueue,
                        contentDescription = statusText,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                isOnline ->
                    Icon(
                        Icons.Default.CloudDone,
                        contentDescription = statusText,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                else ->
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = statusText,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp),
                    )
            }
            Column {
                Text(
                    text = statusText,
                    style =
                        if (offline) {
                            MaterialTheme.typography.titleSmall
                        } else {
                            MaterialTheme.typography.labelLarge
                        },
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (lastSyncText != null) {
                    Text(
                        text = stringResource(R.string.last_sync_attempt_at, lastSyncText),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row {
            IconButton(onClick = onRefresh, enabled = isOnline && !isSyncing) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_refresh))
            }
            trailingActions()
        }
    }
}

/** Error text with a retry button. */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

/**
 * Shared list-screen layout: shows [LoadingState], [ErrorState], [EmptyState], or [PullToRefreshBox] with [content].
 * Use when you have a list that can be loading, in error, empty, or showing items with pull-to-refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreenContent(
    loading: Boolean,
    error: String?,
    isEmpty: Boolean,
    onRetry: () -> Unit,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptySubtitle: String? = null,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit,
) {
    val pullRefreshState = rememberPullToRefreshState()
    when {
        loading && isEmpty -> LoadingState()
        error != null && isEmpty -> ErrorState(message = error, onRetry = onRetry)
        isEmpty ->
            EmptyState(
                icon = emptyIcon,
                title = emptyTitle,
                subtitle = emptySubtitle,
            )
        else ->
            PullToRefreshBox(
                isRefreshing = loading,
                onRefresh = onRefresh,
                state = pullRefreshState,
            ) {
                content()
            }
    }
}

/** Empty-list placeholder with an icon, title, and optional subtitle. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = title,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * Wraps [content] in a swipe-to-delete dismiss box when [enabled].
 * When swiped end-to-start, calls [onDelete] and shows a red delete background.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteContainer(
    enabled: Boolean,
    onDelete: suspend () -> Unit,
    modifier: Modifier = Modifier,
    deleteConfirmMessage: String? = null,
    content: @Composable () -> Unit,
) {
    if (enabled) {
        val dismissState = rememberSwipeToDismissBoxState()
        var showDeleteConfirm by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val resolvedConfirmMessage =
            deleteConfirmMessage ?: stringResource(R.string.delete_confirm_generic)
        LaunchedEffect(dismissState.currentValue) {
            if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                showDeleteConfirm = true
            }
        }
        if (showDeleteConfirm) {
            ConfirmDeleteDialog(
                message = resolvedConfirmMessage,
                onDismiss = {
                    showDeleteConfirm = false
                    scope.launch { dismissState.reset() }
                },
                onConfirm = {
                    showDeleteConfirm = false
                    scope.launch {
                        onDelete()
                        dismissState.reset()
                    }
                },
            )
        }
        SwipeToDismissBox(
            state = dismissState,
            modifier = modifier.fillMaxWidth(),
            enableDismissFromStartToEnd = false,
            backgroundContent = {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.error)
                            .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.onError,
                    )
                }
            },
        ) {
            content()
        }
    } else {
        content()
    }
}
