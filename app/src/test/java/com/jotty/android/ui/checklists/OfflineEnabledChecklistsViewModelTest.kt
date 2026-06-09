package com.jotty.android.ui.checklists

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jotty.android.data.api.AddItemRequest
import com.jotty.android.data.api.AdminOverviewResponse
import com.jotty.android.data.api.ApiResponse
import com.jotty.android.data.api.Categories
import com.jotty.android.data.api.CategoriesResponse
import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.ChecklistsResponse
import com.jotty.android.data.api.CreateChecklistRequest
import com.jotty.android.data.api.CreateNoteRequest
import com.jotty.android.data.api.CreateTaskStatusRequest
import com.jotty.android.data.api.DEFAULT_TASK_STATUSES
import com.jotty.android.data.api.HealthResponse
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import com.jotty.android.data.api.NotesResponse
import com.jotty.android.data.api.SuccessResponse
import com.jotty.android.data.api.SummaryResponse
import com.jotty.android.data.api.TaskChecklist
import com.jotty.android.data.api.TaskResponse
import com.jotty.android.data.api.TaskStatus
import com.jotty.android.data.api.TaskStatusesResponse
import com.jotty.android.data.api.UpdateChecklistRequest
import com.jotty.android.data.api.UpdateNoteRequest
import com.jotty.android.data.api.UpdateTaskItemStatusRequest
import com.jotty.android.data.api.UpdateTaskStatusRequest
import com.jotty.android.data.local.JottyDatabase
import com.jotty.android.data.local.OfflineChecklistsRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineEnabledChecklistsViewModelTest {
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
                OfflineChecklistsRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = MinimalChecklistApi(),
                    initialOnlineOverride = true,
                    useSharedConnectivity = false,
                )
            val vm = OfflineEnabledChecklistsViewModel(repo, MinimalChecklistApi())

            vm.applyConflictSearchFilter()

            assertEquals("(Local copy)", vm.searchQuery.value)
            repo.close()
        }

    @Test
    fun setSearchQuery_updatesSearchQuery() =
        runTest {
            val repo =
                OfflineChecklistsRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = MinimalChecklistApi(),
                    initialOnlineOverride = true,
                    useSharedConnectivity = false,
                )
            val vm = OfflineEnabledChecklistsViewModel(repo, MinimalChecklistApi())

            vm.setSearchQuery("Groceries")

            assertEquals("Groceries", vm.searchQuery.value)
            repo.close()
        }

    @Test
    fun toggleCategoryChip_togglesSelection() =
        runTest {
            val repo =
                OfflineChecklistsRepository(
                    context = context,
                    database = database,
                    instanceId = instanceId,
                    api = MinimalChecklistApi(),
                    initialOnlineOverride = true,
                    useSharedConnectivity = false,
                )
            val vm = OfflineEnabledChecklistsViewModel(repo, MinimalChecklistApi())

            vm.toggleCategoryChip("Home")
            assertEquals("Home", vm.selectedCategory.value)
            vm.toggleCategoryChip("Home")
            assertEquals(null, vm.selectedCategory.value)
            repo.close()
        }
}

private class MinimalChecklistApi : JottyApi {
    override suspend fun health(): HealthResponse = HealthResponse("ok", "1", "")

    override suspend fun getChecklists(
        category: String?,
        type: String?,
        search: String?,
    ): ChecklistsResponse = ChecklistsResponse(emptyList())

    override suspend fun createChecklist(body: CreateChecklistRequest): ApiResponse<Checklist> = error("unused")

    override suspend fun updateChecklist(
        listId: String,
        body: UpdateChecklistRequest,
    ): ApiResponse<Checklist> = error("unused")

    override suspend fun deleteChecklist(listId: String): SuccessResponse = error("unused")

    override suspend fun addChecklistItem(
        listId: String,
        body: AddItemRequest,
    ): SuccessResponse = error("unused")

    override suspend fun checkItem(
        listId: String,
        itemIndex: String,
    ): SuccessResponse = error("unused")

    override suspend fun uncheckItem(
        listId: String,
        itemIndex: String,
    ): SuccessResponse = error("unused")

    override suspend fun deleteItem(
        listId: String,
        itemIndex: String,
    ): SuccessResponse = error("unused")

    override suspend fun updateItem(
        listId: String,
        itemIndex: String,
        body: com.jotty.android.data.api.UpdateItemRequest,
    ): SuccessResponse = error("unused")

    override suspend fun reorderItems(
        listId: String,
        body: com.jotty.android.data.api.ReorderItemsRequest,
    ): SuccessResponse = error("unused")

    override suspend fun search(
        query: String,
        type: String?,
    ): com.jotty.android.data.api.SearchResponse = com.jotty.android.data.api.SearchResponse()

    override suspend fun getNotes(
        category: String?,
        search: String?,
    ): NotesResponse = NotesResponse(emptyList())

    override suspend fun createNote(body: CreateNoteRequest): ApiResponse<Note> = error("unused")

    override suspend fun updateNote(
        noteId: String,
        body: UpdateNoteRequest,
    ): ApiResponse<Note> = error("unused")

    override suspend fun deleteNote(noteId: String): SuccessResponse = error("unused")

    override suspend fun getCategories(): CategoriesResponse =
        CategoriesResponse(Categories(notes = emptyList(), checklists = emptyList()))

    override suspend fun getAdminOverview(): AdminOverviewResponse = AdminOverviewResponse()

    override suspend fun getSummary(): SummaryResponse = SummaryResponse()

    override suspend fun getTask(taskId: String): TaskResponse =
        TaskResponse(
            TaskChecklist(
                id = taskId,
                title = "",
                items = emptyList(),
                createdAt = "",
                updatedAt = "",
            ),
        )

    override suspend fun getTaskStatuses(taskId: String): TaskStatusesResponse =
        TaskStatusesResponse(DEFAULT_TASK_STATUSES)

    override suspend fun createTaskStatus(
        taskId: String,
        body: CreateTaskStatusRequest,
    ): ApiResponse<TaskStatus> =
        ApiResponse(
            true,
            TaskStatus(
                id = body.id,
                label = body.label,
                order = body.order ?: 0,
                color = body.color,
                autoComplete = body.autoComplete,
            ),
        )

    override suspend fun updateTaskStatus(
        taskId: String,
        statusId: String,
        body: UpdateTaskStatusRequest,
    ): ApiResponse<TaskStatus> =
        ApiResponse(true, DEFAULT_TASK_STATUSES.first { it.id == statusId })

    override suspend fun deleteTaskStatus(
        taskId: String,
        statusId: String,
    ): SuccessResponse = SuccessResponse(true)

    override suspend fun updateTaskItemStatus(
        taskId: String,
        itemIndex: String,
        body: UpdateTaskItemStatusRequest,
    ): SuccessResponse = SuccessResponse(true)

    override suspend fun getSharingInfo(
        itemType: String,
        itemId: String,
    ): com.jotty.android.data.api.ShareInfoResponse = com.jotty.android.data.api.ShareInfoResponse(success = false)

    override suspend fun updateSharingInfo(
        itemType: String,
        itemId: String,
        body: com.jotty.android.data.api.UpdateShareInfoRequest,
    ): com.jotty.android.data.api.ShareInfoResponse = com.jotty.android.data.api.ShareInfoResponse(success = false)
}
