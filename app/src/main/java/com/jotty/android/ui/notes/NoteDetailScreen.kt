package com.jotty.android.ui.notes

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import com.jotty.android.R
import com.jotty.android.data.api.Note
import com.jotty.android.data.encryption.BiometricPassphraseStore
import com.jotty.android.data.encryption.ParsedNoteContent
import com.jotty.android.data.encryption.clearPassphrase
import com.jotty.android.ui.common.ConfirmDeleteDialog
import com.jotty.android.ui.common.DeleteDropdownMenuItem
import kotlinx.coroutines.Dispatchers
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
    debugLoggingEnabled: Boolean = false,
    imageLoader: ImageLoader? = null,
    biometricStore: BiometricPassphraseStore? = null,
) {
    val detailVm: NoteDetailViewModel =
        viewModel(
            key = note.id,
            factory = NoteDetailViewModel.Factory(note, actions, debugLoggingEnabled),
        )

    val title by detailVm.title.collectAsStateWithLifecycle()
    val content by detailVm.content.collectAsStateWithLifecycle()
    val isEditing by detailVm.isEditing.collectAsStateWithLifecycle()
    val saving by detailVm.saving.collectAsStateWithLifecycle()
    val decryptedContent by detailVm.decryptedContent.collectAsStateWithLifecycle()
    val showDecryptDialog by detailVm.showDecryptDialog.collectAsStateWithLifecycle()
    val showEncryptDialog by detailVm.showEncryptDialog.collectAsStateWithLifecycle()
    val isEncrypting by detailVm.isEncrypting.collectAsStateWithLifecycle()
    val encryptError by detailVm.encryptError.collectAsStateWithLifecycle()
    val decryptError by detailVm.decryptError.collectAsStateWithLifecycle()
    val decryptErrorDetail by detailVm.decryptErrorDetail.collectAsStateWithLifecycle()

    val parsed = detailVm.parsed
    val currentParsed = rememberUpdatedState(parsed)
    val isEncryptedByContent = detailVm.isEncryptedByContent
    val isEncrypted = detailVm.isEncrypted
    val displayContent = detailVm.displayContent

    val scope = rememberCoroutineScope()

    var hasBiometricPassphrase by remember(note.id) {
        mutableStateOf(biometricStore?.hasPassphrase(note.id) == true)
    }
    var biometricAutoTriggered by rememberSaveable(note.id) { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val displayTitle = title.ifBlank { stringResource(R.string.untitled) }
    val deleteConfirmMessage = stringResource(R.string.delete_note_confirm, displayTitle)

    val activity = LocalActivity.current as? FragmentActivity
    val biometricTitle = stringResource(R.string.biometric_prompt_title)
    val biometricSubtitle = stringResource(R.string.biometric_prompt_subtitle)
    val biometricCancelStr = stringResource(R.string.cancel)

    val biometricUnlockPrompt =
        remember(activity, biometricStore, note.id) {
            if (activity == null || biometricStore == null) {
                null
            } else {
                val executor = ContextCompat.getMainExecutor(activity)
                val noteId = note.id
                BiometricPrompt(
                    activity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            val cipher = result.cryptoObject?.cipher ?: return
                            val passChars = biometricStore.loadPassphrase(noteId, cipher) ?: return
                            val encBody = (currentParsed.value as? ParsedNoteContent.Encrypted)?.encryptedBody
                            if (encBody == null) {
                                passChars.clearPassphrase()
                                return
                            }
                            detailVm.decryptWithBiometric(encBody, passChars)
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {}

                        override fun onAuthenticationFailed() {}
                    },
                )
            }
        }

    DisposableEffect(biometricUnlockPrompt) {
        onDispose { biometricUnlockPrompt?.cancelAuthentication() }
    }

    fun launchBiometricUnlock() {
        if (biometricUnlockPrompt == null || biometricStore == null) return
        scope.launch {
            val cipher = withContext(Dispatchers.IO) { biometricStore.getCipherForDecrypt(note.id) }
            if (cipher == null) {
                hasBiometricPassphrase = false
                return@launch
            }
            biometricUnlockPrompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(biometricTitle)
                    .setSubtitle(biometricSubtitle)
                    .setNegativeButtonText(biometricCancelStr)
                    .build(),
                BiometricPrompt.CryptoObject(cipher),
            )
        }
    }

    LaunchedEffect(note.id) {
        detailVm.resetFromNote(note)
        detailVm.loadSessionDecryptedContent()
        if (detailVm.decryptedContent.value == null && hasBiometricPassphrase && !biometricAutoTriggered) {
            biometricAutoTriggered = true
            launchBiometricUnlock()
        }
    }

    LaunchedEffect(note.encrypted, content, parsed) {
        detailVm.logEncryptionStateIfDebug()
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

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = title.ifBlank { stringResource(R.string.untitled) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                val ctx = LocalContext.current
                val exportTitle = stringResource(R.string.export_note)
                if (!isEditing && (displayContent != null || content.isNotBlank())) {
                    IconButton(
                        onClick = {
                            val text = (displayContent ?: content).trim()
                            val shareText = if (text.isNotBlank()) "# $title\n\n$text" else title
                            val intent =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TITLE, title)
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                            ctx.startActivity(Intent.createChooser(intent, exportTitle))
                        },
                    ) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.cd_share))
                    }
                }
                if (isEncrypted && decryptedContent == null && isEncryptedByContent) {
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
                                detailVm.saveEdit(
                                    onSuccess = onUpdate,
                                    onFailure = onSaveFailed,
                                )
                            },
                        ) {
                            Icon(Icons.Default.Save, contentDescription = stringResource(R.string.cd_save))
                        }
                    }
                } else if (!isEncrypted) {
                    IconButton(onClick = { detailVm.showEncryptDialog() }) {
                        Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.cd_encrypt))
                    }
                    IconButton(onClick = { detailVm.startEditing() }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_edit))
                    }
                }
                if (!isEditing) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
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

        when {
            isEncrypted && decryptedContent == null ->
                EncryptedNotePlaceholder(
                    encryptionMethod = (parsed as? ParsedNoteContent.Encrypted)?.encryptionMethod ?: "xchacha",
                    canDecryptInApp = isEncryptedByContent,
                    onDecryptClick = { detailVm.showDecryptDialog() },
                    onBiometricClick =
                        if (hasBiometricPassphrase) {
                            { launchBiometricUnlock() }
                        } else {
                            null
                        },
                )
            isEditing ->
                NoteEditor(
                    title = title,
                    onTitleChange = { detailVm.setTitle(it) },
                    content = content,
                    onContentChange = { detailVm.setContent(it) },
                )
            else ->
                NoteView(
                    title = title,
                    content = displayContent ?: "",
                    imageLoader = imageLoader,
                )
        }
    }

    if (showEncryptDialog) {
        val encryptFailedMsg = stringResource(R.string.error_encrypt_failed)
        EncryptNoteDialog(
            onDismiss = { detailVm.dismissEncryptDialog() },
            isEncrypting = isEncrypting,
            encryptError = encryptError,
            onEncrypt = { passChars ->
                detailVm.encrypt(
                    passChars,
                    encryptFailedMsg,
                    onSuccess = onUpdate,
                    onFailure = onSaveFailed,
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
            onDismiss = { detailVm.dismissDecryptDialog() },
            onDecrypted = { detailVm.onDecrypted(it) },
            onBiometricSaved = { hasBiometricPassphrase = true },
            decryptError = decryptError,
            decryptErrorDetail = decryptErrorDetail,
            onDecryptError = { main, detail -> detailVm.onDecryptError(main, detail) },
            debugLoggingEnabled = debugLoggingEnabled,
        )
    }
}
