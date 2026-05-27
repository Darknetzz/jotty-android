package com.jotty.android.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jotty.android.data.api.Note
import com.jotty.android.data.encryption.NoteDecryptionSession
import com.jotty.android.data.encryption.NoteEncryption
import com.jotty.android.data.encryption.ParsedNoteContent
import com.jotty.android.data.encryption.XChaCha20Encryptor
import com.jotty.android.data.encryption.clearPassphrase
import com.jotty.android.util.AppLog
import com.jotty.android.util.stripInvisibleFromEdges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NoteDetailViewModel(
    private val note: Note,
    private val actions: NoteDetailActions,
    private val debugLoggingEnabled: Boolean,
) : ViewModel() {
    private val _title = MutableStateFlow(stripInvisibleFromEdges(note.title))
    val title: StateFlow<String> = _title.asStateFlow()

    private val _content = MutableStateFlow(stripInvisibleFromEdges(note.content))
    val content: StateFlow<String> = _content.asStateFlow()

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _decryptedContent = MutableStateFlow<String?>(null)
    val decryptedContent: StateFlow<String?> = _decryptedContent.asStateFlow()

    private val _showDecryptDialog = MutableStateFlow(false)
    val showDecryptDialog: StateFlow<Boolean> = _showDecryptDialog.asStateFlow()

    private val _showEncryptDialog = MutableStateFlow(false)
    val showEncryptDialog: StateFlow<Boolean> = _showEncryptDialog.asStateFlow()

    private val _isEncrypting = MutableStateFlow(false)
    val isEncrypting: StateFlow<Boolean> = _isEncrypting.asStateFlow()

    private val _encryptError = MutableStateFlow<String?>(null)
    val encryptError: StateFlow<String?> = _encryptError.asStateFlow()

    private val _decryptError = MutableStateFlow<String?>(null)
    val decryptError: StateFlow<String?> = _decryptError.asStateFlow()

    private val _decryptErrorDetail = MutableStateFlow<String?>(null)
    val decryptErrorDetail: StateFlow<String?> = _decryptErrorDetail.asStateFlow()

    private val _saveFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    fun consumeSaveFailed(): Boolean {
        val failed = _saveFailed.value
        if (failed) _saveFailed.value = false
        return failed
    }

    val parsed: ParsedNoteContent
        get() = NoteEncryption.parse(_content.value)

    val isEncryptedByContent: Boolean
        get() = parsed is ParsedNoteContent.Encrypted

    val isEncrypted: Boolean
        get() = note.encrypted == true || isEncryptedByContent

    val displayContent: String?
        get() =
            when {
                isEncrypted && _decryptedContent.value != null -> _decryptedContent.value
                isEncrypted -> null
                else -> _content.value
            }

    fun resetFromNote(updated: Note) {
        _title.value = stripInvisibleFromEdges(updated.title)
        _content.value = stripInvisibleFromEdges(updated.content)
        _isEditing.value = false
        _saveFailed.value = false
    }

    fun loadSessionDecryptedContent() {
        _decryptedContent.value =
            NoteDecryptionSession.get(note.id)?.let { stripInvisibleFromEdges(it) }
    }

    fun setTitle(value: String) {
        _title.value = value
    }

    fun setContent(value: String) {
        _content.value = value
    }

    fun startEditing() {
        _isEditing.value = true
    }

    fun showDecryptDialog() {
        _showDecryptDialog.value = true
    }

    fun dismissDecryptDialog() {
        _showDecryptDialog.value = false
        _decryptError.value = null
        _decryptErrorDetail.value = null
    }

    fun showEncryptDialog() {
        _showEncryptDialog.value = true
    }

    fun dismissEncryptDialog() {
        _showEncryptDialog.value = false
        _encryptError.value = null
    }

    fun onDecrypted(plain: String) {
        val cleaned = stripInvisibleFromEdges(plain)
        _decryptedContent.value = cleaned
        NoteDecryptionSession.put(note.id, cleaned)
        dismissDecryptDialog()
    }

    fun onDecryptError(
        main: String?,
        detail: String?,
    ) {
        _decryptError.value = main
        _decryptErrorDetail.value = detail
    }

    fun applyBiometricDecrypted(plain: String) {
        _decryptedContent.value = stripInvisibleFromEdges(plain)
        NoteDecryptionSession.put(note.id, plain)
    }

    fun saveEdit(
        onSuccess: (Note) -> Unit,
        onFailure: () -> Unit,
    ) {
        viewModelScope.launch {
            _saving.value = true
            _saveFailed.value = false
            val result =
                actions.updateNote(
                    noteId = note.id,
                    title = _title.value,
                    content = _content.value,
                    category = note.category,
                )
            if (result.isSuccess) {
                onSuccess(result.getOrThrow())
                _isEditing.value = false
            } else {
                _saveFailed.value = true
                onFailure()
            }
            _saving.value = false
        }
    }

    fun encrypt(
        passChars: CharArray,
        encryptFailedMsg: String,
        onSuccess: (Note) -> Unit,
        onFailure: () -> Unit,
    ) {
        viewModelScope.launch {
            _encryptError.value = null
            _isEncrypting.value = true
            try {
                val body =
                    withContext(Dispatchers.Default) {
                        XChaCha20Encryptor.encrypt(displayContent ?: _content.value, passChars)
                    }
                if (body != null) {
                    val fullContent =
                        XChaCha20Encryptor.wrapWithFrontmatter(
                            note.id,
                            _title.value,
                            note.category,
                            body,
                        )
                    val result =
                        actions.updateNote(
                            noteId = note.id,
                            title = _title.value,
                            content = fullContent,
                            category = note.category,
                        )
                    if (result.isSuccess) {
                        onSuccess(result.getOrThrow())
                        dismissEncryptDialog()
                    } else {
                        _saveFailed.value = true
                        onFailure()
                    }
                } else {
                    _encryptError.value = encryptFailedMsg
                }
            } finally {
                passChars.clearPassphrase()
                _isEncrypting.value = false
            }
        }
    }

    fun logEncryptionStateIfDebug() {
        if (!debugLoggingEnabled) return
        val p = parsed
        if (note.encrypted == true || p is ParsedNoteContent.Encrypted) {
            AppLog.d(
                "encryption",
                "Note detail: note.encrypted=${note.encrypted}, contentLength=${_content.value.length}, " +
                    "parsedAsEncrypted=${p is ParsedNoteContent.Encrypted}, " +
                    "contentStart=${_content.value.take(60).replace("\n", " ")}",
            )
        }
    }

    class Factory(
        private val note: Note,
        private val actions: NoteDetailActions,
        private val debugLoggingEnabled: Boolean,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            NoteDetailViewModel(note, actions, debugLoggingEnabled) as T
    }
}
