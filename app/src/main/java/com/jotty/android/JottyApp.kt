package com.jotty.android

import android.app.Application
import com.jotty.android.data.preferences.SettingsRepository

class JottyApp : Application() {
    val settingsRepository by lazy { SettingsRepository(this) }
}
