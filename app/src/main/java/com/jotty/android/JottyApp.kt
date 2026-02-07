package com.jotty.android

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.jotty.android.data.preferences.SettingsRepository
import com.jotty.android.util.AppLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class JottyApp : Application() {
    val settingsRepository by lazy { SettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            settingsRepository.debugLoggingEnabled.first().let { AppLog.setDebugEnabled(it) }
        }
    }
}
