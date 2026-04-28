package com.jotty.android.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jotty.android.data.api.API_CATEGORY_UNCATEGORIZED
import com.jotty.android.data.api.ApiResponse
import com.jotty.android.data.api.Note
import com.jotty.android.data.api.UpdateNoteRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
        database = Room.inMemoryDatabaseBuilder(context, JottyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun syncNotes_whenOffline_returnsFailure() = runTest {
        val api = FakeJottyApi()
        val repo = OfflineNotesRepository(
            context = context,
            database = database,
            instanceId = instanceId,
            api = api,
            initialOnlineOverride = false,
            registerNetworkCallback = false,
        )
        val result = repo.syncNotes()
        assertTrue(result.isFailure)
    }

    @Test
    fun syncNotes_withNoDirtyNotes_replacesWithServerList() = runTest {
        val remote = listOf(
            Note("a", "A", API_CATEGORY_UNCATEGORIZED, "a", "c1", "u1"),
            Note("b", "B", API_CATEGORY_UNCATEGORIZED, "b", "c2", "u2"),
        )
        val api = FakeJottyApi(notesFromGet = remote)
        val repo = OfflineNotesRepository(
            context = context,
            database = database,
            instanceId = instanceId,
            api = api,
            initialOnlineOverride = true,
            registerNetworkCallback = false,
        )
        assertTrue(repo.syncNotes().isSuccess)
        val stored = database.noteDao().getAllNotes(instanceId)
        assertEquals(2, stored.size)
        assertEquals(setOf("a", "b"), stored.map { it.id }.toSet())
    }

    @Test
    fun syncNotes_whenDirtyNoteDiffersFromServer_createsLocalCopy() = runTest {
        val serverNote = Note(
            id = "n1",
            title = "ServerTitle",
            category = API_CATEGORY_UNCATEGORIZED,
            content = "server-body",
            createdAt = "c",
            updatedAt = "u",
        )
        val api = FakeJottyApi(
            notesFromGet = listOf(serverNote),
            updateNoteHandler = { noteId, body ->
                ApiResponse(
                    true,
                    Note(
                        id = noteId,
                        title = body.title,
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
        val repo = OfflineNotesRepository(
            context = context,
            database = database,
            instanceId = instanceId,
            api = api,
            initialOnlineOverride = true,
            registerNetworkCallback = false,
        )
        assertTrue(repo.syncNotes().isSuccess)
        val stored = database.noteDao().getAllNotes(instanceId)
        assertEquals(2, stored.size)
        assertTrue(stored.any { it.title == "LocalTitle (Local copy)" && it.content == "local-body" })
        assertTrue(stored.any { it.title == "ServerTitle" && it.content == "server-body" })
    }

    @Test
    fun getConflictCopiesFlow_returnsOnlyLocalCopies() = runTest {
        val repo = OfflineNotesRepository(
            context = context,
            database = database,
            instanceId = instanceId,
            api = FakeJottyApi(),
            initialOnlineOverride = true,
            registerNetworkCallback = false,
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
    fun clearLocalNotes_deletesOnlyRequestedInstance() = runTest {
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

        OfflineNotesRepository.clearLocalNotes(context, instanceId)

        assertTrue(database.noteDao().getAllNotes(instanceId).isEmpty())
        assertEquals(1, database.noteDao().getAllNotes(otherInstanceId).size)
    }
}
