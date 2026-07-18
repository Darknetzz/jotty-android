package com.jotty.android.ui.sync

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.local.ChecklistSyncPayload
import com.jotty.android.data.local.JottyDatabase
import com.jotty.android.data.local.NoteSyncPayload
import com.jotty.android.data.local.OfflineChecklistsRepository
import com.jotty.android.data.local.OfflineNotesRepository
import com.jotty.android.data.local.PendingSyncItem
import com.jotty.android.data.local.SyncBackup
import com.jotty.android.data.local.SyncBackupKind
import com.jotty.android.data.local.SyncPayloadCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PendingSyncViewModel(
    application: Application,
    instanceId: String,
    api: JottyApi,
) : AndroidViewModel(application) {
    private val notesRepo =
        OfflineNotesRepository(
            context = application,
            database = JottyDatabase.getDatabase(application),
            instanceId = instanceId,
            api = api,
        )
    private val checklistsRepo =
        OfflineChecklistsRepository(
            context = application,
            database = JottyDatabase.getDatabase(application),
            instanceId = instanceId,
            api = api,
        )

    val pendingItems: StateFlow<List<PendingSyncItem>> =
        combine(
            notesRepo.getPendingSyncItemsFlow(),
            checklistsRepo.getPendingSyncItemsFlow(),
        ) { notes, checklists ->
            (notes + checklists).sortedByDescending { it.dirtySinceEpochMs ?: 0L }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _detailLoading = MutableStateFlow(false)
    val detailLoading: StateFlow<Boolean> = _detailLoading

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError

    private val _localNote = MutableStateFlow<NoteSyncPayload?>(null)
    val localNote: StateFlow<NoteSyncPayload?> = _localNote
    private val _serverNote = MutableStateFlow<NoteSyncPayload?>(null)
    val serverNote: StateFlow<NoteSyncPayload?> = _serverNote
    private val _localChecklist = MutableStateFlow<ChecklistSyncPayload?>(null)
    val localChecklist: StateFlow<ChecklistSyncPayload?> = _localChecklist
    private val _serverChecklist = MutableStateFlow<ChecklistSyncPayload?>(null)
    val serverChecklist: StateFlow<ChecklistSyncPayload?> = _serverChecklist
    private val _backups = MutableStateFlow<List<SyncBackup>>(emptyList())
    val backups: StateFlow<List<SyncBackup>> = _backups
    private val _detailItem = MutableStateFlow<PendingSyncItem?>(null)
    val detailItem: StateFlow<PendingSyncItem?> = _detailItem

    fun loadDetail(
        kind: SyncBackupKind,
        id: String,
    ) {
        viewModelScope.launch {
            _detailLoading.value = true
            _actionError.value = null
            try {
                when (kind) {
                    SyncBackupKind.NOTE -> {
                        _detailItem.value = notesRepo.getPendingSyncItem(id)
                        _localNote.value = notesRepo.getLocalSyncPayload(id)
                        _serverNote.value = notesRepo.getServerSyncPayload(id)
                        _localChecklist.value = null
                        _serverChecklist.value = null
                        _backups.value = notesRepo.listSyncBackups(id)
                    }
                    SyncBackupKind.CHECKLIST -> {
                        _detailItem.value = checklistsRepo.getPendingSyncItem(id)
                        _localChecklist.value = checklistsRepo.getLocalSyncPayload(id)
                        _serverChecklist.value = checklistsRepo.getServerSyncPayload(id)
                        _localNote.value = null
                        _serverNote.value = null
                        _backups.value = checklistsRepo.listSyncBackups(id)
                    }
                }
            } finally {
                _detailLoading.value = false
            }
        }
    }

    fun forcePush(
        kind: SyncBackupKind,
        id: String,
        onDone: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            _detailLoading.value = true
            _actionError.value = null
            val result =
                when (kind) {
                    SyncBackupKind.NOTE -> notesRepo.forcePushLocal(id)
                    SyncBackupKind.CHECKLIST -> checklistsRepo.forcePushLocal(id)
                }
            _detailLoading.value = false
            result
                .onSuccess {
                    loadDetail(kind, id)
                    onDone(true)
                }
                .onFailure {
                    _actionError.value = it.message
                    onDone(false)
                }
        }
    }

    fun pullFromServer(
        kind: SyncBackupKind,
        id: String,
        onDone: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            _detailLoading.value = true
            _actionError.value = null
            val result =
                when (kind) {
                    SyncBackupKind.NOTE -> notesRepo.pullFromServer(id)
                    SyncBackupKind.CHECKLIST -> checklistsRepo.pullFromServer(id)
                }
            _detailLoading.value = false
            result
                .onSuccess {
                    loadDetail(kind, id)
                    onDone(true)
                }
                .onFailure {
                    _actionError.value = it.message
                    onDone(false)
                }
        }
    }

    fun restoreBackup(
        kind: SyncBackupKind,
        itemId: String,
        backupId: Long,
        onDone: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            _detailLoading.value = true
            _actionError.value = null
            val result =
                when (kind) {
                    SyncBackupKind.NOTE -> notesRepo.restoreSyncBackup(backupId)
                    SyncBackupKind.CHECKLIST -> checklistsRepo.restoreSyncBackup(backupId)
                }
            _detailLoading.value = false
            result
                .onSuccess {
                    loadDetail(kind, itemId)
                    onDone(true)
                }
                .onFailure {
                    _actionError.value = it.message
                    onDone(false)
                }
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun noteDiffTexts(): Pair<String, String>? {
        val local = _localNote.value ?: return null
        val server = _serverNote.value
        val old = server?.let { SyncPayloadCodec.noteDiffText(it) } ?: ""
        val new = SyncPayloadCodec.noteDiffText(local)
        return old to new
    }

    fun checklistDiffTexts(): Pair<String, String>? {
        val local = _localChecklist.value ?: return null
        val server = _serverChecklist.value
        val old = server?.let { SyncPayloadCodec.checklistDiffText(it) } ?: ""
        val new = SyncPayloadCodec.checklistDiffText(local)
        return old to new
    }

    override fun onCleared() {
        notesRepo.close()
        checklistsRepo.close()
    }

    class Factory(
        private val application: Application,
        private val instanceId: String,
        private val api: JottyApi,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PendingSyncViewModel(application, instanceId, api) as T
    }
}
