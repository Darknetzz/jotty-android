package com.jotty.android.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jotty.android.ui.theme.DEFAULT_CUSTOM_ACCENT_HEX
import com.jotty.android.ui.theme.normalizeThemeAccentHex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val gson = Gson()
private val instancesType = object : TypeToken<List<JottyInstance>>() {}.type

class SettingsRepository(
    private val context: Context,
    private val apiKeyStore: ApiKeyStorage = ApiKeyStore(context),
) {
    // API keys are stored in hardware-backed EncryptedSharedPreferences when available.
    // The instances JSON in DataStore stores apiKey as "" after migration to encrypted storage.

    /** Enrich a parsed instance with its API key from [ApiKeyStore]. */
    private fun JottyInstance.withStoredApiKey(): JottyInstance {
        val stored = apiKeyStore.getApiKey(id)
        // Prefer the encrypted store; fall back to JSON value (pre-migration data).
        return if (stored != null) copy(apiKey = stored) else this
    }

    val instances: Flow<List<JottyInstance>> =
        context.jottySettingsDataStore.data.map { prefs ->
            parseInstances(prefs[KEY_INSTANCES]).orEmpty().map { it.withStoredApiKey() }
        }.catch { emit(emptyList()) }

    val currentInstanceId: Flow<String?> =
        context.jottySettingsDataStore.data.map { prefs ->
            prefs[KEY_CURRENT_INSTANCE_ID].takeIf { !it.isNullOrBlank() }
        }.catch { emit(null) }

    /** Default instance to select when opening app (e.g. after install). */
    val defaultInstanceId: Flow<String?> =
        context.jottySettingsDataStore.data.map { prefs ->
            prefs[KEY_DEFAULT_INSTANCE_ID].takeIf { !it.isNullOrBlank() }
        }.catch { emit(null) }

    val currentInstance: Flow<JottyInstance?> =
        context.jottySettingsDataStore.data.map { prefs ->
            val list = parseInstances(prefs[KEY_INSTANCES]) ?: emptyList()
            val id = prefs[KEY_CURRENT_INSTANCE_ID]?.takeIf { it.isNotBlank() }
            list.find { it.id == id }?.withStoredApiKey()
        }.catch { emit(null) }

    val serverUrl: Flow<String?> = currentInstance.map { it?.serverUrl }
    val apiKey: Flow<String?> = currentInstance.map { it?.apiKey }

    val isConfigured: Flow<Boolean> = currentInstance.map { it != null }

    /** Theme mode: null/"system" = follow system; "light"; "dark". */
    val themeMode: Flow<String?> =
        context.jottySettingsDataStore.data.map { prefs ->
            prefs[KEY_THEME_MODE].takeIf { !it.isNullOrBlank() }
                ?: migrateThemeModeFromLegacy(prefs[KEY_THEME])
        }.catch { emit(null) }

    /** Theme color: preset id or "custom". Default "default". */
    val themeColor: Flow<String> =
        context.jottySettingsDataStore.data.map { prefs ->
            prefs[KEY_THEME_COLOR].takeIf { !it.isNullOrBlank() }
                ?: migrateThemeColorFromLegacy(prefs[KEY_THEME])
                ?: "default"
        }.catch { emit("default") }

    /** Custom accent `#RRGGBB` when [themeColor] is "custom". Default [DEFAULT_CUSTOM_ACCENT_HEX]. */
    val themeCustomAccentHex: Flow<String> =
        context.jottySettingsDataStore.data.map { prefs ->
            prefs[KEY_THEME_CUSTOM_ACCENT]?.takeIf { !it.isNullOrBlank() }
                ?: DEFAULT_CUSTOM_ACCENT_HEX
        }.catch { emit(DEFAULT_CUSTOM_ACCENT_HEX) }

    /** Tint background/surface with the custom accent (vs neutral gray bases). Default false. */
    val themeCustomTintedBackgrounds: Flow<Boolean> =
        context.jottySettingsDataStore.data.map { prefs ->
            prefs[KEY_THEME_CUSTOM_TINTED] ?: false
        }.catch { emit(false) }

    /** Start tab: "checklists", "notes", "settings". Default checklists. */
    val startTab: Flow<String?> =
        context.jottySettingsDataStore.data.map { prefs ->
            prefs[KEY_START_TAB].takeIf { !it.isNullOrBlank() }
        }.catch { emit(null) }

    /** Swipe to delete list/note rows. Default false. */
    val swipeToDeleteEnabled: Flow<Boolean> =
        context.jottySettingsDataStore.data.map { prefs ->
            prefs[KEY_SWIPE_TO_DELETE] ?: false
        }.catch { emit(false) }

    /** Show note body preview on list cards. Default true. */
    val noteListPreviewEnabled: Flow<Boolean> =
        context.jottySettingsDataStore.data.map { prefs ->
            prefs[KEY_NOTE_LIST_PREVIEW] ?: true
        }.catch { emit(true) }

    /** Content padding: "compact" (8dp vertical) or "comfortable" (16dp vertical). Default "comfortable". */
    val contentPaddingMode: Flow<String> =
        context.jottySettingsDataStore.data.map { prefs ->
            prefs[KEY_CONTENT_PADDING].takeIf { !it.isNullOrBlank() } ?: "comfortable"
        }.catch { emit("comfortable") }

    /** List sort order for notes and checklists: "updated", "created", "title". Default "updated". */
    val listSortOption: Flow<String> =
        context.jottySettingsDataStore.data.map { prefs ->
            prefs[KEY_LIST_SORT].takeIf { !it.isNullOrBlank() } ?: "updated"
        }.catch { emit("updated") }

    /** Persisted category filter for the notes list; null = all categories. */
    val notesCategoryFilter: Flow<String?> =
        context.jottySettingsDataStore.data.map { prefs ->
            prefs[KEY_NOTES_CATEGORY_FILTER].takeIf { !it.isNullOrBlank() }
        }.catch { emit(null) }

    /** Persisted category filter for the checklists list; null = all categories. */
    val checklistsCategoryFilter: Flow<String?> =
        context.jottySettingsDataStore.data.map { prefs ->
            prefs[KEY_CHECKLISTS_CATEGORY_FILTER].takeIf { !it.isNullOrBlank() }
        }.catch { emit(null) }

    /** Reader text scale for note content and editor: 0.75, 0.85, 1.0, 1.15, 1.3. Default 1.0. */
    val readerTextScale: Flow<Float> =
        context.jottySettingsDataStore.data.map { prefs ->
            prefs[KEY_READER_TEXT_SCALE] ?: 1.0f
        }.catch { emit(1.0f) }

    /** Motion effects: unset/"on" = off (default); "off" = on; "system" = follow device accessibility. */
    val reducedMotionMode: Flow<String?> =
        context.jottySettingsDataStore.data.map { prefs ->
            prefs[KEY_REDUCED_MOTION].takeIf { !it.isNullOrBlank() }
        }.catch { emit(null) }

    /** Local storage & sync: enable local cache and sync. Default true. */
    val offlineModeEnabled: Flow<Boolean> =
        context.jottySettingsDataStore.data.map { prefs ->
            prefs[KEY_OFFLINE_MODE] ?: true
        }.catch { emit(true) }

    /** Auto-prompt biometric when opening an encrypted note with a stored passphrase. Default true. */
    val biometricAutoUnlockEnabled: Flow<Boolean> =
        context.jottySettingsDataStore.data.map { prefs ->
            prefs[KEY_BIOMETRIC_AUTO_UNLOCK] ?: true
        }.catch { emit(true) }

    /** Offer to remember passphrase with biometric after password decrypt. Default true. */
    val biometricSaveOfferEnabled: Flow<Boolean> =
        context.jottySettingsDataStore.data.map { prefs ->
            prefs[KEY_BIOMETRIC_SAVE_OFFER] ?: true
        }.catch { emit(true) }

    /**
     * GitHub update check channel: `"stable"` (latest semver release) or `"dev"` (`dev-latest` pre-release).
     * Default stable.
     */
    val updateChannel: Flow<String> =
        context.jottySettingsDataStore.data.map { prefs ->
            prefs[KEY_UPDATE_CHANNEL].takeIf { !it.isNullOrBlank() } ?: "stable"
        }.catch { emit("stable") }

    /**
     * Adds or updates an instance. When [setAsCurrent] is true, also sets it as the current instance.
     *
     * When [ApiKeyStore.isEncrypted] is true (typical):
     *  - API key is written to [ApiKeyStore] (commit, durable) BEFORE the DataStore edit.
     *    A crash after the encrypted write leaves a harmless orphan key, never a missing key.
     *  - DataStore JSON stores `apiKey=""` so the key is never on disk in plain text.
     *
     * When encryption is unavailable: instance is stored as-is in DataStore (plain text).
     */
    suspend fun addInstance(
        instance: JottyInstance,
        setAsCurrent: Boolean = true,
    ) {
        val encrypted = apiKeyStore.isEncrypted
        if (encrypted) {
            apiKeyStore.setApiKey(instance.id, instance.apiKey) // commit + Dispatchers.IO inside
        }
        context.jottySettingsDataStore.edit { prefs ->
            val toStore = if (encrypted) instance.copy(apiKey = "") else instance
            val list = parseInstances(prefs[KEY_INSTANCES]).orEmpty().toMutableList()
            if (list.none { it.id == toStore.id }) {
                list.add(toStore)
            } else {
                list[list.indexOfFirst { it.id == toStore.id }] = toStore
            }
            prefs[KEY_INSTANCES] = gson.toJson(list)
            if (setAsCurrent) prefs[KEY_CURRENT_INSTANCE_ID] = instance.id
        }
    }

    suspend fun removeInstance(id: String) {
        // Remove instance from DataStore first, then clean up the key.
        // A crash between the two leaves an orphan key — harmless, never an instance with no key.
        context.jottySettingsDataStore.edit { prefs ->
            val list = parseInstances(prefs[KEY_INSTANCES]).orEmpty().filter { it.id != id }
            prefs[KEY_INSTANCES] = gson.toJson(list)
            if (prefs[KEY_CURRENT_INSTANCE_ID] == id) {
                val defaultId = prefs[KEY_DEFAULT_INSTANCE_ID]?.takeIf { it != id }
                val fallback =
                    defaultId?.takeIf { list.any { inst -> inst.id == defaultId } }
                        ?: list.firstOrNull()?.id
                if (fallback != null) {
                    prefs[KEY_CURRENT_INSTANCE_ID] = fallback
                } else {
                    prefs.remove(KEY_CURRENT_INSTANCE_ID)
                }
            }
            if (prefs[KEY_DEFAULT_INSTANCE_ID] == id) prefs.remove(KEY_DEFAULT_INSTANCE_ID)
        }
        apiKeyStore.removeApiKey(id)
    }

    suspend fun setCurrentInstanceId(id: String?) {
        context.jottySettingsDataStore.edit {
            if (id.isNullOrBlank()) it.remove(KEY_CURRENT_INSTANCE_ID) else it[KEY_CURRENT_INSTANCE_ID] = id
        }
    }

    suspend fun setDefaultInstanceId(id: String?) {
        context.jottySettingsDataStore.edit {
            val safeId = id?.takeIf { it.isNotBlank() }
            if (safeId == null) it.remove(KEY_DEFAULT_INSTANCE_ID) else it[KEY_DEFAULT_INSTANCE_ID] = safeId
        }
    }

    /** Disconnect: clear current instance but keep all instances saved. */
    suspend fun disconnect() {
        context.jottySettingsDataStore.edit { it.remove(KEY_CURRENT_INSTANCE_ID) }
    }

    suspend fun setThemeMode(value: String?) {
        context.jottySettingsDataStore.edit {
            if (value.isNullOrBlank()) it.remove(KEY_THEME_MODE) else it[KEY_THEME_MODE] = value
        }
    }

    suspend fun setThemeColor(value: String) {
        context.jottySettingsDataStore.edit {
            if (value == "default") it.remove(KEY_THEME_COLOR) else it[KEY_THEME_COLOR] = value
        }
    }

    suspend fun setThemeCustomAccentHex(hex: String) {
        val normalized = normalizeThemeAccentHex(hex) ?: DEFAULT_CUSTOM_ACCENT_HEX
        context.jottySettingsDataStore.edit {
            if (normalized == DEFAULT_CUSTOM_ACCENT_HEX) {
                it.remove(KEY_THEME_CUSTOM_ACCENT)
            } else {
                it[KEY_THEME_CUSTOM_ACCENT] = normalized
            }
            it[KEY_THEME_COLOR] = "custom"
        }
    }

    suspend fun setThemeCustomTintedBackgrounds(value: Boolean) {
        context.jottySettingsDataStore.edit {
            if (value) it[KEY_THEME_CUSTOM_TINTED] = true else it.remove(KEY_THEME_CUSTOM_TINTED)
        }
    }

    suspend fun setStartTab(value: String?) {
        context.jottySettingsDataStore.edit {
            if (value.isNullOrBlank()) it.remove(KEY_START_TAB) else it[KEY_START_TAB] = value
        }
    }

    suspend fun setSwipeToDeleteEnabled(value: Boolean) {
        context.jottySettingsDataStore.edit { it[KEY_SWIPE_TO_DELETE] = value }
    }

    suspend fun setNoteListPreviewEnabled(value: Boolean) {
        context.jottySettingsDataStore.edit {
            if (value) it.remove(KEY_NOTE_LIST_PREVIEW) else it[KEY_NOTE_LIST_PREVIEW] = false
        }
    }

    suspend fun setContentPaddingMode(value: String) {
        context.jottySettingsDataStore.edit {
            if (value == "comfortable") it.remove(KEY_CONTENT_PADDING) else it[KEY_CONTENT_PADDING] = value
        }
    }

    suspend fun setListSortOption(value: String) {
        context.jottySettingsDataStore.edit {
            if (value == "updated") it.remove(KEY_LIST_SORT) else it[KEY_LIST_SORT] = value
        }
    }

    suspend fun setNotesCategoryFilter(value: String?) {
        context.jottySettingsDataStore.edit {
            if (value.isNullOrBlank()) it.remove(KEY_NOTES_CATEGORY_FILTER) else it[KEY_NOTES_CATEGORY_FILTER] = value
        }
    }

    suspend fun setChecklistsCategoryFilter(value: String?) {
        context.jottySettingsDataStore.edit {
            if (value.isNullOrBlank()) it.remove(KEY_CHECKLISTS_CATEGORY_FILTER) else it[KEY_CHECKLISTS_CATEGORY_FILTER] = value
        }
    }

    suspend fun setReaderTextScale(value: Float) {
        context.jottySettingsDataStore.edit {
            if (value == 1.0f) it.remove(KEY_READER_TEXT_SCALE) else it[KEY_READER_TEXT_SCALE] = value
        }
    }

    suspend fun setReducedMotionMode(value: String?) {
        context.jottySettingsDataStore.edit {
            val v = value?.lowercase()?.trim()
            when {
                v.isNullOrBlank() -> it.remove(KEY_REDUCED_MOTION)
                v == "system" -> it[KEY_REDUCED_MOTION] = "system"
                else -> it[KEY_REDUCED_MOTION] = v
            }
        }
    }

    suspend fun setBiometricAutoUnlockEnabled(value: Boolean) {
        context.jottySettingsDataStore.edit { it[KEY_BIOMETRIC_AUTO_UNLOCK] = value }
    }

    suspend fun setBiometricSaveOfferEnabled(value: Boolean) {
        context.jottySettingsDataStore.edit { it[KEY_BIOMETRIC_SAVE_OFFER] = value }
    }

    suspend fun setOfflineModeEnabled(value: Boolean) {
        context.jottySettingsDataStore.edit { it[KEY_OFFLINE_MODE] = value }
    }

    suspend fun setUpdateChannel(value: String) {
        context.jottySettingsDataStore.edit {
            val v = value.lowercase().trim()
            if (v == "stable") it.remove(KEY_UPDATE_CHANNEL) else it[KEY_UPDATE_CHANNEL] = v
        }
    }

    /** Clear all data (instances + app preferences) including encrypted API keys. */
    suspend fun clearAll() {
        // DataStore first: a crash before the encrypted clear leaves harmless orphan keys.
        // The reverse would leave instances with no keys, breaking auth with no remediation.
        context.jottySettingsDataStore.edit { it.clear() }
        apiKeyStore.clearAll()
    }

    /**
     * One-time migration: if old server_url/api_key exist and no instances, create one instance and clear legacy keys.
     *
     * When [ApiKeyStorage.isEncrypted] is false, the API key stays in the instances JSON (same as pre-migration storage)
     * so we never clear legacy keys without retaining a usable credential.
     */
    suspend fun migrateFromLegacyIfNeeded() {
        val prefs = context.jottySettingsDataStore.data.first()
        if (!prefs[KEY_INSTANCES].isNullOrBlank()) return
        val url = prefs[KEY_SERVER_URL]?.takeIf { it.isNotBlank() } ?: return
        val key = prefs[KEY_API_KEY]?.takeIf { it.isNotBlank() } ?: return
        val trimmedKey = key.trim()
        val encrypted = apiKeyStore.isEncrypted
        val instance =
            JottyInstance(
                id = UUID.randomUUID().toString(),
                name = "Jotty",
                serverUrl = url.trim(),
                apiKey = if (encrypted) "" else trimmedKey,
            )
        if (encrypted) {
            // Write encrypted key (commit, durable) before DataStore edit.
            // A crash after this write and before the edit leaves a harmless orphan key;
            // the next launch re-runs this migration with a fresh UUID.
            apiKeyStore.setApiKey(instance.id, trimmedKey)
        }
        context.jottySettingsDataStore.edit { p ->
            if (!p[KEY_INSTANCES].isNullOrBlank()) return@edit // concurrent re-entry guard
            p[KEY_INSTANCES] = gson.toJson(listOf(instance))
            p[KEY_CURRENT_INSTANCE_ID] = instance.id
            p.remove(KEY_SERVER_URL)
            p.remove(KEY_API_KEY)
        }
    }

    /**
     * One-time migration: move any API keys still stored as plain text in the instances JSON
     * to [ApiKeyStore] (EncryptedSharedPreferences), then blank them out in DataStore.
     *
     * Safe to call on every launch — it is a no-op when all keys are already encrypted.
     * No-op when [ApiKeyStore.isEncrypted] is false (Keystore unavailable).
     */
    suspend fun migrateApiKeysToEncryptedStoreIfNeeded() {
        if (!apiKeyStore.isEncrypted) return
        val prefs = context.jottySettingsDataStore.data.first()
        val list = parseInstances(prefs[KEY_INSTANCES]).orEmpty()
        val plainTextInstances = list.filter { it.apiKey.isNotBlank() }
        if (plainTextInstances.isEmpty()) return
        // Write all keys to encrypted store first (commit, durable).
        // A crash before the DataStore edit leaves encrypted keys written but DataStore unchanged;
        // the next launch finds the same plaintext keys and re-runs — fully idempotent.
        plainTextInstances.forEach { instance ->
            if (apiKeyStore.getApiKey(instance.id) == null) {
                apiKeyStore.setApiKey(instance.id, instance.apiKey)
            }
            // If already in encrypted store (key non-null), the DataStore copy is stale —
            // still blank it below regardless.
        }
        context.jottySettingsDataStore.edit { p ->
            val current = parseInstances(p[KEY_INSTANCES]).orEmpty()
            val migrated = current.map { if (it.apiKey.isNotBlank()) it.copy(apiKey = "") else it }
            p[KEY_INSTANCES] = gson.toJson(migrated)
        }
    }

    /** One-time migration: split legacy KEY_THEME into KEY_THEME_MODE and KEY_THEME_COLOR. */
    suspend fun migrateThemeToModeAndColorIfNeeded() {
        context.jottySettingsDataStore.edit { prefs ->
            val old = prefs[KEY_THEME]?.takeIf { it.isNotBlank() } ?: return@edit
            if (prefs[KEY_THEME_MODE] != null) return@edit
            val (mode, color) =
                when (old) {
                    "light" -> "light" to "default"
                    "dark" -> "dark" to "default"
                    "amoled" -> "dark" to "amoled"
                    "sepia" -> "light" to "sepia"
                    "midnight" -> "dark" to "midnight"
                    "rose" -> "light" to "rose"
                    "ocean" -> "light" to "ocean"
                    "forest" -> "light" to "forest"
                    else -> null to "default"
                }
            if (mode != null) prefs[KEY_THEME_MODE] = mode
            if (color != "default") prefs[KEY_THEME_COLOR] = color
            prefs.remove(KEY_THEME)
        }
    }

    companion object {
        private val KEY_INSTANCES = stringPreferencesKey("instances")
        private val KEY_CURRENT_INSTANCE_ID = stringPreferencesKey("current_instance_id")
        private val KEY_DEFAULT_INSTANCE_ID = stringPreferencesKey("default_instance_id")
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_THEME_COLOR = stringPreferencesKey("theme_color")
        private val KEY_THEME_CUSTOM_ACCENT = stringPreferencesKey("theme_custom_accent")
        private val KEY_THEME_CUSTOM_TINTED = booleanPreferencesKey("theme_custom_tinted")

        private fun migrateThemeModeFromLegacy(oldTheme: String?): String? {
            if (oldTheme.isNullOrBlank()) return null
            return when (oldTheme) {
                "light" -> "light"
                "dark", "amoled", "midnight" -> "dark"
                "sepia", "rose", "ocean", "forest" -> "light"
                else -> null
            }
        }

        private fun migrateThemeColorFromLegacy(oldTheme: String?): String? {
            if (oldTheme.isNullOrBlank()) return null
            return when (oldTheme) {
                "light", "dark" -> "default"
                "amoled" -> "amoled"
                "sepia" -> "sepia"
                "midnight" -> "midnight"
                "rose" -> "rose"
                "ocean" -> "ocean"
                "forest" -> "forest"
                else -> "default"
            }
        }

        private val KEY_START_TAB = stringPreferencesKey("start_tab")
        private val KEY_SWIPE_TO_DELETE = booleanPreferencesKey("swipe_to_delete_enabled")
        private val KEY_NOTE_LIST_PREVIEW = booleanPreferencesKey("note_list_preview_enabled")
        private val KEY_CONTENT_PADDING = stringPreferencesKey("content_padding")
        private val KEY_LIST_SORT = stringPreferencesKey("list_sort_option")
        private val KEY_NOTES_CATEGORY_FILTER = stringPreferencesKey("notes_category_filter")
        private val KEY_CHECKLISTS_CATEGORY_FILTER = stringPreferencesKey("checklists_category_filter")
        private val KEY_READER_TEXT_SCALE = floatPreferencesKey("reader_text_scale")
        private val KEY_REDUCED_MOTION = stringPreferencesKey("reduced_motion_mode")
        private val KEY_BIOMETRIC_AUTO_UNLOCK = booleanPreferencesKey("biometric_auto_unlock_enabled")
        private val KEY_BIOMETRIC_SAVE_OFFER = booleanPreferencesKey("biometric_save_offer_enabled")
        private val KEY_OFFLINE_MODE = booleanPreferencesKey("offline_mode_enabled")
        private val KEY_UPDATE_CHANNEL = stringPreferencesKey("update_channel")

        private fun parseInstances(json: String?): List<JottyInstance>? {
            if (json.isNullOrBlank()) return null
            return try {
                gson.fromJson<List<JottyInstance>>(json, instancesType) ?: emptyList()
            } catch (_: Exception) {
                null
            }
        }
    }
}
