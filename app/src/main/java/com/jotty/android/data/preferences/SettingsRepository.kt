package com.jotty.android.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jotty_settings")

private val gson = Gson()
private val instancesType = object : TypeToken<List<JottyInstance>>() {}.type

class SettingsRepository(private val context: Context) {

    val instances: Flow<List<JottyInstance>> = context.dataStore.data.map { prefs ->
        parseInstances(prefs[KEY_INSTANCES]).orEmpty()
    }.catch { emit(emptyList()) }

    val currentInstanceId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_CURRENT_INSTANCE_ID].takeIf { !it.isNullOrBlank() }
    }.catch { emit(null) }

    /** Default instance to select when opening app (e.g. after install). */
    val defaultInstanceId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_INSTANCE_ID].takeIf { !it.isNullOrBlank() }
    }.catch { emit(null) }

    val currentInstance: Flow<JottyInstance?> = context.dataStore.data.map { prefs ->
        val list = parseInstances(prefs[KEY_INSTANCES]) ?: emptyList()
        val id = prefs[KEY_CURRENT_INSTANCE_ID]?.takeIf { it.isNotBlank() }
        list.find { it.id == id }
    }.catch { emit(null) }

    val serverUrl: Flow<String?> = currentInstance.map { it?.serverUrl }
    val apiKey: Flow<String?> = currentInstance.map { it?.apiKey }

    val isConfigured: Flow<Boolean> = currentInstance.map { it != null }

    /** Theme mode: null/"system" = follow system; "light"; "dark". */
    val themeMode: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE].takeIf { !it.isNullOrBlank() }
            ?: migrateThemeModeFromLegacy(prefs[KEY_THEME])
    }.catch { emit(null) }

    /** Theme color: "default", "amoled", "sepia", "midnight", "rose", "ocean", "forest". Default "default". */
    val themeColor: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_COLOR].takeIf { !it.isNullOrBlank() }
            ?: migrateThemeColorFromLegacy(prefs[KEY_THEME])
            ?: "default"
    }.catch { emit("default") }

    /** Start tab: "checklists", "notes", "settings". Default checklists. */
    val startTab: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_START_TAB].takeIf { !it.isNullOrBlank() }
    }.catch { emit(null) }

    /** Swipe to delete list/note rows. Default false. */
    val swipeToDeleteEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SWIPE_TO_DELETE] ?: false
    }.catch { emit(false) }

    /**
     * Adds or updates an instance. When [setAsCurrent] is true, also sets it as the current instance.
     */
    suspend fun addInstance(instance: JottyInstance, setAsCurrent: Boolean = true) {
        context.dataStore.edit { prefs ->
            val list = parseInstances(prefs[KEY_INSTANCES]).orEmpty().toMutableList()
            if (list.none { it.id == instance.id }) list.add(instance)
            else list[list.indexOfFirst { it.id == instance.id }] = instance
            prefs[KEY_INSTANCES] = gson.toJson(list)
            if (setAsCurrent) prefs[KEY_CURRENT_INSTANCE_ID] = instance.id
        }
    }

    suspend fun removeInstance(id: String) {
        context.dataStore.edit { prefs ->
            val list = parseInstances(prefs[KEY_INSTANCES]).orEmpty().filter { it.id != id }
            prefs[KEY_INSTANCES] = gson.toJson(list)
            if (prefs[KEY_CURRENT_INSTANCE_ID] == id) {
                val defaultId = prefs[KEY_DEFAULT_INSTANCE_ID]?.takeIf { it != id }
                val fallback = defaultId?.takeIf { list.any { inst -> inst.id == defaultId } }
                    ?: list.firstOrNull()?.id
                if (fallback != null) prefs[KEY_CURRENT_INSTANCE_ID] = fallback
                else prefs.remove(KEY_CURRENT_INSTANCE_ID)
            }
            if (prefs[KEY_DEFAULT_INSTANCE_ID] == id) prefs.remove(KEY_DEFAULT_INSTANCE_ID)
        }
    }

    suspend fun setCurrentInstanceId(id: String?) {
        context.dataStore.edit {
            if (id.isNullOrBlank()) it.remove(KEY_CURRENT_INSTANCE_ID) else it[KEY_CURRENT_INSTANCE_ID] = id
        }
    }

    suspend fun setDefaultInstanceId(id: String?) {
        context.dataStore.edit {
            val safeId = id?.takeIf { it.isNotBlank() }
            if (safeId == null) it.remove(KEY_DEFAULT_INSTANCE_ID) else it[KEY_DEFAULT_INSTANCE_ID] = safeId
        }
    }

    /** Disconnect: clear current instance but keep all instances saved. */
    suspend fun disconnect() {
        context.dataStore.edit { it.remove(KEY_CURRENT_INSTANCE_ID) }
    }

    suspend fun setThemeMode(value: String?) {
        context.dataStore.edit {
            if (value.isNullOrBlank()) it.remove(KEY_THEME_MODE) else it[KEY_THEME_MODE] = value
        }
    }

    suspend fun setThemeColor(value: String) {
        context.dataStore.edit {
            if (value == "default") it.remove(KEY_THEME_COLOR) else it[KEY_THEME_COLOR] = value
        }
    }

    suspend fun setStartTab(value: String?) {
        context.dataStore.edit {
            if (value.isNullOrBlank()) it.remove(KEY_START_TAB) else it[KEY_START_TAB] = value
        }
    }

    suspend fun setSwipeToDeleteEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_SWIPE_TO_DELETE] = value }
    }

    /** Clear all data (instances + app preferences). */
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    /** One-time migration: if old server_url/api_key exist and no instances, create one instance and clear legacy keys. */
    suspend fun migrateFromLegacyIfNeeded() {
        context.dataStore.edit { prefs ->
            if (!prefs[KEY_INSTANCES].isNullOrBlank()) return@edit
            val url = prefs[KEY_SERVER_URL]?.takeIf { it.isNotBlank() } ?: return@edit
            val key = prefs[KEY_API_KEY]?.takeIf { it.isNotBlank() } ?: return@edit
            val instance = JottyInstance(
                id = UUID.randomUUID().toString(),
                name = "Jotty",
                serverUrl = url.trim(),
                apiKey = key.trim(),
            )
            prefs[KEY_INSTANCES] = gson.toJson(listOf(instance))
            prefs[KEY_CURRENT_INSTANCE_ID] = instance.id
            prefs.remove(KEY_SERVER_URL)
            prefs.remove(KEY_API_KEY)
        }
    }

    /** One-time migration: split legacy KEY_THEME into KEY_THEME_MODE and KEY_THEME_COLOR. */
    suspend fun migrateThemeToModeAndColorIfNeeded() {
        context.dataStore.edit { prefs ->
            val old = prefs[KEY_THEME]?.takeIf { it.isNotBlank() } ?: return@edit
            if (prefs[KEY_THEME_MODE] != null) return@edit
            val (mode, color) = when (old) {
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
