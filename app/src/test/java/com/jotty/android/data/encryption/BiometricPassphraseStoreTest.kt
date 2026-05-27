package com.jotty.android.data.encryption

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BiometricPassphraseStoreTest {
    @Test
    fun noteIdFromIvKey_parsesNoteId() {
        assertEquals("abc-123", BiometricPassphraseStore.noteIdFromIvKey("iv_abc-123"))
    }

    @Test
    fun noteIdFromIvKey_returnsNullForNonIvKeys() {
        assertNull(BiometricPassphraseStore.noteIdFromIvKey("ct_abc"))
        assertNull(BiometricPassphraseStore.noteIdFromIvKey("iv_"))
        assertNull(BiometricPassphraseStore.noteIdFromIvKey("other"))
    }
}
