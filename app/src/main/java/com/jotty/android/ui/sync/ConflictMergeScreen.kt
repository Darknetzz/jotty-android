package com.jotty.android.ui.sync

import android.app.Application
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jotty.android.R
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.local.SyncBackupKind
import com.jotty.android.ui.common.InlineAlert
import com.jotty.android.ui.common.InlineAlertVariant
import com.jotty.android.ui.common.mainScreenTabContentPadding
import com.jotty.android.util.formatNoteDate

/**
 * Side-by-side review of one sync conflict copy against the item it was split from: keep the
 * server version, keep mine, keep both, or (unencrypted notes) save a merged edit.
 */
@Composable
fun ConflictMergeScreen(
    api: JottyApi,
    instanceId: String,
    kind: SyncBackupKind,
    copyId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val application = LocalContext.current.applicationContext as Application
    val vm: PendingSyncViewModel =
        viewModel(
            key = "pending-sync|$instanceId",
            factory = PendingSyncViewModel.Factory(application, instanceId, api),
        )
    LaunchedEffect(kind, copyId) { vm.loadConflict(kind, copyId) }

    val detail by vm.conflict.collectAsStateWithLifecycle()
    val loading by vm.detailLoading.collectAsStateWithLifecycle()
    val error by vm.actionError.collectAsStateWithLifecycle()
    val conflict = detail?.takeIf { it.copyId == copyId }

    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var mergedTitle by rememberSaveable { mutableStateOf("") }
    var mergedContent by rememberSaveable { mutableStateOf("") }
    var mergedCategory by rememberSaveable { mutableStateOf("") }

    fun startFrom(version: NoteVersion?) {
        version ?: return
        mergedTitle = version.title
        mergedContent = version.content
        mergedCategory = version.category
        editing = true
    }

    fun resolve(resolution: ConflictResolution) {
        vm.resolveConflict(kind, copyId, resolution) { success -> if (success) onBack() }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .mainScreenTabContentPadding(topComfortDp = 16)
                .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = conflict?.title ?: stringResource(R.string.conflict_merge_title),
            style = MaterialTheme.typography.titleLarge,
        )
        conflict?.let {
            Text(
                text =
                    buildString {
                        append(
                            when (it.kind) {
                                SyncBackupKind.NOTE -> stringResource(R.string.nav_notes)
                                SyncBackupKind.CHECKLIST -> stringResource(R.string.nav_checklists)
                            },
                        )
                        append(" · ")
                        append(stringResource(R.string.conflict_copy_saved_at, formatNoteDate(it.copyUpdatedAt)))
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        error?.let { msg ->
            InlineAlert(message = msg, variant = InlineAlertVariant.Danger, modifier = Modifier.padding(top = 12.dp))
        }

        if (loading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        if (conflict == null) {
            if (!loading) {
                Text(
                    text = stringResource(R.string.conflict_gone),
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        } else {
            if (!conflict.hasOriginal) {
                InlineAlert(
                    message = stringResource(R.string.conflict_original_missing),
                    variant = InlineAlertVariant.Info,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = stringResource(R.string.conflict_diff_heading), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.conflict_diff_legend),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            TextDiffView(
                oldText = conflict.serverText.orEmpty(),
                newText = conflict.mineText,
                modifier = Modifier.height(280.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = stringResource(R.string.pending_sync_actions_heading), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            if (conflict.hasOriginal) {
                Button(
                    onClick = { confirm = ACTION_KEEP_MINE },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.conflict_keep_mine)) }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { confirm = ACTION_KEEP_SERVER },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.conflict_keep_server)) }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { confirm = ACTION_KEEP_BOTH },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.conflict_keep_both)) }
            } else {
                Button(
                    onClick = { confirm = ACTION_KEEP_BOTH },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.conflict_keep_both)) }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { confirm = ACTION_KEEP_SERVER },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.conflict_discard_copy)) }
            }

            if (conflict.kind == SyncBackupKind.NOTE && conflict.hasOriginal) {
                Spacer(modifier = Modifier.height(20.dp))
                if (conflict.encrypted) {
                    Text(
                        text = stringResource(R.string.conflict_encrypted_no_merge),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(text = stringResource(R.string.conflict_edit_merged), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { startFrom(conflict.mineNote) }, enabled = !loading) {
                            Text(stringResource(R.string.conflict_start_from_mine))
                        }
                        TextButton(onClick = { startFrom(conflict.serverNote) }, enabled = !loading) {
                            Text(stringResource(R.string.conflict_start_from_server))
                        }
                    }
                    if (editing) {
                        OutlinedTextField(
                            value = mergedTitle,
                            onValueChange = { mergedTitle = it },
                            label = { Text(stringResource(R.string.title)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = mergedContent,
                            onValueChange = { mergedContent = it },
                            label = { Text(stringResource(R.string.conflict_merged_content)) },
                            minLines = 8,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { confirm = ACTION_SAVE_MERGED },
                            enabled = !loading && mergedTitle.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.conflict_save_merged)) }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
    }

    confirm?.let { action ->
        val (title, message) =
            when (action) {
                ACTION_KEEP_MINE ->
                    stringResource(R.string.conflict_keep_mine) to
                        stringResource(
                            if (kind == SyncBackupKind.CHECKLIST) {
                                R.string.conflict_keep_mine_checklist_confirm
                            } else {
                                R.string.conflict_keep_mine_note_confirm
                            },
                        )
                ACTION_KEEP_SERVER ->
                    stringResource(R.string.conflict_keep_server) to stringResource(R.string.conflict_keep_server_confirm)
                ACTION_KEEP_BOTH ->
                    stringResource(R.string.conflict_keep_both) to stringResource(R.string.conflict_keep_both_confirm)
                else ->
                    stringResource(R.string.conflict_save_merged) to stringResource(R.string.conflict_save_merged_confirm)
            }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = null
                        resolve(
                            when (action) {
                                ACTION_KEEP_MINE -> ConflictResolution.KeepMine
                                ACTION_KEEP_SERVER -> ConflictResolution.KeepServer
                                ACTION_KEEP_BOTH -> ConflictResolution.KeepBoth
                                else ->
                                    ConflictResolution.Merged(
                                        NoteVersion(mergedTitle.trim(), mergedContent, mergedCategory),
                                    )
                            },
                        )
                    },
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

private const val ACTION_KEEP_MINE = "keep_mine"
private const val ACTION_KEEP_SERVER = "keep_server"
private const val ACTION_KEEP_BOTH = "keep_both"
private const val ACTION_SAVE_MERGED = "save_merged"
