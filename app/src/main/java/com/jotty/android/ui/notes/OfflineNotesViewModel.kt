package com.jotty.android.ui.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.local.JottyDatabase
import com.jotty.android.data.local.OfflineNotesRepository
import com.jotty.android.data.local.scheduleInitialOfflineSyncWhenEmpty
import kotlinx.coroutines.flow.map

/**
 * Owns [OfflineNotesRepository] for the duration of the Notes destination.
 *
 * Using AndroidViewModel ties the repository's lifecycle to the ViewModel, which means
 * [OfflineNotesRepository.close] (unregister network callback + cancel coroutine scope)
 * is called when the user navigates away permanently or the process is destroyed —
 * not on every recomposition.
 *
 * A new instance is created per [instanceId] via [Factory] and the `key` parameter of
 * `viewModel()`. Changing instances destroys the old ViewModel (triggering close) and
 * creates a fresh one.
 *
 */
class OfflineNotesViewModel(
    application: Application,
    instanceId: String,
    api: JottyApi,
) : AndroidViewModel(application) {
    val repository: OfflineNotesRepository =
        OfflineNotesRepository(
            context = application,
            database = JottyDatabase.getDatabase(application),
            instanceId = instanceId,
            api = api,
        )

    init {
        scheduleInitialOfflineSyncWhenEmpty(
            observeCacheEmpty = repository.getNotesFlow().map { it.isEmpty() },
            sync = { repository.syncNotes() },
        )
    }

    override fun onCleared() {
        repository.close()
    }

    class Factory(
        private val application: Application,
        private val instanceId: String,
        private val api: JottyApi,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = OfflineNotesViewModel(application, instanceId, api) as T
    }
}
