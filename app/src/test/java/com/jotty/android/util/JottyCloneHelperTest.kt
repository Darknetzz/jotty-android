package com.jotty.android.util

import com.jotty.android.data.api.AddItemRequest
import com.jotty.android.data.api.AdminOverviewResponse
import com.jotty.android.data.api.ApiResponse
import com.jotty.android.data.api.CategoriesResponse
import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.ChecklistsResponse
import com.jotty.android.data.api.CreateChecklistRequest
import com.jotty.android.data.api.CreateNoteRequest
import com.jotty.android.data.api.CreateTaskStatusRequest
import com.jotty.android.data.api.HealthResponse
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import com.jotty.android.data.api.NotesResponse
import com.jotty.android.data.api.ReorderItemsRequest
import com.jotty.android.data.api.SearchResponse
import com.jotty.android.data.api.ShareInfoResponse
import com.jotty.android.data.api.SuccessResponse
import com.jotty.android.data.api.SummaryResponse
import com.jotty.android.data.api.TaskItemResponse
import com.jotty.android.data.api.TaskResponse
import com.jotty.android.data.api.TaskStatus
import com.jotty.android.data.api.TaskStatusesResponse
import com.jotty.android.data.api.UpdateChecklistRequest
import com.jotty.android.data.api.UpdateItemRequest
import com.jotty.android.data.api.UpdateNoteRequest
import com.jotty.android.data.api.UpdateShareInfoRequest
import com.jotty.android.data.api.UpdateTaskItemStatusRequest
import com.jotty.android.data.api.UpdateTaskStatusRequest
import kotlinx.coroutines.test.runTest
import okhttp3.RequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JottyCloneHelperTest {
    @Test
    fun cloneTitle_appendsCopySuffix() {
        assertEquals("My note (Copy)", cloneTitle("My note"))
    }

    @Test
    fun cloneTitle_blankTitle_stillAppendsSuffix() {
        assertEquals(" (Copy)", cloneTitle(""))
    }

    @Test
    fun apiChecklistTypeFrom_mapsProjectTypesToTask() {
        assertEquals("task", apiChecklistTypeFrom("task"))
        assertEquals("task", apiChecklistTypeFrom("project"))
        assertEquals("simple", apiChecklistTypeFrom("simple"))
    }

    @Test
    fun normalizedCloneCategory_blankUsesUncategorized() {
        assertEquals("Uncategorized", normalizedCloneCategory(""))
        assertEquals("Work", normalizedCloneCategory("Work"))
    }

    @Test
    fun cloneNote_sendsCreateRequestWithCopyTitleAndRawContent() =
        runTest {
            val api = RecordingJottyApi()
            val source =
                Note(
                    id = "src",
                    title = "Secret",
                    category = "Personal",
                    content = "encrypted-body",
                    createdAt = "t1",
                    updatedAt = "t1",
                    encrypted = true,
                )

            val result = cloneNote(api, source, "Templates")

            assertTrue(result.isSuccess)
            assertEquals(
                CreateNoteRequest(
                    title = "Secret (Copy)",
                    content = "encrypted-body",
                    category = "Templates",
                ),
                api.createNoteRequest,
            )
        }

    @Test
    fun cloneChecklist_replaysItemsDepthFirst() =
        runTest {
            val api = RecordingJottyApi()
            val source =
                Checklist(
                    id = "src",
                    title = "Weekly",
                    category = "Work",
                    type = "simple",
                    items =
                        listOf(
                            ChecklistItem(
                                id = "a",
                                index = 0,
                                text = "One",
                                completed = true,
                                children =
                                    listOf(
                                        ChecklistItem(id = "b", index = 0, text = "Nested"),
                                    ),
                            ),
                            ChecklistItem(id = "c", index = 1, text = "Two"),
                        ),
                    createdAt = "t1",
                    updatedAt = "t1",
                )

            val result = cloneChecklist(api, source, "Templates")

            assertTrue(result.isSuccess)
            assertEquals(
                CreateChecklistRequest(
                    title = "Weekly (Copy)",
                    category = "Templates",
                    type = "simple",
                ),
                api.createChecklistRequest,
            )
            assertEquals(
                listOf(
                    AddItemRequest(text = "One", status = null, parentIndex = null),
                    AddItemRequest(text = "Nested", status = null, parentIndex = "0"),
                    AddItemRequest(text = "Two", status = null, parentIndex = null),
                ),
                api.addItemCalls,
            )
            assertEquals(listOf("c1" to "0"), api.checkItemCalls)
        }

    private class RecordingJottyApi : JottyApi {
        var createNoteRequest: CreateNoteRequest? = null
        var createChecklistRequest: CreateChecklistRequest? = null
        val addItemCalls = mutableListOf<AddItemRequest>()
        val checkItemCalls = mutableListOf<Pair<String, String>>()

        override suspend fun health(): HealthResponse = unsupported()

        override suspend fun getChecklists(
            category: String?,
            type: String?,
            search: String?,
        ): ChecklistsResponse = unsupported()

        override suspend fun createChecklist(body: CreateChecklistRequest): ApiResponse<Checklist> {
            createChecklistRequest = body
            return ApiResponse(
                success = true,
                data =
                    Checklist(
                        id = "c1",
                        title = body.title,
                        category = body.category ?: "Uncategorized",
                        type = body.type,
                        items = emptyList(),
                        createdAt = "t1",
                        updatedAt = "t1",
                    ),
            )
        }

        override suspend fun updateChecklist(
            listId: String,
            body: UpdateChecklistRequest,
        ): ApiResponse<Checklist> = unsupported()

        override suspend fun deleteChecklist(listId: String): SuccessResponse = unsupported()

        override suspend fun addChecklistItem(
            listId: String,
            body: AddItemRequest,
        ): SuccessResponse {
            addItemCalls.add(body)
            return SuccessResponse()
        }

        override suspend fun checkItem(
            listId: String,
            itemIndex: String,
        ): SuccessResponse {
            checkItemCalls.add(listId to itemIndex)
            return SuccessResponse()
        }

        override suspend fun uncheckItem(
            listId: String,
            itemIndex: String,
        ): SuccessResponse = unsupported()

        override suspend fun deleteItem(
            listId: String,
            itemIndex: String,
        ): SuccessResponse = unsupported()

        override suspend fun updateItem(
            listId: String,
            itemIndex: String,
            body: UpdateItemRequest,
        ): SuccessResponse = unsupported()

        override suspend fun updateItemPatch(
            listId: String,
            itemIndex: String,
            body: RequestBody,
        ): SuccessResponse = unsupported()

        override suspend fun reorderItems(
            listId: String,
            body: ReorderItemsRequest,
        ): SuccessResponse = unsupported()

        override suspend fun getTask(taskId: String): TaskResponse = unsupported()

        override suspend fun getTaskItem(
            taskId: String,
            itemIndex: String,
        ): TaskItemResponse = unsupported()

        override suspend fun getTaskStatuses(taskId: String): TaskStatusesResponse = unsupported()

        override suspend fun createTaskStatus(
            taskId: String,
            body: CreateTaskStatusRequest,
        ): ApiResponse<TaskStatus> = unsupported()

        override suspend fun updateTaskStatus(
            taskId: String,
            statusId: String,
            body: UpdateTaskStatusRequest,
        ): ApiResponse<TaskStatus> = unsupported()

        override suspend fun deleteTaskStatus(
            taskId: String,
            statusId: String,
        ): SuccessResponse = unsupported()

        override suspend fun updateTaskItemStatus(
            taskId: String,
            itemIndex: String,
            body: UpdateTaskItemStatusRequest,
        ): SuccessResponse = unsupported()

        override suspend fun search(
            query: String,
            type: String?,
        ): SearchResponse = unsupported()

        override suspend fun getNotes(
            category: String?,
            search: String?,
        ): NotesResponse = unsupported()

        override suspend fun getNote(noteId: String): ApiResponse<Note> = unsupported()

        override suspend fun createNote(body: CreateNoteRequest): ApiResponse<Note> {
            createNoteRequest = body
            return ApiResponse(
                success = true,
                data =
                    Note(
                        id = "n1",
                        title = body.title,
                        category = body.category ?: "Uncategorized",
                        content = body.content ?: "",
                        createdAt = "t1",
                        updatedAt = "t1",
                    ),
            )
        }

        override suspend fun updateNote(
            noteId: String,
            body: UpdateNoteRequest,
        ): ApiResponse<Note> = unsupported()

        override suspend fun deleteNote(noteId: String): SuccessResponse = unsupported()

        override suspend fun getCategories(): CategoriesResponse = unsupported()

        override suspend fun getAdminOverview(): AdminOverviewResponse = unsupported()

        override suspend fun getSummary(): SummaryResponse = unsupported()

        override suspend fun getSharingInfo(
            itemType: String,
            itemId: String,
        ): ShareInfoResponse = unsupported()

        override suspend fun updateSharingInfo(
            itemType: String,
            itemId: String,
            body: UpdateShareInfoRequest,
        ): ShareInfoResponse = unsupported()

        private fun <T> unsupported(): T = throw UnsupportedOperationException()
    }
}
