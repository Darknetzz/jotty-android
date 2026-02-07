package com.jotty.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Centered loading spinner for list screens. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Error text with a retry button. */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
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
        error != null -> ErrorState(message = error, onRetry = onRetry)
        isEmpty -> EmptyState(
            icon = emptyIcon,
            title = emptyTitle,
            subtitle = emptySubtitle,
        )
        else -> PullToRefreshBox(
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
                contentDescription = null,
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
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (enabled) {
        val dismissState = rememberSwipeToDismissBoxState()
        LaunchedEffect(dismissState.currentValue) {
            if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
            }
        }
        SwipeToDismissBox(
            state = dismissState,
            modifier = modifier.fillMaxWidth(),
            enableDismissFromStartToEnd = false,
            backgroundContent = {
                Box(
                    modifier = Modifier
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
