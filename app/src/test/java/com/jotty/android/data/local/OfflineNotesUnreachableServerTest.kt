package com.jotty.android.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jotty.android.data.api.API_CATEGORY_UNCATEGORIZED
import com.jotty.android.data.api.ApiResponse
import com.jotty.android.data.api.Note
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response
import java.net.UnknownHostException

/**
 * The device reports a network but the Jotty host cannot be reached (LAN-only DNS, #115):
 * note saves must be kept locally and reported as saved, then synced once the server answers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineNotesUnreachableServerTest {
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

    private fun repo(api: FakeJottyApi) =
        OfflineNotesRepository(
            context = context,
            database = database,
            instanceId = instanceId,
            api = api,
            initialOnlineOverride = true,
            useSharedConnectivity = false,
        )

    private suspend fun insertSyncedNote(id: String = "n1") {
        database.noteDao().insertNote(
            NoteEntity(
                id = id,
                title = "Title",
                category = API_CATEGORY_UNCATEGORIZED,
                content = "server-body",
                createdAt = "2020-01-01T00:00:00Z",
                updatedAt = "2024-06-01T00:00:00Z",
                encrypted = null,
                isDirty = false,
                isDeleted = false,
                instanceId = instanceId,
                isLocalOnly = false,
            ),
        )
    }

    @Test
    fun updateNote_whenServerUnreachable_savesLocallyAndSyncsLater() =
        runTest {
            var unreachable = true
            val api = FakeJottyApi()
            api.updateNoteHandler = { id, body ->
                if (unreachable) throw UnknownHostException("jotty.internal")
                val pushed =
                    Note(
                        id = id,
                        title = body.title.orEmpty(),
                        category = body.category ?: API_CATEGORY_UNCATEGORIZED,
                        content = body.content.orEmpty(),
                        createdAt = "2020-01-01T00:00:00Z",
                        updatedAt = "2024-06-02T00:00:00Z",
                    )
                api.notesFromGet = listOf(pushed)
                ApiResponse(true, pushed)
            }
            insertSyncedNote()
            val repo = repo(api)

            val result = repo.updateNote("n1", "Title", "edited away from home", API_CATEGORY_UNCATEGORIZED)

            assertTrue("an unreachable server must not fail the save", result.isSuccess)
            assertEquals("edited away from home", result.getOrThrow().content)
            val kept = requireNotNull(database.noteDao().getNoteById("n1"))
            assertTrue(kept.isDirty)
            assertEquals("edited away from home", kept.content)

            unreachable = false
            assertTrue(repo.syncNotes().isSuccess)

            val synced = requireNotNull(database.noteDao().getNoteById("n1"))
            assertFalse(synced.isDirty)
            assertEquals("edited away from home", synced.content)
            repo.close()
        }

    @Test
    fun updateNote_whenServerAnswersWithError_stillFails() =
        runTest {
            val api =
                FakeJottyApi(
                    updateNoteHandler = { _, _ ->
                        val body = "".toResponseBody("application/json".toMediaTypeOrNull())
                        throw HttpException(Response.error<Any>(500, body))
                    },
                )
            insertSyncedNote()
            val repo = repo(api)

            val result = repo.updateNote("n1", "Title", "rejected edit", API_CATEGORY_UNCATEGORIZED)

            assertTrue(result.isFailure)
            // The edit is still kept for a later retry.
            assertTrue(requireNotNull(database.noteDao().getNoteById("n1")).isDirty)
            repo.close()
        }

    @Test
    fun createNote_whenServerUnreachable_keepsLocalOnlyNote() =
        runTest {
            val api =
                FakeJottyApi(
                    createNoteResponse = { throw UnknownHostException("jotty.internal") },
                )
            val repo = repo(api)

            val result = repo.createNote("Untitled", "", API_CATEGORY_UNCATEGORIZED)

            assertTrue(result.isSuccess)
            val stored = requireNotNull(database.noteDao().getNoteById(result.getOrThrow().id))
            assertTrue(stored.isLocalOnly)
            assertTrue(stored.isDirty)
            repo.close()
        }

    @Test
    fun deleteNote_whenServerUnreachable_staysQueued() =
        runTest {
            val api =
                FakeJottyApi(
                    deleteNoteResult = { throw UnknownHostException("jotty.internal") },
                )
            insertSyncedNote()
            val repo = repo(api)

            assertTrue(repo.deleteNote("n1").isSuccess)

            val marked = requireNotNull(database.noteDao().getNoteByIdIncludingDeleted("n1"))
            assertTrue(marked.isDeleted)
            assertTrue(marked.isDirty)
            repo.close()
        }
}
