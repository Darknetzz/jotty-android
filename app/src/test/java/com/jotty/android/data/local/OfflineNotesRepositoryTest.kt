package com.jotty.android.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jotty.android.data.api.API_CATEGORY_UNCATEGORIZED
import com.jotty.android.data.api.ApiResponse
import com.jotty.android.data.api.Note
import com.jotty.android.data.api.SuccessResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
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
class OfflineNotesRepositoryTest {
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
    fun syncNotes_whenOffline_returnsFailure() =
        runTest {
            val api = FakeJottyApi()
            val repo =
                OfflineNotesRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = api,
                    initialOnlineOverride = false,
                    useSharedConnectivity = false,
                )
            val result = repo.syncNotes()
            assertTrue(result.isFailure)
        }

    @Test
    fun syncNotes_withNoDirtyNotes_replacesWithServerList() =
        runTest {
            val remote =
                listOf(
                    Note("a", "A", API_CATEGORY_UNCATEGORIZED, "a", "c1", "u1"),
                    Note("b", "B", API_CATEGORY_UNCATEGORIZED, "b", "c2", "u2"),
                )
            val api = FakeJottyApi(notesFromGet = remote)
            val repo =
                OfflineNotesRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = api,
                    initialOnlineOverride = true,
                    useSharedConnectivity = false,
                )
            assertTrue(repo.syncNotes().isSuccess)
            val stored = database.noteDao().getAllNotes(instanceId)
            assertEquals(2, stored.size)
            assertEquals(setOf("a", "b"), stored.map { it.id }.toSet())
        }

    @Test
    fun syncNotes_whenDirtyNoteDiffersFromServer_createsLocalCopy() =
        runTest {
            val serverNote =
                Note(
                    id = "n1",
                    title = "ServerTitle",
                    category = API_CATEGORY_UNCATEGORIZED,
                    content = "server-body",
                    createdAt = "c",
                    updatedAt = "u",
                )
            val api =
                FakeJottyApi(
                    notesFromGet = listOf(serverNote),
                    updateNoteHandler = { noteId, body ->
                        ApiResponse(
                            true,
                            Note(
                                id = noteId,
                                title = body.title.orEmpty(),
                                category = body.category ?: API_CATEGORY_UNCATEGORIZED,
                                content = body.content.orEmpty(),
                                createdAt = "c",
                                updatedAt = "u",
                            ),
                        )
                    },
                )
            database.noteDao().insertNote(
                NoteEntity(
                    id = "n1",
                    title = "LocalTitle",
                    category = API_CATEGORY_UNCATEGORIZED,
                    content = "local-body",
                    createdAt = "2020-01-01T00:00:00Z",
                    updatedAt = "2024-06-01T00:00:00Z",
                    encrypted = null,
                    isDirty = true,
                    isDeleted = false,
                    instanceId = instanceId,
                ),
            )
            val repo =
                OfflineNotesRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = api,
                    initialOnlineOverride = true,
                    useSharedConnectivity = false,
                )
            assertTrue(repo.syncNotes().isSuccess)
            val stored = database.noteDao().getAllNotes(instanceId)
            assertEquals(2, stored.size)
            assertTrue(stored.any { it.title == "LocalTitle (Local copy)" && it.content == "local-body" })
            assertTrue(stored.any { it.title == "ServerTitle" && it.content == "server-body" })
        }

    @Test
    fun getConflictCopiesFlow_returnsOnlyLocalCopies() =
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
            database.noteDao().insertNotes(
                listOf(
                    NoteEntity(
                        id = "regular",
                        title = "Regular",
                        category = API_CATEGORY_UNCATEGORIZED,
                        content = "body",
                        createdAt = "c",
                        updatedAt = "u",
                        encrypted = null,
                        instanceId = instanceId,
                    ),
                    NoteEntity(
                        id = "copy",
                        title = "Regular${OfflineNotesRepository.LOCAL_COPY_SUFFIX}",
                        category = API_CATEGORY_UNCATEGORIZED,
                        content = "local body",
                        createdAt = "c",
                        updatedAt = "u",
                        encrypted = null,
                        instanceId = instanceId,
                    ),
                ),
            )

            val conflictCopies = repo.getConflictCopiesFlow().first()

            assertEquals(1, conflictCopies.size)
            assertEquals("copy", conflictCopies.single().id)
        }

    @Test
    fun syncNote_whenIsLocalOnly_callsCreateNoteNotUpdateNote() =
        runTest {
            var createCalled = false
            var updateCalled = false
            val api =
                FakeJottyApi(
                    notesFromGet = emptyList(),
                    createNoteResponse = { req ->
                        createCalled = true
                        ApiResponse(
                            true,
                            Note("server-id", req.title, req.category ?: API_CATEGORY_UNCATEGORIZED, req.content.orEmpty(), "c", "u"),
                        )
                    },
                    updateNoteHandler = { _, _ ->
                        updateCalled = true
                        error("should not be called for a local-only note")
                    },
                )
            val repo =
                OfflineNotesRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = api,
                    initialOnlineOverride = true,
                    useSharedConnectivity = false,
                )

            val result = repo.createNote(title = "Offline note", content = "body")

            assertTrue(result.isSuccess)
            assertEquals("server-id", result.getOrThrow().id)
            assertTrue("createNote should have been called", createCalled)
            assertFalse("updateNote must not be called for a local-only note", updateCalled)
            // The temporary local ID must have been replaced by the server-assigned ID.
            val stored = database.noteDao().getAllNotes(instanceId)
            assertEquals(1, stored.size)
            assertEquals("server-id", stored.single().id)
            assertFalse("isLocalOnly must be cleared after successful server sync", stored.single().isLocalOnly)
        }

    @Test
    fun updateNote_afterLocalOnlyNoteSynced_usesServerIdWithStaleSelectionId() =
        runTest {
            val localId = "local-note-1"
            database.noteDao().insertNote(
                NoteEntity(
                    id = localId,
                    title = "My notes",
                    category = API_CATEGORY_UNCATEGORIZED,
                    content = "draft",
                    createdAt = "c",
                    updatedAt = "u",
                    encrypted = null,
                    isDirty = true,
                    isDeleted = false,
                    instanceId = instanceId,
                    isLocalOnly = true,
                ),
            )
            val remoteNotes = mutableListOf<Note>()
            val api =
                FakeJottyApi(
                    notesFromGet = remoteNotes,
                    createNoteResponse = { req ->
                        val created =
                            Note(
                                "server-id",
                                req.title,
                                req.category ?: API_CATEGORY_UNCATEGORIZED,
                                req.content.orEmpty(),
                                "c",
                                "u",
                            )
                        remoteNotes.add(created)
                        ApiResponse(true, created)
                    },
                )
            val repo =
                OfflineNotesRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = api,
                    initialOnlineOverride = true,
                    useSharedConnectivity = false,
                )
            assertTrue(repo.syncNotes().isSuccess)
            assertEquals("server-id", repo.remappedNoteId(localId))

            val updateResult =
                repo.updateNote(
                    localId,
                    "My notes",
                    "# My notes\n\nedited",
                    API_CATEGORY_UNCATEGORIZED,
                )
            assertTrue(updateResult.isSuccess)
            assertEquals("server-id", updateResult.getOrThrow().id)
            assertEquals(
                "# My notes\n\nedited",
                database.noteDao().getNoteById("server-id")?.content,
            )
        }

    @Test
    fun syncNote_whenNotLocalOnly_callsUpdateNoteNotCreateNote() =
        runTest {
            var createCalled = false
            var updateCalled = false
            val api =
                FakeJottyApi(
                    notesFromGet = emptyList(),
                    createNoteResponse = { _ ->
                        createCalled = true
                        error("should not be called for an existing note")
                    },
                    updateNoteHandler = { noteId, req ->
                        updateCalled = true
                        ApiResponse(
                            true,
                            Note(noteId, req.title.orEmpty(), req.category ?: API_CATEGORY_UNCATEGORIZED, req.content.orEmpty(), "c", "u"),
                        )
                    },
                )
            // Pre-insert an existing (server-synced) note — isLocalOnly defaults to false.
            database.noteDao().insertNote(
                NoteEntity(
                    id = "existing-server-id",
                    title = "Old Title",
                    category = API_CATEGORY_UNCATEGORIZED,
                    content = "old body",
                    createdAt = "2024-01-01T00:00:00Z",
                    updatedAt = "2024-01-01T00:00:00Z",
                    encrypted = null,
                    isDirty = false,
                    isDeleted = false,
                    instanceId = instanceId,
                    isLocalOnly = false,
                ),
            )
            val repo =
                OfflineNotesRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = api,
                    initialOnlineOverride = true,
                    useSharedConnectivity = false,
                )

            val result = repo.updateNote("existing-server-id", "New Title", "new body", API_CATEGORY_UNCATEGORIZED)

            assertTrue(result.isSuccess)
            assertFalse("createNote must not be called for an existing note", createCalled)
            assertTrue("updateNote should have been called", updateCalled)
        }

    @Test
    fun updateNote_encryptedNote_rejectsPlaintextContent() =
        runTest {
            val encryptedBody =
                "---\nencrypted: true\nencryptionMethod: xchacha\n---\n{\"salt\":\"x\"}"
            database.noteDao().insertNote(
                NoteEntity(
                    id = "encrypted-note-id",
                    title = "Secret",
                    category = API_CATEGORY_UNCATEGORIZED,
                    content = encryptedBody,
                    createdAt = "2024-01-01T00:00:00Z",
                    updatedAt = "2024-01-01T00:00:00Z",
                    encrypted = true,
                    isDirty = false,
                    isDeleted = false,
                    instanceId = instanceId,
                    isLocalOnly = false,
                ),
            )
            val repo =
                OfflineNotesRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = FakeJottyApi(),
                    initialOnlineOverride = false,
                    useSharedConnectivity = false,
                )

            val result =
                repo.updateNote(
                    "encrypted-note-id",
                    "Secret",
                    "leaked plaintext from session",
                    API_CATEGORY_UNCATEGORIZED,
                )

            assertTrue(result.isFailure)
            val stored = database.noteDao().getNoteById("encrypted-note-id")
            assertEquals(encryptedBody, stored?.content)
        }

    @Test
    fun syncNote_createOfflineThenEditOfflineThenSync_callsCreateNotUpdateNote() =
        runTest {
            // Exact reproduction of the original bug:
            // 1. createNote() offline  → isLocalOnly = true, createdAt == updatedAt
            // 2. updateNote() offline  → updatedAt changes, but isLocalOnly stays true
            // 3. syncNotes() online    → must call createNote, NOT updateNote
            var createCalled = false
            var updateCalled = false
            val api =
                FakeJottyApi(
                    notesFromGet = emptyList(),
                    createNoteResponse = { req ->
                        createCalled = true
                        ApiResponse(
                            true,
                            Note("server-id", req.title, req.category ?: API_CATEGORY_UNCATEGORIZED, req.content.orEmpty(), "c", "u"),
                        )
                    },
                    updateNoteHandler = { _, _ ->
                        updateCalled = true
                        error("must not be called — note was never on the server")
                    },
                )
            // Start offline so neither createNote nor syncNote hits the server yet.
            val repo =
                OfflineNotesRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = api,
                    initialOnlineOverride = false,
                    useSharedConnectivity = false,
                )

            val createResult = repo.createNote(title = "Draft", content = "v1")
            assertTrue(createResult.isSuccess)
            val localId = createResult.getOrThrow().id

            val editResult = repo.updateNote(localId, "Draft", "v2 edited offline", API_CATEGORY_UNCATEGORIZED)
            assertTrue(editResult.isSuccess)

            // Verify the note is still flagged local-only after the edit.
            val entityAfterEdit = database.noteDao().getNoteById(localId)
            assertTrue("isLocalOnly must survive offline edit", entityAfterEdit!!.isLocalOnly)

            // Simulate going online: create a new repo pointing at the same DB and API with online=true.
            val onlineRepo =
                OfflineNotesRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = api,
                    initialOnlineOverride = true,
                    useSharedConnectivity = false,
                )
            val syncResult = onlineRepo.syncNotes()
            assertTrue(syncResult.isSuccess)
            assertTrue("createNote must be called when syncing a local-only note", createCalled)
            assertFalse("updateNote must not be called for a note never seen by the server", updateCalled)
        }

    @Test
    fun deleteNote_whenLocalOnly_hardDeletesWithoutServerCall() =
        runTest {
            var deleteCalled = false
            val api =
                FakeJottyApi(
                    deleteNoteResult = { _ ->
                        deleteCalled = true
                        SuccessResponse(true)
                    },
                )
            val repo =
                OfflineNotesRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = api,
                    initialOnlineOverride = true,
                    useSharedConnectivity = false,
                )

            val createResult = repo.createNote(title = "Local only", content = "")
            assertTrue(createResult.isSuccess)
            val localId = createResult.getOrThrow().id

            // Pause sync so createNote stays pending (online but note not synced yet).
            // Re-use a fresh repo with isOnline=false to isolate the delete path.
            val offlineRepo =
                OfflineNotesRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = api,
                    initialOnlineOverride = false,
                    useSharedConnectivity = false,
                )
            val deleteResult = offlineRepo.deleteNote(localId)

            assertTrue(deleteResult.isSuccess)
            assertFalse("server deleteNote must not be called for a local-only note", deleteCalled)
            assertNull("note must be hard-deleted from DB", database.noteDao().getNoteById(localId))
        }

    @Test
    fun syncNotes_whenPushFails_doesNotWipeLocalNotesWithServerSnapshot() =
        runTest {
            val serverOnly =
                Note(
                    id = "server-only",
                    title = "Server",
                    category = API_CATEGORY_UNCATEGORIZED,
                    content = "server-body",
                    createdAt = "c",
                    updatedAt = "u",
                )
            val api =
                FakeJottyApi(
                    notesFromGet = listOf(serverOnly),
                    updateNoteHandler = { _, _ ->
                        throw RuntimeException("simulated push failure")
                    },
                )
            database.noteDao().insertNote(
                NoteEntity(
                    id = "n1",
                    title = "LocalTitle",
                    category = API_CATEGORY_UNCATEGORIZED,
                    content = "local-body",
                    createdAt = "2020-01-01T00:00:00Z",
                    updatedAt = "2024-06-01T00:00:00Z",
                    encrypted = null,
                    isDirty = true,
                    isDeleted = false,
                    instanceId = instanceId,
                    isLocalOnly = false,
                ),
            )
            val repo =
                OfflineNotesRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = api,
                    initialOnlineOverride = true,
                    useSharedConnectivity = false,
                )

            assertTrue(repo.syncNotes().isFailure)

            val stored = database.noteDao().getAllNotes(instanceId)
            assertEquals(1, stored.size)
            val row = stored.single()
            assertEquals("n1", row.id)
            assertTrue(row.isDirty)
            assertEquals("LocalTitle", row.title)
            assertEquals("local-body", row.content)
        }

    @Test
    fun clearLocalNotes_deletesOnlyRequestedInstance() =
        runTest {
            val otherInstanceId = "other-instance"
            database.noteDao().insertNotes(
                listOf(
                    NoteEntity(
                        id = "target",
                        title = "Target",
                        category = API_CATEGORY_UNCATEGORIZED,
                        content = "body",
                        createdAt = "c",
                        updatedAt = "u",
                        encrypted = null,
                        instanceId = instanceId,
                    ),
                    NoteEntity(
                        id = "other",
                        title = "Other",
                        category = API_CATEGORY_UNCATEGORIZED,
                        content = "body",
                        createdAt = "c",
                        updatedAt = "u",
                        encrypted = null,
                        instanceId = otherInstanceId,
                    ),
                ),
            )

            OfflineNotesRepository.clearLocalNotes(context, instanceId, database)

            assertTrue(database.noteDao().getAllNotes(instanceId).isEmpty())
            assertEquals(1, database.noteDao().getAllNotes(otherInstanceId).size)
        }
}
