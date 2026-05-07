package com.jotty.android.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** Single [DataStore] for app preferences (`jotty_settings`). Exposed for tests that seed legacy keys. */
internal val Context.jottySettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "jotty_settings")
