package com.jotty.android.ui.sync

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jotty.android.R
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.local.PendingSyncItem
import com.jotty.android.data.local.SyncBackupKind
import com.jotty.android.ui.common.EmptyState
import com.jotty.android.ui.common.mainScreenTabContentPadding
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
fun PendingSyncScreen(
    api: JottyApi,
    instanceId: String,
    onOpenItem: (SyncBackupKind, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val application = LocalContext.current.applicationContext as Application
    val vm: PendingSyncViewModel =
        viewModel(
            key = "pending-sync|$instanceId",
            factory = PendingSyncViewModel.Factory(application, instanceId, api),
        )
    val items by vm.pendingItems.collectAsStateWithLifecycle()
    val notes = items.filter { it.kind == SyncBackupKind.NOTE }
    val checklists = items.filter { it.kind == SyncBackupKind.CHECKLIST }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .mainScreenTabContentPadding(topComfortDp = 16),
    ) {
        Text(
            text = stringResource(R.string.pending_sync_manager_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.pending_sync_manager_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Default.CloudQueue,
                title = stringResource(R.string.pending_sync_empty),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (notes.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.nav_notes),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    items(notes, key = { "note-${it.id}" }) { item ->
                        PendingSyncRow(item = item, onClick = { onOpenItem(item.kind, item.id) })
                    }
                }
                if (checklists.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.nav_checklists),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    items(checklists, key = { "checklist-${it.id}" }) { item ->
                        PendingSyncRow(item = item, onClick = { onOpenItem(item.kind, item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingSyncRow(
    item: PendingSyncItem,
    onClick: () -> Unit,
) {
    val nowMs by
        produceState(initialValue = System.currentTimeMillis(), key1 = item.dirtySinceEpochMs) {
            while (true) {
                value = System.currentTimeMillis()
                delay(60_000)
            }
        }
    val dirtySince =
        item.dirtySinceEpochMs?.let { epoch ->
            stringResource(R.string.pending_sync_dirty_since, formatPendingRelativeTime(epoch, nowMs))
        }
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = item.title, style = MaterialTheme.typography.titleMedium)
            val badges =
                buildList {
                    if (item.isPendingDelete) add(stringResource(R.string.pending_sync_pending_delete))
                    if (item.isLocalOnly) add(stringResource(R.string.pending_sync_local_only))
                    if (item.pendingOpCount > 0) {
                        add(stringResource(R.string.pending_sync_ops_count, item.pendingOpCount))
                    }
                }
            if (badges.isNotEmpty()) {
                Text(
                    text = badges.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (dirtySince != null) {
                Text(
                    text = dirtySince,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                text = stringResource(R.string.pending_sync_updated_at, item.updatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatPendingRelativeTime(
    epochMs: Long,
    nowMs: Long,
): String {
    val delta = (nowMs - epochMs).coerceAtLeast(0L)
    val minutes = delta / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        else -> DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMs))
    }
}
