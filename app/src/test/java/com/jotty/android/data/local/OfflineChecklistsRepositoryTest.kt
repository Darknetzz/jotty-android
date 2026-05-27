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
import com.jotty.android.data.api.UpdateNoteRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
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
class OfflineChecklistsRepositoryTest {
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
    fun syncChecklists_whenCancelled_doesNotSetLastSyncError() =
        runTest {
            val block = CompletableDeferred<Unit>()
            val remote =
                mutableListOf(
                    Checklist(
                        id = "remote-1",
                        title = "Remote",
                        category = API_CATEGORY_UNCATEGORIZED,
                        type = "simple",
                        items = emptyList(),
                        createdAt = "c",
                        updatedAt = "u",
                    ),
                )
            val api =
                object : FakeChecklistApi(remote) {
                    override suspend fun getChecklists(
                        category: String?,
                        type: String?,
                        search: String?,
                    ): ChecklistsResponse {
                        block.await()
                        return super.getChecklists(category, type, search)
                    }
                }
            val repo =
                OfflineChecklistsRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = api,
                    initialOnlineOverride = true,
                    useSharedConnectivity = false,
                )
            val syncJob = async { repo.syncChecklists(force = true) }
            while (!repo.isSyncing.value) {
                yield()
            }
            syncJob.cancelAndJoin()
            assertNull(repo.lastSyncError.value)
        }

    @Test
    fun syncChecklists_whenOffline_returnsFailure() =
        runTest {
            val repo =
                OfflineChecklistsRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = FakeChecklistApi(),
                    initialOnlineOverride = false,
                    useSharedConnectivity = false,
                )

            val result = repo.syncChecklists(force = true)

            assertTrue(result.isFailure)
        }

    @Test
    fun updateChecklist_whenOffline_marksDirtyAndUpdatesTitle() =
        runTest {
            database.checklistDao().insert(
                ChecklistEntity(
                    id = "list-1",
                    title = "Old title",
                    category = API_CATEGORY_UNCATEGORIZED,
                    type = "simple",
                    itemsJson = "[]",
                    pendingOpsJson = "[]",
                    createdAt = "c",
                    updatedAt = "u",
                    isDirty = false,
                    isDeleted = false,
                    instanceId = instanceId,
                    isLocalOnly = false,
                ),
            )

            val repo =
                OfflineChecklistsRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = FakeChecklistApi(),
                    initialOnlineOverride = false,
                    useSharedConnectivity = false,
                )

            val result = repo.updateChecklist("list-1", "New title")

            assertTrue(result.isSuccess)
            assertEquals("New title", result.getOrNull()?.title)
            val entity = database.checklistDao().getById("list-1")
            assertEquals("New title", entity?.title)
            assertEquals(true, entity?.isDirty)
        }

    @Test
    fun syncChecklists_whenPendingReplayFails_tracksReplayFailureCount() =
        runTest {
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

            val api =
                FakeChecklistApi(
                    remoteChecklists =
                        mutableListOf(
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

            val repo =
                OfflineChecklistsRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = api,
                    initialOnlineOverride = true,
                    useSharedConnectivity = false,
                )

            val result = repo.syncChecklists(force = true)

            assertTrue(result.isFailure)
            assertEquals(1, repo.replayFailuresDetected.value)
            val entity = database.checklistDao().getById("list-1")
            assertEquals(true, entity?.isDirty)
            assertEquals("Local list", entity?.title)
        }

    @Test
    fun renameLeafItem_whenOffline_replacesItemWithNewText() =
        runTest {
            val existingItems =
                listOf(
                    ChecklistItem(index = 0, text = "Keep"),
                    ChecklistItem(index = 1, text = "Old", completed = true),
                )
            database.checklistDao().insert(
                ChecklistEntity(
                    id = "list-1",
                    title = "Local list",
                    category = API_CATEGORY_UNCATEGORIZED,
                    type = "simple",
                    itemsJson = Gson().toJson(existingItems),
                    pendingOpsJson = "[]",
                    createdAt = "c",
                    updatedAt = "u",
                    isDirty = false,
                    isDeleted = false,
                    instanceId = instanceId,
                    isLocalOnly = false,
                ),
            )
            val repo =
                OfflineChecklistsRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = FakeChecklistApi(),
                    initialOnlineOverride = false,
                    useSharedConnectivity = false,
                )

            val result = repo.renameLeafItem("list-1", "1", "Renamed")

            assertTrue(result.isSuccess)
            val updatedItems = result.getOrNull()?.items.orEmpty()
            assertEquals(2, updatedItems.size)
            assertEquals("Keep", updatedItems[0].text)
            assertEquals("Renamed", updatedItems[1].text)
            assertTrue(updatedItems[1].completed)
        }

    @Test
    fun renameLeafItem_withChildren_returnsFailure() =
        runTest {
            val existingItems =
                listOf(
                    ChecklistItem(
                        index = 0,
                        text = "Parent",
                        children = listOf(ChecklistItem(index = 0, text = "Child")),
                    ),
                )
            database.checklistDao().insert(
                ChecklistEntity(
                    id = "list-1",
                    title = "Local list",
                    category = API_CATEGORY_UNCATEGORIZED,
                    type = "task",
                    itemsJson = Gson().toJson(existingItems),
                    pendingOpsJson = "[]",
                    createdAt = "c",
                    updatedAt = "u",
                    isDirty = false,
                    isDeleted = false,
                    instanceId = instanceId,
                    isLocalOnly = false,
                ),
            )
            val repo =
                OfflineChecklistsRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = FakeChecklistApi(),
                    initialOnlineOverride = false,
                    useSharedConnectivity = false,
                )

            val result = repo.renameLeafItem("list-1", "0", "Renamed")

            assertTrue(result.isFailure)
            val unchangedItems = database.checklistDao().getById("list-1")?.items().orEmpty()
            assertEquals("Parent", unchangedItems.firstOrNull()?.text)
        }

    @Test
    fun syncChecklists_whenPushFails_doesNotWipeLocalChecklistsWithServerSnapshot() =
        runTest {
            val serverList =
                Checklist(
                    id = "list-1",
                    title = "Server title",
                    category = API_CATEGORY_UNCATEGORIZED,
                    type = "simple",
                    items = listOf(ChecklistItem(index = 0, text = "Server item")),
                    createdAt = "c",
                    updatedAt = "u",
                )
            val api =
                FakeChecklistApi(
                    remoteChecklists = mutableListOf(serverList),
                    onUpdateChecklist = { _, _ -> throw RuntimeException("simulated push failure") },
                )
            database.checklistDao().insert(
                ChecklistEntity(
                    id = "list-1",
                    title = "Local title",
                    category = API_CATEGORY_UNCATEGORIZED,
                    type = "simple",
                    itemsJson = Gson().toJson(listOf(ChecklistItem(index = 0, text = "Local item"))),
                    pendingOpsJson = "[]",
                    createdAt = "c",
                    updatedAt = "u",
                    isDirty = true,
                    isDeleted = false,
                    instanceId = instanceId,
                    isLocalOnly = false,
                ),
            )
            val repo =
                OfflineChecklistsRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = api,
                    initialOnlineOverride = true,
                    useSharedConnectivity = false,
                )

            assertTrue(repo.syncChecklists(force = true).isFailure)

            val stored = database.checklistDao().getAllChecklists(instanceId)
            assertEquals(1, stored.size)
            val row = stored.single()
            assertEquals("list-1", row.id)
            assertTrue(row.isDirty)
            assertEquals("Local title", row.title)
        }

    @Test
    fun syncChecklists_whenDirtyChecklistDiffersFromServer_createsLocalCopy() =
        runTest {
            val serverList =
                Checklist(
                    id = "list-1",
                    title = "Server title",
                    category = API_CATEGORY_UNCATEGORIZED,
                    type = "simple",
                    items = listOf(ChecklistItem(index = 0, text = "Server item")),
                    createdAt = "c",
                    updatedAt = "u",
                )
            val api =
                FakeChecklistApi(
                    remoteChecklists = mutableListOf(serverList),
                    onUpdateChecklist = { _, _ ->
                        // Push succeeds but pull still returns the pre-conflict server snapshot.
                        ApiResponse(true, serverList)
                    },
                )
            database.checklistDao().insert(
                ChecklistEntity(
                    id = "list-1",
                    title = "Local title",
                    category = API_CATEGORY_UNCATEGORIZED,
                    type = "simple",
                    itemsJson = Gson().toJson(listOf(ChecklistItem(index = 0, text = "Local item"))),
                    pendingOpsJson = "[]",
                    createdAt = "c",
                    updatedAt = "u",
                    isDirty = true,
                    isDeleted = false,
                    instanceId = instanceId,
                    isLocalOnly = false,
                ),
            )
            val repo =
                OfflineChecklistsRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = api,
                    initialOnlineOverride = true,
                    useSharedConnectivity = false,
                )

            assertTrue(repo.syncChecklists(force = true).isSuccess)

            val stored = database.checklistDao().getAllChecklists(instanceId)
            assertEquals(2, stored.size)
            assertTrue(stored.any { it.title == "Local title${OfflineChecklistsRepository.LOCAL_COPY_SUFFIX}" })
            assertTrue(stored.any { it.title == "Server title" })
            assertEquals(1, repo.conflictsDetected.value)
        }

    @Test
    fun getConflictCopiesFlow_returnsOnlyLocalCopies() =
        runTest {
            val repo =
                OfflineChecklistsRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = FakeChecklistApi(),
                    initialOnlineOverride = true,
                    useSharedConnectivity = false,
                )
            database.checklistDao().insert(
                ChecklistEntity(
                    id = "regular",
                    title = "Regular",
                    category = API_CATEGORY_UNCATEGORIZED,
                    type = "simple",
                    itemsJson = "[]",
                    pendingOpsJson = "[]",
                    createdAt = "c",
                    updatedAt = "u",
                    instanceId = instanceId,
                ),
            )
            database.checklistDao().insert(
                ChecklistEntity(
                    id = "copy",
                    title = "Regular${OfflineChecklistsRepository.LOCAL_COPY_SUFFIX}",
                    category = API_CATEGORY_UNCATEGORIZED,
                    type = "simple",
                    itemsJson = "[]",
                    pendingOpsJson = "[]",
                    createdAt = "c",
                    updatedAt = "u",
                    instanceId = instanceId,
                ),
            )

            val conflictCopies = repo.getConflictCopiesFlow().first()

            assertEquals(1, conflictCopies.size)
            assertEquals("copy", conflictCopies.single().id)
        }
}

