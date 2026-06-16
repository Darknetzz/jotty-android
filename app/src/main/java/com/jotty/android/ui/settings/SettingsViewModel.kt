package com.jotty.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.encryption.BiometricPassphraseStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsViewModel : ViewModel() {
    private val _healthOk = MutableStateFlow<Boolean?>(null)
    val healthOk: StateFlow<Boolean?> = _healthOk.asStateFlow()

    private val _serverVersion = MutableStateFlow<String?>(null)
    val serverVersion: StateFlow<String?> = _serverVersion.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _storedPassphraseCount = MutableStateFlow(0)
    val storedPassphraseCount: StateFlow<Int> = _storedPassphraseCount.asStateFlow()

    private val _isExportingLogs = MutableStateFlow(false)
    val isExportingLogs: StateFlow<Boolean> = _isExportingLogs.asStateFlow()

    private val _pendingLogFile = MutableStateFlow<File?>(null)
    val pendingLogFile: StateFlow<File?> = _pendingLogFile.asStateFlow()

    fun refreshConnection(
        api: JottyApi?,
        showRefreshingIndicator: Boolean,
    ) {
        viewModelScope.launch {
            if (showRefreshingIndicator) _isRefreshing.value = true
            try {
                api?.let { a ->
                    try {
                        val health = a.health()
                        _healthOk.value = true
                        _serverVersion.value = health.version
                    } catch (_: Exception) {
                        _healthOk.value = false
                        _serverVersion.value = null
                    }
                }
            } finally {
                if (showRefreshingIndicator) _isRefreshing.value = false
            }
        }
    }

    fun refreshBiometricCount(biometricStore: BiometricPassphraseStore) {
        viewModelScope.launch {
            _storedPassphraseCount.value =
                withContext(Dispatchers.IO) {
                    biometricStore.pruneInvalidatedPassphrases()
                    biometricStore.storedCount()
                }
        }
    }

    fun setExportingLogs(exporting: Boolean) {
        _isExportingLogs.value = exporting
    }

    fun setPendingLogFile(file: File?) {
        _pendingLogFile.value = file
    }

    fun clearBiometricPassphrases(biometricStore: BiometricPassphraseStore) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { biometricStore.clearAll() }
            refreshBiometricCount(biometricStore)
        }
    }
}
