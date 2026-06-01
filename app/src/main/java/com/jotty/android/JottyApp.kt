package com.jotty.android

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.jotty.android.data.encryption.NoteDecryptionSession
import com.jotty.android.data.local.NetworkConnectivityMonitor
import com.jotty.android.data.local.OfflineSyncWorker
import com.jotty.android.data.preferences.SettingsRepository
import com.jotty.android.util.AppLog

class JottyApp : Application() {
    val settingsRepository by lazy { SettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        AppLog.installCrashHandler()
        NetworkConnectivityMonitor.ensureStarted(this)
        OfflineSyncWorker.schedule(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : androidx.lifecycle.DefaultLifecycleObserver {
                override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                    NetworkConnectivityMonitor.refreshIfStarted(this@JottyApp)
                }

                override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                    NoteDecryptionSession.clear()
                }
            },
        )
    }
}