private class FakeChecklistApi(
    private val remoteChecklists: MutableList<Checklist> = mutableListOf(),
    private val onDeleteItem: suspend (String, String) -> SuccessResponse = { _, _ -> SuccessResponse(true) },
    private val onUpdateChecklist: suspend (String, UpdateChecklistRequest) -> ApiResponse<Checklist> = { listId, body ->
        val existing = remoteChecklists.first { it.id == listId }
        val updated =
            existing.copy(
                title = body.title ?: existing.title,
                category = body.category ?: existing.category,
            )
        remoteChecklists.replaceAll { if (it.id == listId) updated else it }
        ApiResponse(true, updated)
    },
) : JottyApi {
    override suspend fun health(): HealthResponse = HealthResponse("ok", "1", "")

    override suspend fun getChecklists(
        category: String?,
        type: String?,
        search: String?,
    ): ChecklistsResponse = ChecklistsResponse(remoteChecklists.toList())

    override suspend fun createChecklist(body: CreateChecklistRequest): ApiResponse<Checklist> {
        val created =
            Checklist(
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

    override suspend fun updateChecklist(
        listId: String,
        body: UpdateChecklistRequest,
    ): ApiResponse<Checklist> = onUpdateChecklist(listId, body)

    override suspend fun deleteChecklist(listId: String): SuccessResponse {
        remoteChecklists.removeAll { it.id == listId }
        return SuccessResponse(true)
    }

    override suspend fun addChecklistItem(
        listId: String,
        body: AddItemRequest,
    ): SuccessResponse = SuccessResponse(true)

    override suspend fun checkItem(
        listId: String,
        itemIndex: String,
    ): SuccessResponse = SuccessResponse(true)

    override suspend fun uncheckItem(
        listId: String,
        itemIndex: String,
    ): SuccessResponse = SuccessResponse(true)

    override suspend fun deleteItem(
        listId: String,
        itemIndex: String,
    ): SuccessResponse = onDeleteItem(listId, itemIndex)

    override suspend fun getNotes(
        category: String?,
        search: String?,
    ): NotesResponse = NotesResponse(emptyList())

    override suspend fun createNote(body: CreateNoteRequest): ApiResponse<Note> = error("not used in checklist tests")

    override suspend fun updateNote(
        noteId: String,
        body: UpdateNoteRequest,
    ): ApiResponse<Note> = error("not used in checklist tests")

    override suspend fun deleteNote(noteId: String): SuccessResponse = error("not used in checklist tests")

    override suspend fun getCategories(): CategoriesResponse = CategoriesResponse(Categories(notes = emptyList(), checklists = emptyList()))

    override suspend fun getAdminOverview(): AdminOverviewResponse = AdminOverviewResponse()

    override suspend fun getSummary(): SummaryResponse = SummaryResponse()
}
