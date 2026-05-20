package com.jotty.android.data.encryption

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import com.jotty.android.util.AppLog
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores note-decryption passphrases encrypted with a biometric-protected AES-GCM key
 * in the Android Keystore. The key is only usable after the user authenticates with a
 * strong biometric (fingerprint / face). If the user adds a new biometric or clears all,
 * Android invalidates the key automatically — the stored passphrase is lost and the user
 * must re-enter it manually.
 *
 * Stored format in [SharedPreferences] per instance:
 *   key "iv_<instanceId>"  → Base64 GCM IV (12 bytes)
 *   key "ct_<instanceId>"  → Base64 ciphertext + tag
 */
class BiometricPassphraseStore(private val context: Context) {
    private val prefs: SharedPreferences by lazy {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** True when the device has a strong biometric enrolled and the hardware is available. */
    fun isAvailable(): Boolean {
        val bm = BiometricManager.from(context)
        return bm.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /** True when a passphrase is stored for [instanceId]. */
    fun hasPassphrase(instanceId: String): Boolean = prefs.contains(ivKey(instanceId)) && prefs.contains(ctKey(instanceId))

    /**
     * Returns an initialized [Cipher] ready for encryption. Pass this to
     * [androidx.biometric.BiometricPrompt.CryptoObject] — Android unlocks the Keystore key
     * on successful biometric auth, then call [savePassphrase] with the authenticated cipher.
     *
     * Returns null if the key cannot be created (no secure hardware).
     */
    fun getCipherForEncrypt(instanceId: String): Cipher? {
        return try {
            val key = getOrCreateKey(instanceId)
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, key)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "getCipherForEncrypt failed for $instanceId", e)
            null
        }
    }

    /**
     * Returns an initialized [Cipher] ready for decryption using the stored IV.
     * Pass this to [androidx.biometric.BiometricPrompt.CryptoObject].
     *
     * Returns null if no passphrase is stored or the key is invalid (e.g. new biometric enrolled).
     */
    fun getCipherForDecrypt(instanceId: String): Cipher? {
        return try {
            val ivB64 = prefs.getString(ivKey(instanceId), null) ?: return null
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            val key = getKey(instanceId) ?: return null
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            AppLog.d(TAG, "Biometric key invalidated for $instanceId — clearing stored passphrase")
            clearPassphrase(instanceId)
            null
        } catch (e: Exception) {
            AppLog.e(TAG, "getCipherForDecrypt failed for $instanceId", e)
            null
        }
    }

    /**
     * Encrypts and stores [passphrase] using the biometric-authenticated [cipher].
     * Must be called from the [androidx.biometric.BiometricPrompt] success callback.
     */
    fun savePassphrase(
        instanceId: String,
        passphrase: CharArray,
        cipher: Cipher,
    ) {
        val bytes = utf8Encode(passphrase)
        try {
            val ct = cipher.doFinal(bytes)
            prefs.edit()
                .putString(ivKey(instanceId), Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString(ctKey(instanceId), Base64.encodeToString(ct, Base64.NO_WRAP))
                .apply()
            AppLog.d(TAG, "Passphrase saved for $instanceId")
        } catch (e: Exception) {
            AppLog.e(TAG, "savePassphrase failed for $instanceId", e)
        } finally {
            bytes.fill(0)
        }
    }

    /**
     * Decrypts and returns the stored passphrase using the biometric-authenticated [cipher].
     * Must be called from the [androidx.biometric.BiometricPrompt] success callback.
     * Caller should clear the returned [CharArray] with [clearPassphrase] when finished.
     */
    fun loadPassphrase(
        instanceId: String,
        cipher: Cipher,
    ): CharArray? {
        return try {
            val ctB64 = prefs.getString(ctKey(instanceId), null) ?: return null
            val ct = Base64.decode(ctB64, Base64.NO_WRAP)
            val plainBytes = cipher.doFinal(ct)
            try {
                val cb = Charsets.UTF_8.decode(ByteBuffer.wrap(plainBytes))
                val n = cb.remaining()
                val out = CharArray(n)
                cb.get(out)
                out
            } finally {
                plainBytes.fill(0)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "loadPassphrase failed for $instanceId", e)
            null
        }
    }

    /** Removes stored passphrase and Keystore key for [instanceId]. */
    fun clearPassphrase(instanceId: String) {
        prefs.edit().remove(ivKey(instanceId)).remove(ctKey(instanceId)).apply()
        try {
            KeyStore.getInstance(KEYSTORE).apply {
                load(null)
                if (containsAlias(keyAlias(instanceId))) deleteEntry(keyAlias(instanceId))
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "clearPassphrase key deletion failed for $instanceId", e)
        }
    }

    /** Removes all stored passphrases (e.g. on disconnect/clear all). */
    fun clearAll() {
        val keys = prefs.all.keys.toList()
        prefs.edit().apply { keys.forEach { remove(it) } }.apply()
        try {
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            ks.aliases().asSequence()
                .filter { it.startsWith(KEY_ALIAS_PREFIX) }
                .forEach { ks.deleteEntry(it) }
        } catch (e: Exception) {
            AppLog.e(TAG, "clearAll key deletion failed", e)
        }
    }

    private fun getOrCreateKey(instanceId: String): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val alias = keyAlias(instanceId)
        ks.getKey(alias, null)?.let { return it as SecretKey }
        val spec =
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(spec)
        }.generateKey()
    }

    private fun getKey(instanceId: String): SecretKey? {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return ks.getKey(keyAlias(instanceId), null) as? SecretKey
    }

    private fun keyAlias(instanceId: String) = "$KEY_ALIAS_PREFIX$instanceId"

    private fun ivKey(instanceId: String) = "iv_$instanceId"

    private fun ctKey(instanceId: String) = "ct_$instanceId"

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val PREFS_NAME = "jotty_biometric_passphrases"
        private const val KEY_ALIAS_PREFIX = "jotty_passphrase_"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val TAG = "BiometricPassphraseStore"
    }
}
