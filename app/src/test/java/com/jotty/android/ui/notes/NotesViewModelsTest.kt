package com.jotty.android.ui.notes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jotty.android.data.api.API_CATEGORY_UNCATEGORIZED
import com.jotty.android.data.api.Note
import com.jotty.android.data.encryption.NoteDecryptionSession
import com.jotty.android.data.local.FakeJottyApi
import com.jotty.android.data.local.JottyDatabase
import com.jotty.android.data.local.OfflineNotesRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
    }

    @Test
    fun onDecrypted_ignoresBlankPlaintext() {
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
        assertNull(vm.decryptedContent.value)
        assertNull(NoteDecryptionSession.get(note.id))
    }

    @Test
    fun loadSessionDecryptedContent_ignoresBlankCache() {
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
        NoteDecryptionSession.put(note.id, "  ")
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
        assertNull(vm.decryptedContent.value)
        assertNull(NoteDecryptionSession.get(note.id))
    }
}
