package com.jotty.android.ui.checklists

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.local.JottyDatabase
import com.jotty.android.data.local.OfflineChecklistsRepository

/**
 * Owns [OfflineChecklistsRepository] for the duration of the Checklists destination.
 * Calls [OfflineChecklistsRepository.close] in [onCleared] to unregister the network
 * callback and cancel the coroutine scope — same lifecycle pattern as [OfflineNotesViewModel].
 */
class OfflineChecklistsViewModel(
    application: Application,
    instanceId: String,
    api: JottyApi,
) : AndroidViewModel(application) {
    val repository: OfflineChecklistsRepository =
        OfflineChecklistsRepository(
            context = application,
            database = JottyDatabase.getDatabase(application),
            instanceId = instanceId,
            api = api,
        )

    override fun onCleared() {
        repository.close()
    }

    class Factory(
        private val application: Application,
        private val instanceId: String,
        private val api: JottyApi,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = OfflineChecklistsViewModel(application, instanceId, api) as T
    }
}
