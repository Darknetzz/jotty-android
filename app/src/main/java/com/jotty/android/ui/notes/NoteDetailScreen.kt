package com.jotty.android.ui.notes

import android.content.Intent
import android.util.Log
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
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import coil.ImageLoader
import com.jotty.android.R
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import com.jotty.android.data.api.UpdateNoteRequest
import com.jotty.android.data.encryption.BiometricPassphraseStore
import com.jotty.android.data.encryption.NoteDecryptionSession
import com.jotty.android.data.encryption.NoteEncryption
import com.jotty.android.data.encryption.ParsedNoteContent
import com.jotty.android.data.encryption.XChaCha20Encryptor
import com.jotty.android.util.stripInvisibleFromEdges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteDetailScreen(
    note: Note,
    api: JottyApi,
    onBack: () -> Unit,
    onUpdate: (Note) -> Unit,
    onDelete: () -> Unit,
    onSaveFailed: () -> Unit = {},
    debugLoggingEnabled: Boolean = false,
    imageLoader: ImageLoader? = null,
    biometricStore: BiometricPassphraseStore? = null,
) {
    var title by remember { mutableStateOf(note.title) }
    var content by remember { mutableStateOf(note.content) }
    var isEditing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var decryptedContent by remember { mutableStateOf<String?>(null) }
    var showDecryptDialog by remember { mutableStateOf(false) }
    var showEncryptDialog by remember { mutableStateOf(false) }
    var isEncrypting by remember { mutableStateOf(false) }
    var encryptError by remember { mutableStateOf<String?>(null) }
    var decryptError by remember { mutableStateOf<String?>(null) }
    var decryptErrorDetail by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val parsed = remember(content) { NoteEncryption.parse(content) }
    val isEncryptedByContent = parsed is ParsedNoteContent.Encrypted
    val isEncrypted = note.encrypted == true || isEncryptedByContent
    val displayContent = when {
        isEncrypted && decryptedContent != null -> decryptedContent
        isEncrypted -> null
        else -> content
    }

    var hasBiometricPassphrase by remember(note.id) {
        mutableStateOf(biometricStore?.hasPassphrase(note.id) == true)
    }
    // Survives rotation (rememberSaveable) so we don't re-trigger after config change.
    // Resets when note.id changes because note.id is a key.
    var biometricAutoTriggered by rememberSaveable(note.id) { mutableStateOf(false) }

    val activity = LocalContext.current as? FragmentActivity
    val biometricTitle = stringResource(R.string.biometric_prompt_title)
    val biometricSubtitle = stringResource(R.string.biometric_prompt_subtitle)
    val biometricCancelStr = stringResource(R.string.cancel)

    val biometricUnlockPrompt = remember(activity, biometricStore, note.id) {
        if (activity == null || biometricStore == null) null
        else {
            val executor = ContextCompat.getMainExecutor(activity)
            val noteId = note.id
            BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val cipher = result.cryptoObject?.cipher ?: return
                    val plaintext = biometricStore.loadPassphrase(noteId, cipher) ?: return
                    val cleaned = stripInvisibleFromEdges(plaintext)
                    decryptedContent = cleaned
                    NoteDecryptionSession.put(noteId, cleaned)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
                override fun onAuthenticationFailed() {}
            })
        }
    }

    // Cancel any pending biometric auth when the note changes or the screen is disposed.
    DisposableEffect(biometricUnlockPrompt) {
        onDispose { biometricUnlockPrompt?.cancelAuthentication() }
    }

    fun launchBiometricUnlock() {
        if (biometricUnlockPrompt == null || biometricStore == null) return
        // getCipherForDecrypt touches the Keystore (binder) — run on IO to avoid ANR.
        scope.launch {
            val cipher = withContext(Dispatchers.IO) { biometricStore.getCipherForDecrypt(note.id) }
            if (cipher == null) { hasBiometricPassphrase = false; return@launch }
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
        title = stripInvisibleFromEdges(note.title)
        content = stripInvisibleFromEdges(note.content)
        decryptedContent = NoteDecryptionSession.get(note.id)?.let { stripInvisibleFromEdges(it) }
        if (decryptedContent == null && hasBiometricPassphrase && !biometricAutoTriggered) {
            biometricAutoTriggered = true
            launchBiometricUnlock()
        }
    }

    LaunchedEffect(note.encrypted, content, parsed) {
        if (note.encrypted == true || parsed is ParsedNoteContent.Encrypted) {
            Log.i(
                "Jotty/encryption",
                "Note detail: note.encrypted=${note.encrypted}, contentLength=${content.length}, " +
                    "parsedAsEncrypted=${parsed is ParsedNoteContent.Encrypted}, " +
                    "contentStart=${content.take(60).replace("\n", " ")}"
            )
        }
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
                            val intent = Intent(Intent.ACTION_SEND).apply {
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
                    IconButton(onClick = { showDecryptDialog = true }) {
                        Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.cd_decrypt))
                    }
                } else if (isEditing) {
                    if (saving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(12.dp))
                    } else {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    saving = true
                                    try {
                                        val updated = api.updateNote(
                                            note.id,
                                            UpdateNoteRequest(
                                                title = title,
                                                content = content,
                                                originalCategory = note.category,
                                            ),
                                        )
                                        if (updated.success) {
                                            onUpdate(updated.data)
                                            isEditing = false
                                        }
                                    } catch (_: Exception) { onSaveFailed() }
                                    saving = false
                                }
                            },
                        ) {
                            Icon(Icons.Default.Save, contentDescription = stringResource(R.string.cd_save))
                        }
                    }
                } else if (!isEncrypted) {
                    IconButton(onClick = { showEncryptDialog = true }) {
                        Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.cd_encrypt))
                    }
                    IconButton(onClick = { isEditing = true }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_edit))
                    }
                }
            },
        )

        when {
            isEncrypted && decryptedContent == null -> EncryptedNotePlaceholder(
                encryptionMethod = (parsed as? ParsedNoteContent.Encrypted)?.encryptionMethod ?: "xchacha",
                canDecryptInApp = isEncryptedByContent,
                onDecryptClick = { showDecryptDialog = true },
                onBiometricClick = if (hasBiometricPassphrase) {{ launchBiometricUnlock() }} else null,
            )
            isEditing -> NoteEditor(
                title = title,
                onTitleChange = { title = it },
                content = content,
                onContentChange = { content = it },
            )
            else -> NoteView(
                title = title,
                content = displayContent ?: "",
                imageLoader = imageLoader,
            )
        }
    }

    if (showEncryptDialog) {
        val encryptFailedMsg = stringResource(R.string.error_encrypt_failed)
        EncryptNoteDialog(
            onDismiss = {
                showEncryptDialog = false
                encryptError = null
            },
            isEncrypting = isEncrypting,
            encryptError = encryptError,
            onEncrypt = { passphrase ->
                encryptError = null
                isEncrypting = true
                scope.launch {
                    val body = withContext(Dispatchers.Default) {
                        XChaCha20Encryptor.encrypt(displayContent ?: content, passphrase)
                    }
                    isEncrypting = false
                    if (body != null) {
                        val fullContent = XChaCha20Encryptor.wrapWithFrontmatter(note.id, title, note.category, body)
                        try {
                            val updated = api.updateNote(
                                note.id,
                                UpdateNoteRequest(
                                    title = title,
                                    content = fullContent,
                                    originalCategory = note.category,
                                ),
                            )
                            if (updated.success) {
                                onUpdate(updated.data)
                                showEncryptDialog = false
                            }
                        } catch (_: Exception) { onSaveFailed() }
                    } else {
                        encryptError = encryptFailedMsg
                    }
                }
            },
        )
    }
    if (showDecryptDialog && parsed is ParsedNoteContent.Encrypted) {
        DecryptNoteDialog(
            encryptionMethod = parsed.encryptionMethod,
            encryptedBody = parsed.encryptedBody,
            noteId = note.id,
            biometricStore = biometricStore,
            onDismiss = {
                showDecryptDialog = false
                decryptError = null
                decryptErrorDetail = null
            },
            onDecrypted = {
                val cleaned = stripInvisibleFromEdges(it)
                decryptedContent = cleaned
                NoteDecryptionSession.put(note.id, cleaned)
                showDecryptDialog = false
                decryptError = null
                decryptErrorDetail = null
            },
            onBiometricSaved = { hasBiometricPassphrase = true },
            decryptError = decryptError,
            decryptErrorDetail = decryptErrorDetail,
            onDecryptError = { main, detail ->
                decryptError = main
                decryptErrorDetail = detail
            },
            debugLoggingEnabled = debugLoggingEnabled,
        )
    }
}
