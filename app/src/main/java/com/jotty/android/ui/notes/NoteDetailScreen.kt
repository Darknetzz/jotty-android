package com.jotty.android.ui.notes

import android.content.ClipData
import android.webkit.WebView
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import com.jotty.android.R
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import com.jotty.android.data.encryption.BiometricPassphraseStore
import com.jotty.android.data.local.NoteSnapshot
import com.jotty.android.data.local.NoteSnapshotRepository
import com.jotty.android.data.local.JottyDatabase
import com.jotty.android.data.encryption.NoteEncryption
import com.jotty.android.data.encryption.ParsedNoteContent
import androidx.compose.material.icons.filled.Share
import com.jotty.android.ui.common.ArchiveDropdownMenuItem
import com.jotty.android.ui.common.ConfirmDeleteDialog
import com.jotty.android.ui.common.DeleteDropdownMenuItem
import com.jotty.android.ui.common.MainNestedScaffoldContentWindowInsets
import com.jotty.android.ui.common.NoteDetailDateSubtitle
import com.jotty.android.ui.common.ShareDropdownMenuItem
import com.jotty.android.ui.common.ShareServerDialog
import com.jotty.android.util.defaultUnarchiveCategory
import com.jotty.android.util.isArchivedCategory
import com.jotty.android.util.JOTTY_ARCHIVE_CATEGORY
import com.jotty.android.util.formatNoteDate
import com.jotty.android.util.noteContentContainsRawHtml
import com.jotty.android.util.noteNeedsRichEditor
import com.jotty.android.util.prepareWysiwygHtmlForMarkdown
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteDetailScreen(
    note: Note,
    actions: NoteDetailActions,
    onBack: () -> Unit,
    onUpdate: (Note) -> Unit,
    onDelete: () -> Unit,
    onSaveFailed: () -> Unit = {},
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader? = null,
    jottyServerUrl: String? = null,
    apiKey: String? = null,
    serverCapabilitiesKey: String? = null,
    biometricStore: BiometricPassphraseStore? = null,
    biometricAutoUnlockEnabled: Boolean = true,
    biometricSaveOfferEnabled: Boolean = true,
    categorySuggestions: List<String> = emptyList(),
    richEditorEnabled: Boolean = false,
    visualEditorSaveAsMarkdown: Boolean = false,
    noteSnapshotsEnabled: Boolean = true,
    openNotesInEditMode: Boolean = false,
    defaultNoteEditMode: String = "markdown",
    markdownEditorMonospace: Boolean = false,
    api: JottyApi? = null,
    isOnline: Boolean = true,
) {
    val context = LocalContext.current
    val snapshotRepository =
        remember(serverCapabilitiesKey) {
            serverCapabilitiesKey?.let { instanceId ->
                NoteSnapshotRepository(
                    JottyDatabase.getDatabase(context.applicationContext),
                    instanceId,
                )
            }
        }
    val detailVm: NoteDetailViewModel =
        viewModel(
            key = note.id,
            factory = NoteDetailViewModel.Factory(note, actions, snapshotRepository),
        )

    val title by detailVm.title.collectAsStateWithLifecycle()
    val content by detailVm.content.collectAsStateWithLifecycle()
    val category by detailVm.category.collectAsStateWithLifecycle()
    val isEditing by detailVm.isEditing.collectAsStateWithLifecycle()
    val saving by detailVm.saving.collectAsStateWithLifecycle()
    val decryptedContent by detailVm.decryptedContent.collectAsStateWithLifecycle()
    val showDecryptDialog by detailVm.showDecryptDialog.collectAsStateWithLifecycle()
    val showEncryptDialog by detailVm.showEncryptDialog.collectAsStateWithLifecycle()
    val isEncrypting by detailVm.isEncrypting.collectAsStateWithLifecycle()
    val encryptError by detailVm.encryptError.collectAsStateWithLifecycle()
    val decryptError by detailVm.decryptError.collectAsStateWithLifecycle()
    val decryptErrorDetail by detailVm.decryptErrorDetail.collectAsStateWithLifecycle()
    val legacyEncryptionDetected by detailVm.legacyEncryptionDetected.collectAsStateWithLifecycle()

    val parsed = detailVm.parsed
    val isEncryptedByContent = detailVm.isEncryptedByContent
    val isEncrypted = detailVm.isEncrypted
    val displayContent = detailVm.displayContent
    val isDecrypted = decryptedContent != null
    val encryptedBodyForBiometric =
        remember(content) {
            (NoteEncryption.parse(content) as? ParsedNoteContent.Encrypted)?.encryptedBody
        }

    var noteEditMode by rememberSaveable(note.id) {
        mutableStateOf(resolveInitialNoteEditMode(richEditorEnabled, defaultNoteEditMode))
    }
    var autoEditTriggered by rememberSaveable(note.id) { mutableStateOf(false) }
    var editorReloadNonce by rememberSaveable(note.id) { mutableIntStateOf(0) }
    var wysiwygWebView by remember(note.id) { mutableStateOf<WebView?>(null) }
    var wysiwygBridge by remember(note.id) { mutableStateOf<WysiwygEditorBridge?>(null) }
    var encryptedVisualAcknowledged by rememberSaveable(note.id) { mutableStateOf(false) }
    var showEncryptedVisualConfirm by remember { mutableStateOf(false) }
    var showUnsavedChangesConfirm by remember { mutableStateOf(false) }

    fun currentEditBody(): String = if (isEncrypted) decryptedContent.orEmpty() else content

    fun setEditBody(value: String) {
        if (isEncrypted) {
            detailVm.setDecryptedContent(value)
        } else {
            detailVm.setContent(value)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var hasBiometricPassphrase by remember(note.id) {
        mutableStateOf(biometricStore?.hasPassphrase(note.id) == true)
    }
    var biometricAutoTriggered by rememberSaveable(note.id) { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showShareServerDialog by remember { mutableStateOf(false) }
    var showArchiveConfirm by remember { mutableStateOf(false) }
    var showRestoreSnapshots by remember { mutableStateOf(false) }
    var pendingRestoreSnapshot by remember { mutableStateOf<NoteSnapshot?>(null) }
    var noteSnapshots by remember { mutableStateOf<List<NoteSnapshot>>(emptyList()) }

    val displayTitle = title.ifBlank { stringResource(R.string.untitled) }
    val deleteConfirmMessage = stringResource(R.string.delete_note_confirm, displayTitle)
    val exportNoteTitle = stringResource(R.string.export_note)
    val restoreSnapshotSuccessPlain = stringResource(R.string.restore_note_snapshot_success)
    val restoreSnapshotSuccessEncrypted = stringResource(R.string.restore_note_snapshot_success_encrypted)
    val saveFailedRestoreMsg = stringResource(R.string.save_failed_restore_backup)
    val restoreBackupAction = stringResource(R.string.restore_backup_action)
    val saveUnsafeMsg = stringResource(R.string.error_unsafe_html_save)
    val encryptFailedMsg = stringResource(R.string.error_encrypt_failed)
    val encryptNoPlaintextMsg = stringResource(R.string.error_encrypt_no_plaintext)
    val encryptServerVerifyFailedMsg = stringResource(R.string.error_encrypt_server_verify_failed)
    val encryptStaleSessionMsg = stringResource(R.string.error_encrypt_stale_session)
    val encryptArgonFallbackMsg = stringResource(R.string.encrypt_argon_fallback_notice)
    val restoreVerifyFailedMsg = stringResource(R.string.error_restore_verify_failed)
    val ctx = context

    val activity = LocalActivity.current as? FragmentActivity
    val biometricTitle = stringResource(R.string.biometric_prompt_title)
    val biometricSubtitle = stringResource(R.string.biometric_prompt_subtitle)
    val biometricCancelStr = stringResource(R.string.cancel)
    val biometricErrorMsg = stringResource(R.string.biometric_error)
    val decryptFailedMsg = stringResource(R.string.error_decrypt_failed)
    val biometricSavedMsg = stringResource(R.string.biometric_passphrase_saved)

    val biometricUnlock =
        rememberBiometricNoteUnlock(
            activity = activity,
            biometricStore = biometricStore,
            noteId = note.id,
            title = biometricTitle,
            subtitle = biometricSubtitle,
            negativeButtonText = biometricCancelStr,
            encryptedBody = { encryptedBodyForBiometric },
            onDecrypted = { plain, usedLegacy, pass ->
                detailVm.onDecrypted(plain, usedLegacy, passphrase = pass)
            },
            onDecryptFailed = {
                scope.launch { snackbarHostState.showSnackbar(decryptFailedMsg) }
            },
            onAuthError = { _, _ ->
                scope.launch { snackbarHostState.showSnackbar(biometricErrorMsg) }
            },
            onAuthCancelled = { biometricAutoTriggered = false },
            onNoStoredPassphrase = { hasBiometricPassphrase = false },
        )

    fun launchBiometricUnlock() {
        biometricUnlock.launchUnlock()
    }

    val noteUpdatedLabel =
        remember(note.updatedAt) {
            val formatted = formatNoteDate(note.updatedAt)
            if (formatted.isNotBlank()) formatted else null
        }

    LaunchedEffect(note.id, note.content, note.updatedAt, note.encrypted) {
        detailVm.onNoteSnapshotUpdated(note)
    }

    LaunchedEffect(note.id, biometricStore) {
        hasBiometricPassphrase =
            withContext(Dispatchers.IO) {
                biometricStore?.ensurePassphraseValid(note.id) == true
            }
        if (
            biometricAutoUnlockEnabled &&
            detailVm.decryptedContent.value == null &&
            hasBiometricPassphrase &&
            !encryptedBodyForBiometric.isNullOrBlank() &&
            !biometricAutoTriggered
        ) {
            biometricAutoTriggered = true
            delay(150)
            launchBiometricUnlock()
        }
    }

    LaunchedEffect(note.encrypted, content) {
        detailVm.logEncryptionState()
    }

    LaunchedEffect(note.id, note.content, snapshotRepository) {
        noteSnapshots =
            if (snapshotRepository != null) {
                withContext(Dispatchers.IO) {
                    snapshotRepository.listForNote(note.id)
                }
            } else {
                emptyList()
            }
    }

    fun refreshNoteSnapshots() {
        val repo = snapshotRepository ?: return
        scope.launch {
            noteSnapshots =
                withContext(Dispatchers.IO) {
                    repo.listForNote(note.id)
                }
        }
    }

    fun bodyAfterVisualSave(html: String): String =
        if (visualEditorSaveAsMarkdown) {
            prepareWysiwygHtmlForMarkdown(html)
        } else {
            html
        }

    fun isVisualBodySafeToSave(body: String): Boolean = !detailVm.isUnsafeVisualPlaintextForSave(body)

    fun handleSaveFailed() {
        scope.launch {
            val snapshots =
                if (snapshotRepository != null) {
                    withContext(Dispatchers.IO) {
                        snapshotRepository.listForNote(note.id)
                    }
                } else {
                    emptyList()
                }
            noteSnapshots = snapshots
            if (snapshots.isNotEmpty()) {
                val result =
                    snackbarHostState.showSnackbar(
                        message = saveFailedRestoreMsg,
                        actionLabel = restoreBackupAction,
                        duration = SnackbarDuration.Long,
                    )
                if (result == SnackbarResult.ActionPerformed) {
                    showRestoreSnapshots = true
                }
            } else {
                onSaveFailed()
            }
        }
    }

    fun onEncryptSuccess(updated: Note) {
        onUpdate(updated)
        if (detailVm.consumeArgonFallbackNotice()) {
            scope.launch {
                snackbarHostState.showSnackbar(encryptArgonFallbackMsg)
            }
        }
    }

    fun runEncryptedSaveWithSessionPassphrase() {
        detailVm.encryptWithSessionPassphrase(
            encryptFailedMsg,
            onSuccess = ::onEncryptSuccess,
            onFailure = { handleSaveFailed() },
            encryptNoPlaintextMsg = encryptNoPlaintextMsg,
            encryptServerVerifyFailedMsg = encryptServerVerifyFailedMsg,
            encryptStaleSessionMsg = encryptStaleSessionMsg,
            snapshotsEnabled = noteSnapshotsEnabled,
        )
    }

    fun reencryptForWebCompatibility() {
        if (detailVm.hasSessionPassphrase()) {
            runEncryptedSaveWithSessionPassphrase()
        } else {
            detailVm.showEncryptDialog()
        }
    }

    fun persistPlainEdit(onComplete: () -> Unit = {}) {
        detailVm.saveEdit(
            onSuccess = {
                onUpdate(it)
                onComplete()
            },
            onFailure = { handleSaveFailed() },
            snapshotsEnabled = noteSnapshotsEnabled,
        )
    }

    fun initiatePlainSave(onComplete: () -> Unit = {}) {
        if (noteEditMode == NoteEditMode.Visual) {
            flushWysiwygContentForSave(wysiwygWebView, wysiwygBridge, currentEditBody()) { html ->
                val body = bodyAfterVisualSave(html)
                if (!isVisualBodySafeToSave(body)) {
                    scope.launch { snackbarHostState.showSnackbar(saveUnsafeMsg) }
                    return@flushWysiwygContentForSave
                }
                setEditBody(body)
                persistPlainEdit(onComplete)
            }
        } else {
            persistPlainEdit(onComplete)
        }
    }

    fun initiateEncryptedSave(flushVisualEditor: Boolean = true) {
        if (flushVisualEditor && noteEditMode == NoteEditMode.Visual) {
            flushWysiwygContentForSave(wysiwygWebView, wysiwygBridge, currentEditBody()) { html ->
                val body = bodyAfterVisualSave(html)
                if (!isVisualBodySafeToSave(body)) {
                    scope.launch { snackbarHostState.showSnackbar(saveUnsafeMsg) }
                    return@flushWysiwygContentForSave
                }
                setEditBody(body)
                detailVm.showEncryptDialog()
            }
        } else {
            detailVm.showEncryptDialog()
        }
    }

    fun requestNavigateBack() {
        if (isEditing && detailVm.hasUnsavedChanges()) {
            showUnsavedChangesConfirm = true
        } else {
            if (isEditing) {
                detailVm.cancelEditing()
            }
            onBack()
        }
    }

    fun applyNoteEditModeChange(mode: NoteEditMode) {
        if (mode == NoteEditMode.Markdown && noteEditMode == NoteEditMode.Visual) {
            setEditBody(prepareWysiwygHtmlForMarkdown(currentEditBody()))
        }
        noteEditMode = mode
        editorReloadNonce++
    }

    fun onNoteEditModeChange(mode: NoteEditMode) {
        if (mode == noteEditMode) return
        if (mode == NoteEditMode.Visual && isEncrypted && isDecrypted && !encryptedVisualAcknowledged) {
            showEncryptedVisualConfirm = true
            return
        }
        applyNoteEditModeChange(mode)
    }

    BackHandler(enabled = isEditing) {
        requestNavigateBack()
    }

    fun formatSnapshotLabel(epochMs: Long): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        return Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(formatter)
    }

    LaunchedEffect(isEditing, isEncrypted, encryptedVisualAcknowledged, noteEditMode) {
        if (isEditing && isEncrypted && noteEditMode == NoteEditMode.Visual && !encryptedVisualAcknowledged) {
            noteEditMode = NoteEditMode.Markdown
            editorReloadNonce++
        }
    }

    LaunchedEffect(note.id, openNotesInEditMode, isEncrypted, isDecrypted, autoEditTriggered) {
        if (autoEditTriggered || !openNotesInEditMode || isEditing) return@LaunchedEffect
        if (!isEncrypted || isDecrypted) {
            detailVm.startEditing()
            autoEditTriggered = true
        }
    }

    if (showEncryptedVisualConfirm) {
        AlertDialog(
            onDismissRequest = { showEncryptedVisualConfirm = false },
            title = { Text(stringResource(R.string.encrypted_note_visual_editor_confirm_title)) },
            text = { Text(stringResource(R.string.encrypted_note_visual_editor_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEncryptedVisualConfirm = false
                        encryptedVisualAcknowledged = true
                        applyNoteEditModeChange(NoteEditMode.Visual)
                    },
                ) {
                    Text(stringResource(R.string.encrypted_note_visual_editor_confirm_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEncryptedVisualConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            message = deleteConfirmMessage,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
        )
    }

    if (showArchiveConfirm) {
        val archived = isArchivedCategory(category)
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            title = {
                Text(stringResource(if (archived) R.string.unarchive else R.string.archive))
            },
            text = { Text(stringResource(R.string.archive_note_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showArchiveConfirm = false
                        scope.launch {
                            val newCategory =
                                if (archived) {
                                    defaultUnarchiveCategory()
                                } else {
                                    JOTTY_ARCHIVE_CATEGORY
                                }
                            actions
                                .updateNote(
                                    noteId = note.id,
                                    title = title,
                                    content = detailVm.contentForServerUpdate(),
                                    category = newCategory,
                                    originalCategory = category,
                                ).onSuccess {
                                    onUpdate(it)
                                    if (!archived) onBack()
                                }.onFailure {
                                    onSaveFailed()
                                }
                        }
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showShareServerDialog && api != null) {
        ShareServerDialog(
            itemType = "note",
            itemId = note.id,
            itemTitle = displayTitle,
            api = api,
            capabilitiesKey = serverCapabilitiesKey,
            onDismiss = { showShareServerDialog = false },
            onExportText = {
                val text =
                    when {
                        isDecrypted -> decryptedContent.orEmpty()
                        isEncrypted -> ""
                        else -> content
                    }.trim()
                val shareText = if (text.isNotBlank()) "# $title\n\n$text" else title
                val intent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TITLE, title)
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                ctx.startActivity(Intent.createChooser(intent, exportNoteTitle))
                showShareServerDialog = false
            },
        )
    }

    val placeholderHint =
        when {
            !isOnline && isEncrypted && !isDecrypted && !hasBiometricPassphrase ->
                stringResource(R.string.encrypted_note_offline_hint)
            hasBiometricPassphrase -> stringResource(R.string.enter_passphrase_or_biometric)
            else -> stringResource(R.string.enter_passphrase_to_view)
        }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = MainNestedScaffoldContentWindowInsets,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                title = {
                    Text(
                        text = title.ifBlank { stringResource(R.string.untitled) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { requestNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    val ctx = LocalContext.current
                    val canEdit = !isEncrypted || isDecrypted
                    if (!isEditing && (isDecrypted || (!isEncrypted && content.isNotBlank()))) {
                        IconButton(
                            onClick = {
                                val text =
                                    when {
                                        isDecrypted -> decryptedContent.orEmpty()
                                        isEncrypted -> ""
                                        else -> content
                                    }.trim()
                                val shareText = if (text.isNotBlank()) "# $title\n\n$text" else title
                                val intent =
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TITLE, title)
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }
                                ctx.startActivity(Intent.createChooser(intent, exportNoteTitle))
                            },
                        ) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.cd_share))
                        }
                    }
                    if (isEncrypted && !isDecrypted && isEncryptedByContent) {
                        if (hasBiometricPassphrase) {
                            IconButton(onClick = { launchBiometricUnlock() }) {
                                Icon(Icons.Default.Fingerprint, contentDescription = stringResource(R.string.biometric_unlock))
                            }
                        }
                        IconButton(onClick = { detailVm.showDecryptDialog() }) {
                            Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.cd_decrypt))
                        }
                    } else if (isEditing) {
                        if (saving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(12.dp))
                        } else {
                            IconButton(
                                onClick = {
                                    if (isEncrypted) {
                                        initiateEncryptedSave()
                                    } else {
                                        initiatePlainSave()
                                    }
                                },
                            ) {
                                Icon(Icons.Default.Save, contentDescription = stringResource(R.string.cd_save))
                            }
                        }
                    } else if (isEncrypted && isDecrypted) {
                        if (!saving && !isEncrypting) {
                            IconButton(onClick = { initiateEncryptedSave(flushVisualEditor = false) }) {
                                Icon(Icons.Default.Save, contentDescription = stringResource(R.string.cd_save))
                            }
                        }
                        IconButton(
                            onClick = {
                                detailVm.lockNote()
                                encryptedVisualAcknowledged = false
                                hasBiometricPassphrase = biometricStore?.hasPassphrase(note.id) == true
                            },
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.cd_lock_note))
                        }
                    } else if (!isEncrypted) {
                        IconButton(onClick = { detailVm.showEncryptDialog() }) {
                            Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.cd_encrypt))
                        }
                    }
                    if (!isEditing && canEdit) {
                        IconButton(
                            onClick = {
                                if (isEncrypted) {
                                    encryptedVisualAcknowledged = false
                                    noteEditMode = NoteEditMode.Markdown
                                } else {
                                    val body = currentEditBody()
                                    noteEditMode =
                                        if (richEditorEnabled || noteNeedsRichEditor(body)) {
                                            NoteEditMode.Visual
                                        } else {
                                            NoteEditMode.Markdown
                                        }
                                }
                                editorReloadNonce++
                                detailVm.startEditing()
                            },
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_edit))
                        }
                    }
                    if (!isEditing) {
                        val noteCopiedMessage = stringResource(R.string.note_copied)
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            val copyBody =
                                when {
                                    isDecrypted -> decryptedContent.orEmpty()
                                    isEncrypted -> ""
                                    else -> content
                                }.trim()
                            if (copyBody.isNotBlank() || title.isNotBlank()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.copy_note)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = stringResource(R.string.copy_note),
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        val copyText =
                                            if (copyBody.isNotBlank()) {
                                                "# $title\n\n$copyBody"
                                            } else {
                                                title
                                            }
                                        val clipboard =
                                            ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(
                                            ClipData.newPlainText(title.ifBlank { "note" }, copyText),
                                        )
                                        scope.launch {
                                            snackbarHostState.showSnackbar(noteCopiedMessage)
                                        }
                                    },
                                )
                            }
                            if (noteSnapshots.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.restore_note_snapshot)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.History,
                                            contentDescription = stringResource(R.string.restore_note_snapshot),
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        refreshNoteSnapshots()
                                        showRestoreSnapshots = true
                                    },
                                )
                            }
                            if (isEncrypted && isDecrypted) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.lock_note)) },
                                    onClick = {
                                        menuExpanded = false
                                        detailVm.lockNote()
                                        encryptedVisualAcknowledged = false
                                        hasBiometricPassphrase = biometricStore?.hasPassphrase(note.id) == true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = stringResource(R.string.lock_note),
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.reencrypt)) },
                                    onClick = {
                                        menuExpanded = false
                                        initiateEncryptedSave(flushVisualEditor = false)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = stringResource(R.string.reencrypt),
                                        )
                                    },
                                )
                            }
                            if (api != null) {
                                ShareDropdownMenuItem(
                                    labelRes = R.string.share_note,
                                    onClick = {
                                        menuExpanded = false
                                        showShareServerDialog = true
                                    },
                                )
                            }
                            if (!isEncrypted || isDecrypted) {
                                ArchiveDropdownMenuItem(
                                    isArchived = isArchivedCategory(category),
                                    onClick = {
                                        menuExpanded = false
                                        showArchiveConfirm = true
                                    },
                                )
                            }
                            DeleteDropdownMenuItem(
                                onClick = {
                                    menuExpanded = false
                                    showDeleteConfirm = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            when {
                isEncrypted && !isDecrypted ->
                    EncryptedNotePlaceholder(
                        encryptionMethod = (parsed as? ParsedNoteContent.Encrypted)?.encryptionMethod ?: "xchacha",
                        canDecryptInApp = isEncryptedByContent,
                        hintText = placeholderHint,
                        onDecryptClick = { detailVm.showDecryptDialog() },
                        onBiometricClick =
                            if (hasBiometricPassphrase) {
                                { launchBiometricUnlock() }
                            } else {
                                null
                            },
                        modifier = Modifier.fillMaxSize(),
                    )
                isEditing -> {
                    val editBody = currentEditBody()
                    val editorReloadKey = "${note.id}:${note.updatedAt}:$editorReloadNonce"
                    Column(modifier = Modifier.fillMaxSize()) {
                        NoteEditModeToggle(
                            mode = noteEditMode,
                            onModeChange = ::onNoteEditModeChange,
                        )
                        if (isEncrypted && noteEditMode == NoteEditMode.Visual) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.encrypted_note_visual_editor_warning),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        when (noteEditMode) {
                            NoteEditMode.Visual ->
                                WysiwygNoteEditor(
                                    title = title,
                                    onTitleChange = { detailVm.setTitle(it) },
                                    content = editBody,
                                    contentReloadKey = editorReloadKey,
                                    onContentChange = ::setEditBody,
                                    category = category,
                                    onCategoryChange = { detailVm.setCategory(it) },
                                    categorySuggestions = categorySuggestions,
                                    showHtmlSaveHint = noteContentContainsRawHtml(editBody),
                                    onEditorWebView = { wysiwygWebView = it },
                                    onEditorBridge = { wysiwygBridge = it },
                                    jottyServerUrl = jottyServerUrl,
                                    apiKey = apiKey,
                                    serverCapabilitiesKey = serverCapabilitiesKey,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            NoteEditMode.Markdown ->
                                NoteEditor(
                                    title = title,
                                    onTitleChange = { detailVm.setTitle(it) },
                                    content = editBody,
                                    onContentChange = ::setEditBody,
                                    category = category,
                                    onCategoryChange = { detailVm.setCategory(it) },
                                    categorySuggestions = categorySuggestions,
                                    monospaceFont = markdownEditorMonospace,
                                    modifier = Modifier.fillMaxSize(),
                                )
                        }
                    }
                }
                else -> {
                    val viewContent =
                        when {
                            isEncrypted -> decryptedContent.orEmpty()
                            else -> displayContent.orEmpty()
                        }
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (noteUpdatedLabel != null) {
                            NoteDetailDateSubtitle(
                                updatedAtText = stringResource(R.string.note_updated_label, noteUpdatedLabel),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        if (isEncrypted && isDecrypted) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier =
                                    Modifier
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.note_decrypted_session),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                        serverCapabilitiesKey?.let { key ->
                            NoteImageAuthBanner(
                                capabilitiesKey = key,
                                noteContent = viewContent,
                                modifier =
                                    Modifier
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 8.dp),
                            )
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            NoteView(
                                content = viewContent,
                                imageLoader = imageLoader,
                                jottyServerUrl = jottyServerUrl,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (isEncrypted && isDecrypted && legacyEncryptionDetected) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    shape = MaterialTheme.shapes.small,
                                    modifier =
                                        Modifier
                                            .align(Alignment.TopCenter)
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = stringResource(R.string.legacy_encryption_warning),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        TextButton(
                                            onClick = { reencryptForWebCompatibility() },
                                            contentPadding = PaddingValues(0.dp),
                                        ) {
                                            Text(stringResource(R.string.reencrypt_for_web))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRestoreSnapshots) {
        AlertDialog(
            onDismissRequest = { showRestoreSnapshots = false },
            title = { Text(stringResource(R.string.restore_note_snapshot_title)) },
            text = {
                Column {
                    noteSnapshots.forEach { snapshot ->
                        TextButton(
                            onClick = {
                                showRestoreSnapshots = false
                                pendingRestoreSnapshot = snapshot
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(
                                    R.string.restore_note_snapshot_item,
                                    formatSnapshotLabel(snapshot.savedAtEpochMs),
                                ),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRestoreSnapshots = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    pendingRestoreSnapshot?.let { snapshot ->
        val snapshotEncrypted = NoteEncryption.isEncrypted(snapshot.content)
        AlertDialog(
            onDismissRequest = { pendingRestoreSnapshot = null },
            title = { Text(stringResource(R.string.restore_note_snapshot)) },
            text = {
                Text(
                    stringResource(
                        if (snapshotEncrypted) {
                            R.string.restore_note_snapshot_confirm_encrypted
                        } else {
                            R.string.restore_note_snapshot_confirm
                        },
                        formatSnapshotLabel(snapshot.savedAtEpochMs),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestoreSnapshot = null
                        detailVm.restoreSnapshot(
                            snapshotId = snapshot.id,
                            onSuccess = {
                                refreshNoteSnapshots()
                                onUpdate(it)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (snapshotEncrypted) {
                                            restoreSnapshotSuccessEncrypted
                                        } else {
                                            restoreSnapshotSuccessPlain
                                        },
                                    )
                                }
                            },
                            onFailure = {
                                if (detailVm.consumeRestoreVerifyFailed()) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(restoreVerifyFailedMsg)
                                    }
                                } else {
                                    handleSaveFailed()
                                }
                            },
                        )
                    },
                ) {
                    Text(stringResource(R.string.restore))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreSnapshot = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showEncryptDialog) {
        val reEncryptMode = isEncrypted && detailVm.hasSessionPassphrase()
        EncryptNoteDialog(
            onDismiss = { detailVm.dismissEncryptDialog() },
            isEncrypting = isEncrypting,
            encryptError = encryptError,
            reEncryptMode = reEncryptMode,
            onUseStoredPassphrase = { runEncryptedSaveWithSessionPassphrase() },
            onEncrypt = { passChars ->
                detailVm.encrypt(
                    passChars,
                    encryptFailedMsg,
                    onSuccess = ::onEncryptSuccess,
                    onFailure = { handleSaveFailed() },
                    encryptNoPlaintextMsg = encryptNoPlaintextMsg,
                    encryptServerVerifyFailedMsg = encryptServerVerifyFailedMsg,
                    encryptStaleSessionMsg = encryptStaleSessionMsg,
                    snapshotsEnabled = noteSnapshotsEnabled,
                )
            },
        )
    }
    if (showDecryptDialog && parsed is ParsedNoteContent.Encrypted) {
        DecryptNoteDialog(
            encryptionMethod = parsed.encryptionMethod,
            encryptedBody = parsed.encryptedBody,
            noteId = note.id,
            biometricStore = biometricStore,
            biometricSaveOfferEnabled = biometricSaveOfferEnabled,
            onDismiss = { detailVm.dismissDecryptDialog() },
            onDecrypted = { plaintext, usedLegacyDataOrder, passphrase ->
                detailVm.onDecrypted(plaintext, usedLegacyDataOrder, passphrase)
            },
            onBiometricSaved = {
                hasBiometricPassphrase = true
                scope.launch { snackbarHostState.showSnackbar(biometricSavedMsg) }
            },
            decryptError = decryptError,
            decryptErrorDetail = decryptErrorDetail,
            onDecryptError = { main, detail -> detailVm.onDecryptError(main, detail) },
        )
    }

    if (showUnsavedChangesConfirm) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesConfirm = false },
            title = { Text(stringResource(R.string.note_unsaved_changes_title)) },
            text = { Text(stringResource(R.string.note_unsaved_changes_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnsavedChangesConfirm = false
                        if (isEncrypted) {
                            initiateEncryptedSave()
                        } else {
                            initiatePlainSave(onComplete = { onBack() })
                        }
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUnsavedChangesConfirm = false
                        detailVm.discardEditing()
                        onBack()
                    },
                ) {
                    Text(stringResource(R.string.discard))
                }
            },
        )
    }
}
