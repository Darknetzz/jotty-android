package com.jotty.android.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.jotty.android.data.api.API_CATEGORY_UNCATEGORIZED
import com.jotty.android.data.api.AddItemRequest
import com.jotty.android.data.api.AdminOverviewResponse
import com.jotty.android.data.api.ApiResponse
import com.jotty.android.data.api.Categories
import com.jotty.android.data.api.CategoriesResponse
import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.ChecklistsResponse
import com.jotty.android.data.api.CreateChecklistRequest
import com.jotty.android.data.api.CreateNoteRequest
import com.jotty.android.data.api.HealthResponse
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import com.jotty.android.data.api.NotesResponse
import com.jotty.android.data.api.SuccessResponse
import com.jotty.android.data.api.SummaryResponse
import com.jotty.android.data.api.UpdateChecklistRequest
import com.jotty.android.data.api.UpdateItemRequest
import com.jotty.android.data.api.UpdateNoteRequest
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
class OfflineChecklistsRepositoryTest {
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
    fun syncChecklists_whenOffline_returnsFailure() = runTest {
        val repo = OfflineChecklistsRepository(
            context = context,
            database = database,
            instanceId = instanceId,
            api = FakeChecklistApi(),
            initialOnlineOverride = false,
            registerNetworkCallback = false,
        )

        val result = repo.syncChecklists()

        assertTrue(result.isFailure)
    }

    @Test
    fun syncChecklists_whenPendingReplayFails_tracksReplayFailureCount() = runTest {
        database.checklistDao().insert(
            ChecklistEntity(
                id = "list-1",
                title = "Local list",
                category = API_CATEGORY_UNCATEGORIZED,
                type = "simple",
                itemsJson = Gson().toJson(listOf(ChecklistItem(index = 0, text = "A"))),
                pendingOpsJson = Gson().toJson(listOf(PendingItemOp(type = "DELETE", path = "99"))),
                createdAt = "c",
                updatedAt = "u",
                isDirty = true,
                isDeleted = false,
                instanceId = instanceId,
                isLocalOnly = false,
            ),
        )

        val api = FakeChecklistApi(
            remoteChecklists = mutableListOf(
                Checklist(
                    id = "list-1",
                    title = "Server list",
                    category = API_CATEGORY_UNCATEGORIZED,
                    type = "simple",
                    items = listOf(ChecklistItem(index = 0, text = "A")),
                    createdAt = "c",
                    updatedAt = "u",
                ),
            ),
            onDeleteItem = { _, _ -> throw IllegalStateException("stale path") },
        )

        val repo = OfflineChecklistsRepository(
            context = context,
            database = database,
            instanceId = instanceId,
            api = api,
            initialOnlineOverride = true,
            registerNetworkCallback = false,
        )

        val result = repo.syncChecklists()

        assertTrue(result.isSuccess)
        assertEquals(1, repo.replayFailuresDetected.value)
    }
}

private class FakeChecklistApi(
    private val remoteChecklists: MutableList<Checklist> = mutableListOf(),
    private val onDeleteItem: suspend (String, String) -> SuccessResponse = { _, _ -> SuccessResponse(true) },
) : JottyApi {
    override suspend fun health(): HealthResponse = HealthResponse("ok", "1", "")

    override suspend fun getChecklists(category: String?, type: String?, search: String?): ChecklistsResponse =
        ChecklistsResponse(remoteChecklists.toList())

    override suspend fun createChecklist(body: CreateChecklistRequest): ApiResponse<Checklist> {
        val created = Checklist(
            id = "server-${remoteChecklists.size + 1}",
            title = body.title,
            category = body.category ?: API_CATEGORY_UNCATEGORIZED,
            type = body.type,
            items = emptyList(),
            createdAt = "c",
            updatedAt = "u",
        )
        remoteChecklists.add(created)
        return ApiResponse(true, created)
    }

    override suspend fun updateChecklist(listId: String, body: UpdateChecklistRequest): ApiResponse<Checklist> {
        val existing = remoteChecklists.first { it.id == listId }
        val updated = existing.copy(
            title = body.title ?: existing.title,
            category = body.category ?: existing.category,
        )
        remoteChecklists.replaceAll { if (it.id == listId) updated else it }
        return ApiResponse(true, updated)
    }

    override suspend fun deleteChecklist(listId: String): SuccessResponse {
        remoteChecklists.removeAll { it.id == listId }
        return SuccessResponse(true)
    }

    override suspend fun addChecklistItem(listId: String, body: AddItemRequest): SuccessResponse = SuccessResponse(true)

    override suspend fun checkItem(listId: String, itemIndex: String): SuccessResponse = SuccessResponse(true)

    override suspend fun uncheckItem(listId: String, itemIndex: String): SuccessResponse = SuccessResponse(true)

    override suspend fun updateItem(listId: String, itemIndex: String, body: UpdateItemRequest): SuccessResponse =
        SuccessResponse(true)

    override suspend fun deleteItem(listId: String, itemIndex: String): SuccessResponse =
        onDeleteItem(listId, itemIndex)

    override suspend fun getNotes(category: String?, search: String?): NotesResponse = NotesResponse(emptyList())

    override suspend fun createNote(body: CreateNoteRequest): ApiResponse<Note> =
        error("not used in checklist tests")

    override suspend fun updateNote(noteId: String, body: UpdateNoteRequest): ApiResponse<Note> =
        error("not used in checklist tests")

    override suspend fun deleteNote(noteId: String): SuccessResponse =
        error("not used in checklist tests")

    override suspend fun getCategories(): CategoriesResponse =
        CategoriesResponse(Categories(notes = emptyList(), checklists = emptyList()))

    override suspend fun getAdminOverview(): AdminOverviewResponse = AdminOverviewResponse()

    override suspend fun getSummary(): SummaryResponse = SummaryResponse()
}
