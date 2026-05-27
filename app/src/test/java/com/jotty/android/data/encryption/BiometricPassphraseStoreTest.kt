package com.jotty.android.data.encryption

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BiometricPassphraseStoreTest {
    private val ciphertextKeyPrefix = "ct_"

    @Test
    fun noteIdFromIvKey_parsesNoteId() {
        val noteId = "note-fixture-1"
        assertEquals(noteId, BiometricPassphraseStore.noteIdFromIvKey("${BiometricPassphraseStore.IV_KEY_PREFIX}$noteId"))
    }

    @Test
    fun noteIdFromIvKey_returnsNullForNonIvKeys() {
        assertNull(BiometricPassphraseStore.noteIdFromIvKey("${ciphertextKeyPrefix}note-fixture-1"))
        assertNull(BiometricPassphraseStore.noteIdFromIvKey("${BiometricPassphraseStore.IV_KEY_PREFIX}"))
        assertNull(BiometricPassphraseStore.noteIdFromIvKey("not-a-prefs-key"))
    }
}
