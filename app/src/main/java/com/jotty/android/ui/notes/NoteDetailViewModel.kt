package com.jotty.android.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jotty.android.data.api.Note
import com.jotty.android.data.encryption.NoteDecryptionSession
import com.jotty.android.data.encryption.NotePassphraseSession
import com.jotty.android.data.encryption.NoteEncryption
import com.jotty.android.data.encryption.ParsedNoteContent
import com.jotty.android.data.encryption.XChaCha20Decryptor
import com.jotty.android.data.encryption.XChaCha20Encryptor
import com.jotty.android.data.encryption.clearPassphrase
import com.jotty.android.data.encryption.copyTrimmedOrNull
import com.jotty.android.util.AppLog
import com.jotty.android.util.decodeJsonUnicodeEscapes
import com.jotty.android.util.stripInvisibleFromEdges
import com.jotty.android.util.stripInvisibleUnicode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NoteDetailViewModel(
    private val note: Note,
    private val actions: NoteDetailActions,
) : ViewModel() {
    /** Tracks server id after a local-only note syncs; [note.id] may still be the temporary UUID. */
    private var activeNoteId = note.id

    /** Ciphertext fingerprint when the user last decrypted; used to drop stale session plaintext. */
    private var sessionSourceFingerprint: String? = null
    private val _title = MutableStateFlow(stripInvisibleFromEdges(note.title))
    val title: StateFlow<String> = _title.asStateFlow()

    private val _content = MutableStateFlow(stripInvisibleFromEdges(note.content))
    val content: StateFlow<String> = _content.asStateFlow()

    private val _category = MutableStateFlow(note.category)
    val category: StateFlow<String> = _category.asStateFlow()

    // Category currently stored on the server (used as originalCategory on the next save).
    private var persistedCategory = note.category

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

    private val _legacyEncryptionDetected = MutableStateFlow(false)
    val legacyEncryptionDetected: StateFlow<Boolean> = _legacyEncryptionDetected.asStateFlow()

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
        get() {
            val decrypted = _decryptedContent.value?.takeIf { it.isNotBlank() }
            return when {
                isEncrypted && decrypted != null -> decrypted
                isEncrypted -> null
                else -> _content.value
            }
        }

    /** Body to persist on the server — always ciphertext for encrypted notes, never session plaintext. */
    fun contentForServerUpdate(): String = _content.value

    fun resetFromNote(updated: Note) {
        adoptNoteId(updated.id)
        _title.value = stripInvisibleFromEdges(updated.title)
        _content.value = stripInvisibleFromEdges(updated.content)
        _category.value = updated.category
        persistedCategory = updated.category
        _isEditing.value = false
        _saveFailed.value = false
        _legacyEncryptionDetected.value = false
    }

    fun loadSessionDecryptedContent() {
        val cached =
            NoteDecryptionSession.get(activeNoteId)
                ?.let {
                    decodeJsonUnicodeEscapes(
                        stripInvisibleUnicode(stripInvisibleFromEdges(it)),
                    )
                }
                ?.takeIf { it.isNotBlank() }
        _decryptedContent.value = cached
        if (cached == null) {
            NoteDecryptionSession.remove(activeNoteId)
            NotePassphraseSession.remove(activeNoteId)
            sessionSourceFingerprint = null
        } else if (sessionSourceFingerprint == null) {
            sessionSourceFingerprint = contentFingerprint(_content.value)
        }
    }

    fun onNoteSnapshotUpdated(updated: Note) {
        invalidateDecryptedIfServerContentChanged(updated.content)
        resetFromNote(updated)
    }

    fun invalidateDecryptedIfServerContentChanged(serverContent: String) {
        if (_decryptedContent.value.isNullOrBlank()) return
        val fp = contentFingerprint(serverContent)
        if (sessionSourceFingerprint != null && sessionSourceFingerprint != fp) {
            lockNote()
        }
    }

    fun lockNote() {
        _decryptedContent.value = null
        _isEditing.value = false
        _legacyEncryptionDetected.value = false
        sessionSourceFingerprint = null
        NoteDecryptionSession.remove(activeNoteId)
        NotePassphraseSession.remove(activeNoteId)
    }

    fun hasSessionPassphrase(): Boolean = NotePassphraseSession.has(activeNoteId)

    fun encryptWithSessionPassphrase(
        encryptFailedMsg: String,
        onSuccess: (Note) -> Unit,
        onFailure: () -> Unit,
        encryptNoPlaintextMsg: String? = null,
    ) {
        val passChars = NotePassphraseSession.get(activeNoteId)
        if (passChars == null) {
            showEncryptDialog()
            return
        }
        encrypt(passChars, encryptFailedMsg, onSuccess, onFailure, encryptNoPlaintextMsg)
    }

    fun setTitle(value: String) {
        _title.value = value
    }

    fun setContent(value: String) {
        _content.value = value
    }

    /** Updates the in-memory decrypted plaintext while editing an encrypted note. */
    fun setDecryptedContent(value: String) {
        _decryptedContent.value = value
    }

    fun setCategory(value: String) {
        _category.value = value
    }

    fun startEditing() {
        _isEditing.value = true
    }

    fun cancelEditing() {
        _isEditing.value = false
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

    fun onDecrypted(
        plain: String,
        usedLegacyDataOrder: Boolean = false,
        passphrase: CharArray? = null,
    ) {
        val cleaned =
            decodeJsonUnicodeEscapes(
                stripInvisibleUnicode(stripInvisibleFromEdges(plain)),
            )
        if (cleaned.isBlank()) {
            _decryptedContent.value = null
            NoteDecryptionSession.remove(activeNoteId)
            NotePassphraseSession.remove(activeNoteId)
            passphrase?.clearPassphrase()
            dismissDecryptDialog()
            return
        }
        _decryptedContent.value = cleaned
        _legacyEncryptionDetected.value = usedLegacyDataOrder
        sessionSourceFingerprint = contentFingerprint(_content.value)
        NoteDecryptionSession.put(activeNoteId, cleaned)
        passphrase?.copyTrimmedOrNull()?.let { trimmed ->
            NotePassphraseSession.put(activeNoteId, trimmed)
            passphrase.clearPassphrase()
        } ?: passphrase?.clearPassphrase()
        dismissDecryptDialog()
    }

    fun onDecryptError(
        main: String?,
        detail: String?,
    ) {
        _decryptError.value = main
        _decryptErrorDetail.value = detail
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
                    noteId = activeNoteId,
                    title = _title.value,
                    content = _content.value,
                    category = _category.value,
                    originalCategory = persistedCategory,
                )
            if (result.isSuccess) {
                persistedCategory = _category.value
                completePersist(result.getOrThrow(), onSuccess)
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
        encryptNoPlaintextMsg: String? = null,
    ) {
        viewModelScope.launch {
            _encryptError.value = null
            _isEncrypting.value = true
            val plainToEncrypt = resolvePlaintextForEncrypt()
            if (plainToEncrypt == null) {
                _encryptError.value = encryptNoPlaintextMsg ?: encryptFailedMsg
                passChars.clearPassphrase()
                _isEncrypting.value = false
                return@launch
            }
            if (isEncrypted && NoteEncryption.isEncrypted(plainToEncrypt)) {
                AppLog.e(
                    "encryption",
                    "Refusing to re-encrypt ciphertext as plaintext for note $activeNoteId",
                )
                _encryptError.value = encryptNoPlaintextMsg ?: encryptFailedMsg
                passChars.clearPassphrase()
                _isEncrypting.value = false
                return@launch
            }
            try {
                val body =
                    withContext(Dispatchers.Default) {
                        XChaCha20Encryptor.encrypt(plainToEncrypt, passChars)
                    }
                if (body != null) {
                    val fullContent =
                        XChaCha20Encryptor.wrapWithFrontmatter(
                            activeNoteId,
                            _title.value,
                            _category.value,
                            body,
                        )
                    // Safety net (issue #67): never push ciphertext we cannot read back.
                    // Round-trip decrypt the freshly produced body (and the wrapped form) with the
                    // same passphrase before saving; abort if it does not match the plaintext.
                    val roundTripsOk =
                        withContext(Dispatchers.Default) {
                            verifyEncryptRoundTrip(body, fullContent, plainToEncrypt, passChars)
                        }
                    if (!roundTripsOk) {
                        AppLog.e(
                            "encryption",
                            "Aborting save: re-encrypted note $activeNoteId failed local round-trip decrypt",
                        )
                        _encryptError.value = encryptFailedMsg
                        return@launch
                    }
                    val result =
                        actions.updateNote(
                            noteId = activeNoteId,
                            title = _title.value,
                            content = fullContent,
                            category = _category.value,
                            originalCategory = persistedCategory,
                        )
                    if (result.isSuccess) {
                        persistedCategory = _category.value
                        val saved = result.getOrThrow()
                        // Keep the freshly-encrypted plaintext available so the note stays
                        // readable (and editable) without re-decrypting after save.
                        _decryptedContent.value = plainToEncrypt.takeIf { it.isNotBlank() }
                        _legacyEncryptionDetected.value = false
                        if (plainToEncrypt.isNotBlank()) {
                            sessionSourceFingerprint = contentFingerprint(fullContent)
                            NoteDecryptionSession.put(activeNoteId, plainToEncrypt)
                        }
                        NotePassphraseSession.put(activeNoteId, passChars)
                        _isEditing.value = false
                        completePersist(saved, onSuccess)
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

    private fun adoptNoteId(newId: String) {
        if (newId == activeNoteId) return
        NoteDecryptionSession.get(activeNoteId)?.let { plain ->
            NoteDecryptionSession.remove(activeNoteId)
            NoteDecryptionSession.put(newId, plain)
        }
        if (NotePassphraseSession.has(activeNoteId)) {
            NotePassphraseSession.get(activeNoteId)?.let { pass ->
                NotePassphraseSession.remove(activeNoteId)
                NotePassphraseSession.put(newId, pass)
            }
        }
        activeNoteId = newId
    }

    private fun completePersist(
        saved: Note,
        onSuccess: (Note) -> Unit,
    ) {
        adoptNoteId(saved.id)
        _content.value = stripInvisibleFromEdges(saved.content)
        onSuccess(saved)
    }

    fun logEncryptionState() {
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

    internal fun contentFingerprint(content: String): String =
        stripInvisibleFromEdges(content).hashCode().toString()

    /**
     * Decrypts the freshly produced [body] (and the frontmatter-wrapped [fullContent]) with [passChars]
     * and confirms the result matches [expectedPlaintext]. Guards against pushing undecryptable ciphertext
     * (issue #67). Uses passphrase copies so the caller's buffer survives for the session store.
     */
    internal fun verifyEncryptRoundTrip(
        body: String,
        fullContent: String,
        expectedPlaintext: String,
        passChars: CharArray,
    ): Boolean {
        val fromBody = XChaCha20Decryptor.decrypt(body, passChars.copyOf())
        if (fromBody != expectedPlaintext) return false
        val parsed = NoteEncryption.parse(fullContent) as? ParsedNoteContent.Encrypted ?: return false
        val fromWrapped = XChaCha20Decryptor.decrypt(parsed.encryptedBody, passChars.copyOf())
        return fromWrapped == expectedPlaintext
    }

    /**
     * Plaintext to encrypt. Re-encrypting an already encrypted note must use session plaintext only —
     * never [content] (ciphertext), which would produce an undecryptable note with the real passphrase.
     */
    internal fun resolvePlaintextForEncrypt(): String? {
        val raw =
            when {
                isEncrypted -> _decryptedContent.value
                else -> displayContent ?: _content.value
            } ?: return null
        return stripInvisibleUnicode(stripInvisibleFromEdges(decodeJsonUnicodeEscapes(raw)))
    }

    class Factory(
        private val note: Note,
        private val actions: NoteDetailActions,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            NoteDetailViewModel(note, actions) as T
    }
}
