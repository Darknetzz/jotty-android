package com.jotty.android.ui.notes

import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.jotty.android.data.encryption.BiometricPassphraseStore
import com.jotty.android.data.encryption.XChaCha20Decryptor
import com.jotty.android.data.encryption.clearPassphrase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles biometric unlock for a note: loads the stored passphrase and decrypts [encryptedBody].
 */
class BiometricNoteUnlockController internal constructor(
    val launchUnlock: () -> Unit,
    internal val cancelAuthentication: () -> Unit,
)

@Composable
fun rememberBiometricNoteUnlock(
    activity: FragmentActivity?,
    biometricStore: BiometricPassphraseStore?,
    noteId: String,
    title: String,
    subtitle: String,
    negativeButtonText: String,
    encryptedBody: () -> String?,
    onDecrypted: (plaintext: String, usedLegacyDataOrder: Boolean, passphrase: CharArray?) -> Unit,
    onDecryptFailed: () -> Unit,
    onAuthError: (errorCode: Int, errString: CharSequence) -> Unit,
    onAuthCancelled: () -> Unit,
    onNoStoredPassphrase: () -> Unit,
): BiometricNoteUnlockController {
    val scope = rememberCoroutineScope()
    val currentEncryptedBody = rememberUpdatedState(encryptedBody)
    val currentOnDecrypted = rememberUpdatedState(onDecrypted)
    val currentOnDecryptFailed = rememberUpdatedState(onDecryptFailed)
    val currentOnAuthError = rememberUpdatedState(onAuthError)
    val currentOnAuthCancelled = rememberUpdatedState(onAuthCancelled)
    val currentOnNoStoredPassphrase = rememberUpdatedState(onNoStoredPassphrase)

    val prompt =
        remember(activity, biometricStore, noteId, title, subtitle, negativeButtonText) {
            if (activity == null || biometricStore == null || noteId.isBlank()) {
                null
            } else {
                val executor = ContextCompat.getMainExecutor(activity)
                val store = biometricStore
                val id = noteId
                BiometricPrompt(
                    activity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            val cipher = result.cryptoObject?.cipher
                            if (cipher == null) {
                                currentOnDecryptFailed.value()
                                return
                            }
                            val encBody = currentEncryptedBody.value()
                            if (encBody.isNullOrBlank()) {
                                currentOnDecryptFailed.value()
                                return
                            }
                            scope.launch {
                                val passChars =
                                    withContext(Dispatchers.IO) {
                                        store.loadPassphrase(id, cipher)
                                    }
                                if (passChars == null) {
                                    withContext(Dispatchers.Main) {
                                        currentOnDecryptFailed.value()
                                    }
                                    return@launch
                                }
                                val result =
                                    withContext(Dispatchers.Default) {
                                        XChaCha20Decryptor.decryptWithReason(encBody, passChars)
                                    }
                                withContext(Dispatchers.Main) {
                                    val plain = result.plaintext
                                    if (plain != null) {
                                        currentOnDecrypted.value(plain, result.usedLegacyDataOrder, passChars)
                                    } else {
                                        passChars.clearPassphrase()
                                        currentOnDecryptFailed.value()
                                    }
                                }
                            }
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                            ) {
                                currentOnAuthCancelled.value()
                            } else {
                                currentOnAuthError.value(errorCode, errString)
                            }
                        }

                        override fun onAuthenticationFailed() {}
                    },
                )
            }
        }

    DisposableEffect(prompt) {
        onDispose { prompt?.cancelAuthentication() }
    }

    return remember(prompt, biometricStore, noteId, title, subtitle, negativeButtonText) {
        BiometricNoteUnlockController(
            launchUnlock = {
                val p = prompt
                val store = biometricStore
                if (p == null || store == null || noteId.isBlank()) return@BiometricNoteUnlockController
                scope.launch {
                    val cipher =
                        withContext(Dispatchers.IO) {
                            store.getCipherForDecrypt(noteId)
                        }
                    if (cipher == null) {
                        currentOnNoStoredPassphrase.value()
                        return@launch
                    }
                    p.authenticate(
                        BiometricPrompt.PromptInfo.Builder()
                            .setTitle(title)
                            .setSubtitle(subtitle)
                            .setNegativeButtonText(negativeButtonText)
                            .build(),
                        BiometricPrompt.CryptoObject(cipher),
                    )
                }
            },
            cancelAuthentication = { prompt?.cancelAuthentication() },
        )
    }
}
