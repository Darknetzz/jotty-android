package com.jotty.android.ui.sync

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jotty.android.R
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.encryption.NoteDecryptionSession
import com.jotty.android.data.encryption.NoteEncryption
import com.jotty.android.data.encryption.ParsedNoteContent
import com.jotty.android.data.local.NoteEntity
import com.jotty.android.data.local.ChecklistSyncPayload
import com.jotty.android.data.local.JottyDatabase
import com.jotty.android.data.local.NoteSyncPayload
import com.jotty.android.data.local.OfflineChecklistsRepository
import com.jotty.android.data.local.OfflineNotesRepository
import com.jotty.android.data.local.PendingSyncItem
import com.jotty.android.data.local.SyncBackup
import com.jotty.android.data.local.SyncBackupKind
import com.jotty.android.data.local.SyncPayloadCodec
import com.jotty.android.data.local.toSyncPayload
import com.jotty.android.data.local.withoutLocalCopySuffix
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

    val conflictCopies: StateFlow<List<ConflictCopyItem>> =
        combine(
            notesRepo.getConflictCopiesFlow(),
            checklistsRepo.getConflictCopiesFlow(),
        ) { notes, checklists ->
            notes.map { ConflictCopyItem(it.id, SyncBackupKind.NOTE, it.title, it.updatedAt) } +
                checklists.map { ConflictCopyItem(it.id, SyncBackupKind.CHECKLIST, it.title, it.updatedAt) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _conflict = MutableStateFlow<ConflictDetail?>(null)
    val conflict: StateFlow<ConflictDetail?> = _conflict

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

    fun loadConflict(
        kind: SyncBackupKind,
        copyId: String,
    ) {
        viewModelScope.launch {
            _detailLoading.value = true
            _actionError.value = null
            try {
                _conflict.value =
                    when (kind) {
                        SyncBackupKind.NOTE -> notesRepo.getConflict(copyId)?.let { noteConflictDetail(it.copy, it.original) }
                        SyncBackupKind.CHECKLIST ->
                            checklistsRepo.getConflict(copyId)?.let { conflict ->
                                val mine = conflict.copy.toSyncPayload().copy(title = conflict.copy.title.withoutLocalCopySuffix())
                                ConflictDetail(
                                    kind = kind,
                                    copyId = copyId,
                                    title = mine.title,
                                    copyUpdatedAt = conflict.copy.updatedAt,
                                    hasOriginal = conflict.original != null,
                                    serverText = conflict.original?.let { SyncPayloadCodec.checklistDiffText(it.toSyncPayload()) },
                                    mineText = SyncPayloadCodec.checklistDiffText(mine),
                                )
                            }
                    }
            } finally {
                _detailLoading.value = false
            }
        }
    }

    private fun noteConflictDetail(
        copy: NoteEntity,
        original: NoteEntity?,
    ): ConflictDetail {
        val mine = copy.copy(title = copy.title.withoutLocalCopySuffix())
        val encrypted = isEncrypted(mine) || (original != null && isEncrypted(original))
        return ConflictDetail(
            kind = SyncBackupKind.NOTE,
            copyId = copy.id,
            title = mine.title,
            copyUpdatedAt = copy.updatedAt,
            hasOriginal = original != null,
            serverText = original?.let { noteDisplayText(it) },
            mineText = noteDisplayText(mine),
            mineNote = mine.takeUnless { encrypted }?.let { NoteVersion(it.title, it.content, it.category) },
            serverNote = original?.takeUnless { encrypted }?.let { NoteVersion(it.title, it.content, it.category) },
            encrypted = encrypted,
        )
    }

    private fun isEncrypted(note: NoteEntity): Boolean =
        note.encrypted == true || NoteEncryption.parse(note.content) is ParsedNoteContent.Encrypted

    /** Diff text for a note; encrypted bodies show decrypted text only when unlocked this session. */
    private fun noteDisplayText(note: NoteEntity): String {
        val payload = note.toSyncPayload()
        if (!isEncrypted(note)) return SyncPayloadCodec.noteDiffText(payload)
        val body =
            NoteDecryptionSession.get(note.id)
                ?: getApplication<Application>().getString(R.string.pending_sync_encrypted_body)
        return SyncPayloadCodec.noteDiffText(payload.copy(content = body))
    }

    fun resolveConflict(
        kind: SyncBackupKind,
        copyId: String,
        resolution: ConflictResolution,
        onDone: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            _detailLoading.value = true
            _actionError.value = null
            val result: Result<Any?> =
                when (kind) {
                    SyncBackupKind.NOTE ->
                        when (resolution) {
                            ConflictResolution.KeepServer -> notesRepo.resolveConflictKeepServer(copyId)
                            ConflictResolution.KeepBoth -> notesRepo.resolveConflictKeepBoth(copyId)
                            ConflictResolution.KeepMine -> {
                                val conflict = notesRepo.getConflict(copyId)
                                if (conflict == null) {
                                    Result.failure(IllegalStateException(getApplication<Application>().getString(R.string.conflict_gone)))
                                } else {
                                    notesRepo.resolveConflictKeepMine(
                                        copyId = copyId,
                                        title = conflict.copy.title.withoutLocalCopySuffix(),
                                        content = conflict.copy.content,
                                        category = conflict.copy.category,
                                    )
                                }
                            }
                            is ConflictResolution.Merged ->
                                notesRepo.resolveConflictKeepMine(
                                    copyId = copyId,
                                    title = resolution.version.title,
                                    content = resolution.version.content,
                                    category = resolution.version.category,
                                )
                        }
                    SyncBackupKind.CHECKLIST ->
                        when (resolution) {
                            ConflictResolution.KeepServer -> checklistsRepo.resolveConflictKeepServer(copyId)
                            ConflictResolution.KeepBoth -> checklistsRepo.resolveConflictKeepBoth(copyId)
                            ConflictResolution.KeepMine -> checklistsRepo.resolveConflictKeepMine(copyId)
                            is ConflictResolution.Merged ->
                                Result.failure(IllegalArgumentException("Checklists have no merged edit"))
                        }
                }
            _detailLoading.value = false
            result
                .onSuccess {
                    _conflict.value = null
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

/** One unresolved conflict copy, for the Conflicts list. */
data class ConflictCopyItem(
    val id: String,
    val kind: SyncBackupKind,
    val title: String,
    val updatedAt: String,
)

/** Title, body and category of one side of a note conflict (only for unencrypted notes). */
data class NoteVersion(
    val title: String,
    val content: String,
    val category: String,
)

/** What the merge screen shows for one conflict copy. */
data class ConflictDetail(
    val kind: SyncBackupKind,
    val copyId: String,
    /** Copy title without the "(Local copy)" suffix. */
    val title: String,
    val copyUpdatedAt: String,
    /** False when the row the copy was split from is gone or unknown: only keep both / discard apply. */
    val hasOriginal: Boolean,
    val serverText: String?,
    val mineText: String,
    val mineNote: NoteVersion? = null,
    val serverNote: NoteVersion? = null,
    val encrypted: Boolean = false,
)

sealed interface ConflictResolution {
    data object KeepServer : ConflictResolution

    data object KeepMine : ConflictResolution

    data object KeepBoth : ConflictResolution

    /** A note edited in the merge screen; replaces the original like [KeepMine]. */
    data class Merged(
        val version: NoteVersion,
    ) : ConflictResolution
}
