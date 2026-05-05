package com.jotty.android.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.jotty.android.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stores API keys in [EncryptedSharedPreferences] backed by the Android Keystore
 * (AES256-GCM master key, AES256-SIV key encryption, AES256-GCM value encryption).
 *
 * [isEncrypted] is `true` when the encrypted store is available. When it is `false`
 * (extremely rare — corrupted Keystore, certain Android 6–8 OEM bugs), all methods
 * are no-ops and API keys remain stored in DataStore as-is; no silent plain-text
 * fallback file is created.
 *
 * [encryptedPrefs] is initialised lazily so the Keystore I/O happens on the first
 * background access rather than on the main-thread constructor call.
 */
class ApiKeyStore(private val context: Context) {

    private val encryptedPrefs: SharedPreferences? by lazy {
        runCatching {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse { e ->
            AppLog.e(TAG, "EncryptedSharedPreferences unavailable — API keys will remain in DataStore unencrypted", e)
            null
        }
    }

    /**
     * `true` when EncryptedSharedPreferences is available and keys are encrypted on disk.
     * `false` means the Android Keystore is unavailable; API keys are not migrated and
     * remain in their current storage location (DataStore plain text).
     */
    val isEncrypted: Boolean get() = encryptedPrefs != null

    /** Returns the encrypted API key for [instanceId], or `null` if absent or encryption unavailable. */
    fun getApiKey(instanceId: String): String? =
        encryptedPrefs?.getString(prefKey(instanceId), null)?.takeIf { it.isNotBlank() }

    /**
     * Persists [apiKey] using [commit] (durable, blocking) on [Dispatchers.IO].
     * Callers must ensure this completes before the corresponding DataStore write so
     * a crash between the two leaves a key in the encrypted store (harmless orphan)
     * rather than an instance with no key.
     */
    suspend fun setApiKey(instanceId: String, apiKey: String) {
        if (apiKey.isBlank()) return
        withContext(Dispatchers.IO) {
            encryptedPrefs?.edit()?.putString(prefKey(instanceId), apiKey)?.commit()
        }
    }

    /** Removes the key for [instanceId] durably. */
    suspend fun removeApiKey(instanceId: String) {
        withContext(Dispatchers.IO) {
            encryptedPrefs?.edit()?.remove(prefKey(instanceId))?.commit()
        }
    }

    /** Removes all stored API keys. Call from [SettingsRepository.clearAll]. */
    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            encryptedPrefs?.edit()?.clear()?.commit()
        }
    }

    private fun prefKey(instanceId: String) = "${PREF_KEY_PREFIX}$instanceId"

    companion object {
        private const val PREFS_NAME = "jotty_api_keys"
        private const val PREF_KEY_PREFIX = "api_key_"
        private const val TAG = "ApiKeyStore"
    }
}
