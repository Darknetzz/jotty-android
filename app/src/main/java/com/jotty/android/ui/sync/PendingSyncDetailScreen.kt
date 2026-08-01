package com.jotty.android.ui.sync

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jotty.android.R
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.encryption.NoteDecryptionSession
import com.jotty.android.data.encryption.NoteEncryption
import com.jotty.android.data.encryption.ParsedNoteContent
import com.jotty.android.data.local.PendingItemOp
import com.jotty.android.data.local.SyncBackupDirection
import com.jotty.android.data.local.SyncBackupKind
import com.jotty.android.data.local.SyncPayloadCodec
import com.jotty.android.ui.common.InlineAlert
import com.jotty.android.ui.common.InlineAlertVariant
import com.jotty.android.ui.common.mainScreenTabContentPadding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.DateFormat
import java.util.Date

@Composable
fun PendingSyncDetailScreen(
    api: JottyApi,
    instanceId: String,
    kind: SyncBackupKind,
    itemId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val application = LocalContext.current.applicationContext as Application
    val context = LocalContext.current
    val vm: PendingSyncViewModel =
        viewModel(
            key = "pending-sync|$instanceId",
            factory = PendingSyncViewModel.Factory(application, instanceId, api),
        )
    LaunchedEffect(kind, itemId) { vm.loadDetail(kind, itemId) }

    val item by vm.detailItem.collectAsStateWithLifecycle()
    val loading by vm.detailLoading.collectAsStateWithLifecycle()
    val error by vm.actionError.collectAsStateWithLifecycle()
    val localNote by vm.localNote.collectAsStateWithLifecycle()
    val serverNote by vm.serverNote.collectAsStateWithLifecycle()
    val localChecklist by vm.localChecklist.collectAsStateWithLifecycle()
    val serverChecklist by vm.serverChecklist.collectAsStateWithLifecycle()
    val backups by vm.backups.collectAsStateWithLifecycle()

    var confirmPush by remember { mutableStateOf(false) }
    var confirmPull by remember { mutableStateOf(false) }
    var restoreBackupId by remember { mutableStateOf<Long?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val doneMsg = stringResource(R.string.pending_sync_action_done)
    val shareLabel = stringResource(R.string.pending_sync_share_backup)

    LaunchedEffect(error) {
        if (error != null) successMessage = null
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .mainScreenTabContentPadding(topComfortDp = 16)
                .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = item?.title ?: stringResource(R.string.pending_sync_detail_title),
            style = MaterialTheme.typography.titleLarge,
        )
        item?.let { pending ->
            Text(
                text =
                    buildString {
                        append(
                            when (pending.kind) {
                                SyncBackupKind.NOTE -> stringResource(R.string.nav_notes)
                                SyncBackupKind.CHECKLIST -> stringResource(R.string.nav_checklists)
                            },
                        )
                        if (pending.isPendingDelete) {
                            append(" · ")
                            append(stringResource(R.string.pending_sync_pending_delete))
                        }
                        if (pending.isLocalOnly) {
                            append(" · ")
                            append(stringResource(R.string.pending_sync_local_only))
                        }
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            pending.dirtySinceEpochMs?.let { epoch ->
                Text(
                    text =
                        stringResource(
                            R.string.pending_sync_dirty_since,
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(epoch)),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        error?.let { msg ->
            InlineAlert(
                message = msg,
                variant = InlineAlertVariant.Danger,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        successMessage?.let { msg ->
            InlineAlert(
                message = msg,
                variant = InlineAlertVariant.Success,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        if (loading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.pending_sync_diff_heading),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.pending_sync_diff_legend),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        when (kind) {
            SyncBackupKind.NOTE -> {
                val local = localNote
                if (local != null) {
                    val serverText =
                        serverNote?.let { notePayloadDisplayText(it) }
                            ?: stringResource(R.string.pending_sync_no_server_version)
                    val localText = notePayloadDisplayText(local)
                    TextDiffView(oldText = serverText, newText = localText, modifier = Modifier.height(280.dp))
                } else if (!loading) {
                    Text(stringResource(R.string.pending_sync_item_gone))
                }
            }
            SyncBackupKind.CHECKLIST -> {
                val local = localChecklist
                if (local != null) {
                    val ops = parsePendingOps(local.pendingOpsJson)
                    if (ops.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.pending_sync_ops_heading),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        ops.forEach { op ->
                            Text(
                                text = "• ${formatPendingOp(op)}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 2.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    val serverText =
                        serverChecklist?.let { SyncPayloadCodec.checklistDiffText(it) }
                            ?: stringResource(R.string.pending_sync_no_server_version)
                    val localText = SyncPayloadCodec.checklistDiffText(local)
                    TextDiffView(oldText = serverText, newText = localText, modifier = Modifier.height(280.dp))
                } else if (!loading) {
                    Text(stringResource(R.string.pending_sync_item_gone))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.pending_sync_actions_heading),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.pending_sync_backup_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        Button(
            onClick = { confirmPush = true },
            enabled = !loading && item != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.pending_sync_overwrite_server))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { confirmPull = true },
            enabled = !loading && item != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.pending_sync_overwrite_local))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                val payload =
                    when (kind) {
                        SyncBackupKind.NOTE -> localNote?.let { SyncPayloadCodec.encodeNote(it) }
                        SyncBackupKind.CHECKLIST -> localChecklist?.let { SyncPayloadCodec.encodeChecklist(it) }
                    } ?: return@OutlinedButton
                val title = item?.title ?: "backup"
                val send =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, shareLabel)
                        putExtra(Intent.EXTRA_TEXT, "# $title\n\n$payload")
                    }
                context.startActivity(Intent.createChooser(send, shareLabel))
            },
            enabled = item != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.pending_sync_share_local))
        }

        if (backups.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.pending_sync_backups_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            backups.forEach { backup ->
                val directionLabel =
                    when (backup.direction) {
                        SyncBackupDirection.BEFORE_PUSH_LOCAL ->
                            stringResource(R.string.pending_sync_backup_before_push)
                        SyncBackupDirection.BEFORE_PULL_SERVER ->
                            stringResource(R.string.pending_sync_backup_before_pull)
                    }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(directionLabel, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(backup.createdAtEpochMs)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { restoreBackupId = backup.id }, enabled = !loading) {
                        Text(stringResource(R.string.pending_sync_restore_backup))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.back))
        }
    }

    if (confirmPush) {
        AlertDialog(
            onDismissRequest = { confirmPush = false },
            title = { Text(stringResource(R.string.pending_sync_overwrite_server)) },
            text = { Text(stringResource(R.string.pending_sync_overwrite_server_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmPush = false
                        successMessage = null
                        vm.forcePush(kind, itemId) { success ->
                            if (success) successMessage = doneMsg
                        }
                    },
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmPush = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    if (confirmPull) {
        AlertDialog(
            onDismissRequest = { confirmPull = false },
            title = { Text(stringResource(R.string.pending_sync_overwrite_local)) },
            text = { Text(stringResource(R.string.pending_sync_overwrite_local_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmPull = false
                        successMessage = null
                        vm.pullFromServer(kind, itemId) { success ->
                            if (success) onBack()
                        }
                    },
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmPull = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    restoreBackupId?.let { backupId ->
        AlertDialog(
            onDismissRequest = { restoreBackupId = null },
            title = { Text(stringResource(R.string.pending_sync_restore_backup)) },
            text = { Text(stringResource(R.string.pending_sync_restore_backup_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = backupId
                        restoreBackupId = null
                        successMessage = null
                        vm.restoreBackup(kind, itemId, id) { success ->
                            if (success) successMessage = doneMsg
                        }
                    },
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { restoreBackupId = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun notePayloadDisplayText(payload: com.jotty.android.data.local.NoteSyncPayload): String {
    val parsed = NoteEncryption.parse(payload.content)
    if (parsed is ParsedNoteContent.Encrypted) {
        val unlocked = NoteDecryptionSession.get(payload.id)
        return if (unlocked != null) {
            SyncPayloadCodec.noteDiffText(payload.copy(content = unlocked))
        } else {
            SyncPayloadCodec.noteDiffText(
                payload.copy(content = stringResource(R.string.pending_sync_encrypted_body)),
            )
        }
    }
    return SyncPayloadCodec.noteDiffText(payload)
}

private fun parsePendingOps(json: String): List<PendingItemOp> {
    val type = object : TypeToken<List<PendingItemOp>>() {}.type
    return runCatching { Gson().fromJson<List<PendingItemOp>>(json, type) }.getOrDefault(emptyList())
}

private fun formatPendingOp(op: PendingItemOp): String {
    val path = op.path?.let { " @$it" } ?: ""
    val text = op.text?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
    return "${op.type}$path$text"
}
