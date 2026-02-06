package com.jotty.android.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jotty_settings")

class SettingsRepository(private val context: Context) {

    val serverUrl: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_SERVER_URL]
    }

    val apiKey: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_API_KEY]
    }

    val isConfigured: Flow<Boolean> = context.dataStore.data.map { prefs ->
        !prefs[KEY_SERVER_URL].isNullOrBlank() && !prefs[KEY_API_KEY].isNullOrBlank()
    }

    /** Theme: null/"system" = follow system, "light", "dark" */
    val theme: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME].takeIf { !it.isNullOrBlank() }
    }.catch { emit(null) }

    /** Start tab: "checklists", "notes", "settings". Default checklists. */
    val startTab: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_START_TAB].takeIf { !it.isNullOrBlank() }
    }.catch { emit(null) }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[KEY_SERVER_URL] = url.trim() }
    }

    suspend fun setApiKey(key: String) {
        context.dataStore.edit { it[KEY_API_KEY] = key.trim() }
    }

    suspend fun setTheme(value: String?) {
        context.dataStore.edit {
            if (value.isNullOrBlank()) it.remove(KEY_THEME) else it[KEY_THEME] = value
        }
    }

    suspend fun setStartTab(value: String?) {
        context.dataStore.edit {
            if (value.isNullOrBlank()) it.remove(KEY_START_TAB) else it[KEY_START_TAB] = value
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_START_TAB = stringPreferencesKey("start_tab")
    }
}
