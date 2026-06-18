package com.jotty.android.ui.notes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jotty.android.data.api.API_CATEGORY_UNCATEGORIZED
import com.jotty.android.data.api.Note
import com.jotty.android.data.encryption.NoteDecryptionSession
import com.jotty.android.data.encryption.NoteEncryption
import com.jotty.android.data.encryption.NotePassphraseSession
import com.jotty.android.data.encryption.ParsedNoteContent
import com.jotty.android.data.encryption.XChaCha20Decryptor
import com.jotty.android.data.encryption.XChaCha20Encryptor
import com.jotty.android.data.encryption.clearPassphrase
import com.jotty.android.data.local.FakeJottyApi
import com.jotty.android.data.local.JottyDatabase
import com.jotty.android.data.local.OfflineNotesRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineEnabledNotesViewModelTest {
    private lateinit var context: Context
    private lateinit var database: JottyDatabase
    private val instanceId = "test-instance"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database =
            Room.inMemoryDatabaseBuilder(context, JottyDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun applyConflictSearchFilter_setsLocalCopyQuery() =
        runTest {
            val repo =
                OfflineNotesRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = FakeJottyApi(),
                    initialOnlineOverride = true,
                    useSharedConnectivity = false,
                )
            val vm = OfflineEnabledNotesViewModel(repo, FakeJottyApi())

            vm.applyConflictSearchFilter()

            assertEquals("(Local copy)", vm.searchQuery.value)
            repo.close()
        }

    @Test
    fun setSearchQuery_updatesSearchQuery() =
        runTest {
            val repo =
                OfflineNotesRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = FakeJottyApi(),
                    initialOnlineOverride = true,
                    useSharedConnectivity = false,
                )
            val vm = OfflineEnabledNotesViewModel(repo, FakeJottyApi())

            vm.setSearchQuery("hello")

            assertEquals("hello", vm.searchQuery.value)
            repo.close()
        }

    @Test
    fun toggleCategoryChip_togglesSelection() =
        runTest {
            val repo =
                OfflineNotesRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = FakeJottyApi(),
                    initialOnlineOverride = true,
                    useSharedConnectivity = false,
                )
            val vm = OfflineEnabledNotesViewModel(repo, FakeJottyApi())

            vm.toggleCategoryChip("Work")
            assertEquals("Work", vm.selectedCategory.value)
            vm.toggleCategoryChip("Work")
            assertEquals(null, vm.selectedCategory.value)
            repo.close()
        }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteDetailViewModelTest {
    @Test
    fun saveEdit_onSuccess_updatesTitleAndExitsEditMode() =
        runTest {
            val note =
                Note(
                    id = "n1",
                    title = "Title",
                    category = API_CATEGORY_UNCATEGORIZED,
                    content = "body",
                    createdAt = "c",
                    updatedAt = "u",
                )
            val actions =
                object : NoteDetailActions {
                    override suspend fun updateNote(
                        noteId: String,
                        title: String,
                        content: String,
                        category: String,
                        originalCategory: String,
                    ): Result<Note> = Result.success(note.copy(title = title, content = content))

                    override suspend fun deleteNote(noteId: String): Result<Unit> = Result.success(Unit)
                }
            val vm = NoteDetailViewModel(note, actions)
            vm.setTitle("Updated")
            vm.startEditing()

            var updated: Note? = null
            vm.saveEdit(onSuccess = { updated = it }, onFailure = {})

            assertEquals("Updated", updated?.title)
            assertEquals(false, vm.isEditing.value)
        }

    @Test
    fun saveEdit_onFailure_keepsEditMode() =
        runTest {
            val note =
                Note(
                    id = "n1",
                    title = "Title",
                    category = API_CATEGORY_UNCATEGORIZED,
                    content = "body",
                    createdAt = "c",
                    updatedAt = "u",
                )
            val actions =
                object : NoteDetailActions {
                    override suspend fun updateNote(
                        noteId: String,
                        title: String,
                        content: String,
                        category: String,
                        originalCategory: String,
                    ): Result<Note> = Result.failure(Exception("fail"))

                    override suspend fun deleteNote(noteId: String): Result<Unit> = Result.success(Unit)
                }
            val vm = NoteDetailViewModel(note, actions)
            vm.startEditing()
            var failed = false
            vm.saveEdit(onSuccess = {}, onFailure = { failed = true })

            assertTrue(failed)
            assertEquals(true, vm.isEditing.value)
        }

    @After
    fun clearDecryptionSession() {
        NoteDecryptionSession.clear()
        NotePassphraseSession.clear()
    }

    @Test
    fun onDecrypted_acceptsEmptyPlaintext() {
        val note =
            Note(
                id = "n-enc",
                title = "Empty",
                category = API_CATEGORY_UNCATEGORIZED,
                content = "---\nencrypted: true\nencryptionMethod: xchacha\n---\n{}",
                createdAt = "c",
                updatedAt = "u",
                encrypted = true,
            )
        val vm =
            NoteDetailViewModel(
                note,
                object : NoteDetailActions {
                    override suspend fun updateNote(
                        noteId: String,
                        title: String,
                        content: String,
                        category: String,
                        originalCategory: String,
                    ): Result<Note> = Result.failure(UnsupportedOperationException())

                    override suspend fun deleteNote(noteId: String): Result<Unit> =
                        Result.failure(UnsupportedOperationException())
                },
            )
        vm.onDecrypted("")
        assertEquals("", vm.decryptedContent.value)
        assertEquals("", vm.displayContent)
        assertEquals("", NoteDecryptionSession.get(note.id))
    }

    @Test
    fun onDecrypted_normalizesWhitespaceOnlyToEmpty() {
        val note =
            Note(
                id = "n-enc",
                title = "Secrets",
                category = API_CATEGORY_UNCATEGORIZED,
                content = "---\nencrypted: true\nencryptionMethod: xchacha\n---\n{}",
                createdAt = "c",
                updatedAt = "u",
                encrypted = true,
            )
        val vm =
            NoteDetailViewModel(
                note,
                object : NoteDetailActions {
                    override suspend fun updateNote(
                        noteId: String,
                        title: String,
                        content: String,
                        category: String,
                        originalCategory: String,
                    ): Result<Note> = Result.failure(UnsupportedOperationException())

                    override suspend fun deleteNote(noteId: String): Result<Unit> =
                        Result.failure(UnsupportedOperationException())
                },
            )
        vm.onDecrypted("   ")
        assertEquals("", vm.decryptedContent.value)
        assertEquals("", NoteDecryptionSession.get(note.id))
    }

    private fun encryptedNoteViewModel(content: String): NoteDetailViewModel {
        val note =
            Note(
                id = "n-enc",
                title = "Secret --- note",
                category = "Work",
                content = content,
                createdAt = "c",
                updatedAt = "u",
                encrypted = true,
            )
        return NoteDetailViewModel(
            note,
            object : NoteDetailActions {
                override suspend fun updateNote(
                    noteId: String,
                    title: String,
                    content: String,
                    category: String,
                    originalCategory: String,
                ): Result<Note> = Result.failure(UnsupportedOperationException())

                override suspend fun deleteNote(noteId: String): Result<Unit> =
                    Result.failure(UnsupportedOperationException())
            },
        )
    }

    @Test
    fun verifyEncryptRoundTrip_acceptsFreshlyEncryptedBodyWithDelimiterTitle() {
        val passphrase = "my secure passphrase 123"
        val plaintext = "Edited body with a title containing --- delimiters"
        val body = XChaCha20Encryptor.encrypt(plaintext, passphrase)!!
        val wrapped = XChaCha20Encryptor.wrapWithFrontmatter("n-enc", "Secret --- note", "Work", body)
        val vm = encryptedNoteViewModel(wrapped)

        assertTrue(
            "freshly encrypted body must round-trip decrypt to the same plaintext",
            vm.verifyEncryptRoundTrip(body, wrapped, plaintext, passphrase.toCharArray()),
        )
        // Sanity: the wrapped form still parses as encrypted and decrypts independently.
        val parsed = NoteEncryption.parse(wrapped)
        assertTrue(parsed is ParsedNoteContent.Encrypted)
        assertEquals(plaintext, XChaCha20Decryptor.decrypt((parsed as ParsedNoteContent.Encrypted).encryptedBody, passphrase))
    }

    @Test
    fun verifyEncryptRoundTrip_rejectsTamperedBody() {
        val passphrase = "my secure passphrase 123"
        val plaintext = "Edited body"
        val body = XChaCha20Encryptor.encrypt(plaintext, passphrase)!!
        val wrapped = XChaCha20Encryptor.wrapWithFrontmatter("n-enc", "Secret", "Work", body)
        val vm = encryptedNoteViewModel(wrapped)

        // Wrong expected plaintext simulates a body that does not match what we intended to save.
        assertFalse(vm.verifyEncryptRoundTrip(body, wrapped, "different plaintext", passphrase.toCharArray()))
    }

    @Test
    fun verifyServerContentDecrypts_acceptsServerReserializedFrontmatter() {
        val passphrase = "my secure passphrase 123"
        val plaintext = "Edited body that survives a server frontmatter rewrite"
        val body = XChaCha20Encryptor.encrypt(plaintext, passphrase)!!
        val vm = encryptedNoteViewModel(XChaCha20Encryptor.wrapWithFrontmatter("n-enc", "Secret", "Work", body))

        // Server stores its own frontmatter but keeps the encrypted body intact.
        val serverContent =
            "---\ntitle: Secret\nuuid: server-uuid\nencrypted: true\nencryptionMethod: xchacha\n---\n$body"

        assertTrue(
            "server copy with re-serialized frontmatter but identical body must verify",
            vm.verifyServerContentDecrypts(serverContent, body, plaintext, passphrase.toCharArray()),
        )
    }

    @Test
    fun verifyServerContentDecrypts_rejectsServerCopyWithCorruptedBody() {
        val passphrase = "my secure passphrase 123"
        val plaintext = "Edited body"
        val body = XChaCha20Encryptor.encrypt(plaintext, passphrase)!!
        val vm = encryptedNoteViewModel(XChaCha20Encryptor.wrapWithFrontmatter("n-enc", "Secret", "Work", body))

        // Simulate the server mangling the encrypted JSON body (e.g. altering the hex data field).
        val corruptedBody = body.replace("\"data\":\"", "\"data\":\"00")
        val serverContent = "---\ntitle: Secret\nencrypted: true\nencryptionMethod: xchacha\n---\n$corruptedBody"

        assertFalse(
            "server copy whose body no longer decrypts must be rejected",
            vm.verifyServerContentDecrypts(serverContent, body, plaintext, passphrase.toCharArray()),
        )
    }

    @Test
    fun verifyServerContentDecrypts_rejectsServerCopyNotRecognizedAsEncrypted() {
        val passphrase = "my secure passphrase 123"
        val plaintext = "Edited body"
        val body = XChaCha20Encryptor.encrypt(plaintext, passphrase)!!
        val vm = encryptedNoteViewModel(XChaCha20Encryptor.wrapWithFrontmatter("n-enc", "Secret", "Work", body))

        // Server returned plaintext (lost the encrypted body entirely).
        assertFalse(
            vm.verifyServerContentDecrypts("just some plain text", body, plaintext, passphrase.toCharArray()),
        )
    }

    @Test
    fun isUnsafePlaintextForEncrypt_detectsJsonEscapedHtmlAndCiphertext() {
        val vm = encryptedNoteViewModel("cipher")
        assertTrue(vm.isUnsafePlaintextForEncrypt("""\u003Ctable\u003E"""))
        assertTrue(vm.isUnsafePlaintextForEncrypt("---\nencrypted: true\n---\n{}"))
        assertFalse(vm.isUnsafePlaintextForEncrypt("<table><td>ok</td></table>"))
    }

    @Test
    fun resolvePlaintextForEncrypt_decodesJsonEscapedHtmlBeforeEncrypt() {
        val vm = encryptedNoteViewModel("cipher")
        vm.onDecrypted("""\u003Ctable\u003E\u003Ctd\u003Ecell\u003C/td\u003E""")
        assertEquals("<table><td>cell</td>", vm.resolvePlaintextForEncrypt())
    }

    @Test
    fun resolvePlaintextForEncrypt_encryptedNote_usesDecryptedNotCiphertext() {
        val ciphertext =
            "---\nencrypted: true\nencryptionMethod: xchacha\n---\n" +
                """{"alg":"xchacha20","salt":"aa","nonce":"bb","data":"cc"}"""
        val note =
            Note(
                id = "n-enc",
                title = "Secrets",
                category = API_CATEGORY_UNCATEGORIZED,
                content = ciphertext,
                createdAt = "c",
                updatedAt = "u",
                encrypted = true,
            )
        val vm =
            NoteDetailViewModel(
                note,
                object : NoteDetailActions {
                    override suspend fun updateNote(
                        noteId: String,
                        title: String,
                        content: String,
                        category: String,
                        originalCategory: String,
                    ): Result<Note> = Result.failure(UnsupportedOperationException())

                    override suspend fun deleteNote(noteId: String): Result<Unit> =
                        Result.failure(UnsupportedOperationException())
                },
            )
        assertNull(vm.resolvePlaintextForEncrypt())
        vm.onDecrypted("Edited body", passphrase = "my-long-passphrase".toCharArray())
        assertEquals("Edited body", vm.resolvePlaintextForEncrypt())
    }

    @Test
    fun onDecrypted_storesPassphraseInSession() {
        val note =
            Note(
                id = "n-enc",
                title = "Secrets",
                category = API_CATEGORY_UNCATEGORIZED,
                content = "---\nencrypted: true\nencryptionMethod: xchacha\n---\n{}",
                createdAt = "c",
                updatedAt = "u",
                encrypted = true,
            )
        val vm =
            NoteDetailViewModel(
                note,
                object : NoteDetailActions {
                    override suspend fun updateNote(
                        noteId: String,
                        title: String,
                        content: String,
                        category: String,
                        originalCategory: String,
                    ): Result<Note> = Result.failure(UnsupportedOperationException())

                    override suspend fun deleteNote(noteId: String): Result<Unit> =
                        Result.failure(UnsupportedOperationException())
                },
            )
        val pass = "my-long-passphrase".toCharArray()
        vm.onDecrypted("Secret body", passphrase = pass)
        assertTrue(vm.hasSessionPassphrase())
        val stored = NotePassphraseSession.get(note.id)
        assertArrayEquals("my-long-passphrase".toCharArray(), stored)
        stored?.clearPassphrase()
    }

    @Test
    fun loadSessionDecryptedContent_restoresEmptyCache() {
        val note =
            Note(
                id = "n-enc",
                title = "Secrets",
                category = API_CATEGORY_UNCATEGORIZED,
                content = "---\nencrypted: true\nencryptionMethod: xchacha\n---\n{}",
                createdAt = "c",
                updatedAt = "u",
                encrypted = true,
            )
        NoteDecryptionSession.put(note.id, "")
        val vm =
            NoteDetailViewModel(
                note,
                object : NoteDetailActions {
                    override suspend fun updateNote(
                        noteId: String,
                        title: String,
                        content: String,
                        category: String,
                        originalCategory: String,
                    ): Result<Note> = Result.failure(UnsupportedOperationException())

                    override suspend fun deleteNote(noteId: String): Result<Unit> =
                        Result.failure(UnsupportedOperationException())
                },
            )
        vm.loadSessionDecryptedContent()
        assertEquals("", vm.decryptedContent.value)
        assertEquals("", NoteDecryptionSession.get(note.id))
    }

    @Test
    fun lockNote_clearsDecryptedSession() {
        val note =
            Note(
                id = "n-enc",
                title = "Secrets",
                category = API_CATEGORY_UNCATEGORIZED,
                content = "---\nencrypted: true\nencryptionMethod: xchacha\n---\n{}",
                createdAt = "c",
                updatedAt = "u",
                encrypted = true,
            )
        val vm =
            NoteDetailViewModel(
                note,
                object : NoteDetailActions {
                    override suspend fun updateNote(
                        noteId: String,
                        title: String,
                        content: String,
                        category: String,
                        originalCategory: String,
                    ): Result<Note> = Result.failure(UnsupportedOperationException())

                    override suspend fun deleteNote(noteId: String): Result<Unit> =
                        Result.failure(UnsupportedOperationException())
                },
            )
        vm.onDecrypted("Secret body")
        assertEquals("Secret body", vm.decryptedContent.value)
        vm.lockNote()
        assertNull(vm.decryptedContent.value)
        assertNull(NoteDecryptionSession.get(note.id))
    }

    @Test
    fun invalidateDecryptedIfServerContentChanged_clearsStaleSession() {
        val note =
            Note(
                id = "n-enc",
                title = "Secrets",
                category = API_CATEGORY_UNCATEGORIZED,
                content = "cipher-a",
                createdAt = "c",
                updatedAt = "u",
                encrypted = true,
            )
        val vm =
            NoteDetailViewModel(
                note,
                object : NoteDetailActions {
                    override suspend fun updateNote(
                        noteId: String,
                        title: String,
                        content: String,
                        category: String,
                        originalCategory: String,
                    ): Result<Note> = Result.failure(UnsupportedOperationException())

                    override suspend fun deleteNote(noteId: String): Result<Unit> =
                        Result.failure(UnsupportedOperationException())
                },
            )
        vm.onDecrypted("Secret body")
        vm.invalidateDecryptedIfServerContentChanged("cipher-b")
        assertNull(vm.decryptedContent.value)
    }
}
