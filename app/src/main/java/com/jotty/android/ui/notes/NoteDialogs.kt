package com.jotty.android.ui.notes

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.activity.compose.LocalActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.jotty.android.R
import com.jotty.android.data.encryption.BiometricPassphraseStore
import com.jotty.android.data.encryption.XChaCha20Decryptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun EncryptedNotePlaceholder(
    encryptionMethod: String,
    canDecryptInApp: Boolean,
    onDecryptClick: () -> Unit,
    onBiometricClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = stringResource(R.string.note_is_encrypted),
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.note_is_encrypted),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when {
                encryptionMethod == "pgp" -> stringResource(R.string.pgp_not_supported)
                canDecryptInApp -> stringResource(R.string.enter_passphrase_to_view)
                else -> stringResource(R.string.use_web_app_to_decrypt)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (canDecryptInApp && encryptionMethod == "xchacha") {
            if (onBiometricClick != null) {
                Button(onClick = onBiometricClick) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(stringResource(R.string.biometric_unlock))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onDecryptClick) {
                    Text(stringResource(R.string.decrypt_note))
                }
            } else {
                Button(onClick = onDecryptClick) {
                    Text(stringResource(R.string.decrypt_note))
                }
            }
        }
    }
}

@Composable
internal fun EncryptNoteDialog(
    onDismiss: () -> Unit,
    isEncrypting: Boolean = false,
    encryptError: String? = null,
    onEncrypt: (String) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val errorShort = stringResource(R.string.error_passphrase_short)
    val errorMismatch = stringResource(R.string.error_passphrase_mismatch)
    val displayError = error ?: encryptError
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.encrypt_note)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.encrypt_passphrase_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it; error = null },
                    label = { Text(stringResource(R.string.passphrase)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isEncrypting,
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; error = null },
                    label = { Text(stringResource(R.string.confirm_passphrase)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = displayError != null,
                    supportingText = displayError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    enabled = !isEncrypting,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isEncrypting) return@TextButton
                    when {
                        passphrase.length < 12 -> error = errorShort
                        passphrase != confirm -> error = errorMismatch
                        else -> onEncrypt(passphrase)
                    }
                },
                enabled = !isEncrypting,
            ) {
                if (isEncrypting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text(stringResource(R.string.encrypt))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun DecryptNoteDialog(
    encryptionMethod: String,
    encryptedBody: String,
    noteId: String = "",
    biometricStore: BiometricPassphraseStore? = null,
    onDismiss: () -> Unit,
    onDecrypted: (String) -> Unit,
    onBiometricSaved: () -> Unit = {},
    decryptError: String?,
    decryptErrorDetail: String?,
    onDecryptError: (mainMessage: String?, detail: String?) -> Unit,
    debugLoggingEnabled: Boolean = false,
) {
    var passphrase by remember { mutableStateOf("") }
    var isDecrypting by remember { mutableStateOf(false) }
    // Non-null when decryption succeeded and we're offering to save with biometric.
    var offerBiometric by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Guards against double-delivery of decrypted content when multiple dismiss paths race.
    val offerConsumed = remember { AtomicBoolean(false) }
    val scope = rememberCoroutineScope()
    val decryptFailedMsg = stringResource(R.string.error_decrypt_failed)
    val decryptAuthFailedHint = stringResource(R.string.decrypt_auth_failed_hint)

    val activity = LocalActivity.current as? FragmentActivity
    val biometricSaveTitle = stringResource(R.string.biometric_prompt_save_title)
    val biometricSaveSubtitle = stringResource(R.string.biometric_prompt_save_subtitle)
    val biometricCancelStr = stringResource(R.string.cancel)

    val currentOffer = rememberUpdatedState(offerBiometric)
    val currentOnDecrypted = rememberUpdatedState(onDecrypted)
    val currentOnBiometricSaved = rememberUpdatedState(onBiometricSaved)

    val biometricSavePrompt = remember(activity, biometricStore, noteId) {
        if (activity == null || biometricStore == null || noteId.isBlank()) null
        else {
            val executor = ContextCompat.getMainExecutor(activity)
            BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val cipher = result.cryptoObject?.cipher
                    val (pass, plaintext) = currentOffer.value ?: return
                    if (cipher != null) {
                        biometricStore.savePassphrase(noteId, pass, cipher)
                        currentOnBiometricSaved.value()
                    }
                    if (offerConsumed.compareAndSet(false, true)) {
                        currentOnDecrypted.value(plaintext)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // User cancelled or hardware error — passphrase was already verified, so still deliver it.
                    val plaintext = currentOffer.value?.second ?: return
                    if (offerConsumed.compareAndSet(false, true)) {
                        currentOnDecrypted.value(plaintext)
                    }
                }

                override fun onAuthenticationFailed() {}
            })
        }
    }

    if (encryptionMethod != "xchacha") {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.decrypt_note)) },
            text = { Text(stringResource(R.string.pgp_not_supported_short)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        )
        return
    }

    if (offerBiometric != null) {
        fun deliverFromOffer(plaintext: String) {
            if (offerConsumed.compareAndSet(false, true)) onDecrypted(plaintext)
        }
        AlertDialog(
            onDismissRequest = { offerBiometric?.second?.let { deliverFromOffer(it) } },
            title = { Text(stringResource(R.string.biometric_save_passphrase)) },
            text = { Text(stringResource(R.string.biometric_prompt_save_subtitle)) },
            confirmButton = {
                TextButton(onClick = {
                    val plaintext = offerBiometric?.second ?: return@TextButton
                    scope.launch {
                        // Keystore cipher creation may touch binder/disk — keep off main thread.
                        val cipher = withContext(Dispatchers.IO) {
                            biometricStore?.getCipherForEncrypt(noteId)
                        }
                        if (cipher != null && biometricSavePrompt != null) {
                            biometricSavePrompt.authenticate(
                                BiometricPrompt.PromptInfo.Builder()
                                    .setTitle(biometricSaveTitle)
                                    .setSubtitle(biometricSaveSubtitle)
                                    .setNegativeButtonText(biometricCancelStr)
                                    .build(),
                                BiometricPrompt.CryptoObject(cipher),
                            )
                        } else {
                            deliverFromOffer(plaintext)
                        }
                    }
                }) { Text(stringResource(R.string.biometric_save_passphrase)) }
            },
            dismissButton = {
                TextButton(onClick = { offerBiometric?.second?.let { deliverFromOffer(it) } }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.decrypt_note)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.decrypt_passphrase_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = {
                        passphrase = it
                        onDecryptError(null, null)
                    },
                    label = { Text(stringResource(R.string.passphrase)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = decryptError != null,
                    supportingText = if (decryptError != null) {
                        {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(decryptError, color = MaterialTheme.colorScheme.error)
                                if (decryptErrorDetail != null) {
                                    Text(
                                        decryptErrorDetail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    } else null,
                    enabled = !isDecrypting,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isDecrypting) return@TextButton
                    isDecrypting = true
                    onDecryptError(null, null)
                    val pass = passphrase
                    val showDetail = debugLoggingEnabled
                    scope.launch {
                        val result = withContext(Dispatchers.Default) {
                            XChaCha20Decryptor.decryptWithReason(encryptedBody, pass)
                        }
                        withContext(Dispatchers.Main) {
                            isDecrypting = false
                            val plaintext = result.plaintext
                            if (plaintext != null) {
                                val canOffer = biometricStore != null && biometricSavePrompt != null
                                    && noteId.isNotBlank() && biometricStore.isAvailable()
                                    && !biometricStore.hasPassphrase(noteId)
                                if (canOffer) {
                                    offerConsumed.set(false)
                                    offerBiometric = Pair(pass, plaintext)
                                } else {
                                    onDecrypted(plaintext)
                                }
                            } else {
                                val detail = when {
                                    result.failureReason?.contains("Auth failed") == true ->
                                        if (showDetail) result.failureReason + "\n\n" + decryptAuthFailedHint else decryptAuthFailedHint
                                    showDetail -> result.failureReason
                                    else -> null
                                }
                                onDecryptError(decryptFailedMsg, detail)
                            }
                        }
                    }
                },
                enabled = !isDecrypting,
            ) {
                if (isDecrypting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text(stringResource(R.string.decrypt))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
