package com.jotty.android.data.local

import com.jotty.android.data.api.API_CATEGORY_UNCATEGORIZED
import com.jotty.android.data.api.AddItemRequest
import com.jotty.android.data.api.AdminOverviewResponse
import com.jotty.android.data.api.ApiResponse
import com.jotty.android.data.api.Categories
import com.jotty.android.data.api.CategoriesResponse
import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.ChecklistsResponse
import com.jotty.android.data.api.CreateChecklistRequest
import com.jotty.android.data.api.CreateNoteRequest
import com.jotty.android.data.api.HealthResponse
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import com.jotty.android.data.api.NotesResponse
import com.jotty.android.data.api.CreateTaskStatusRequest
import com.jotty.android.data.api.DEFAULT_TASK_STATUSES
import com.jotty.android.data.api.SuccessResponse
import com.jotty.android.data.api.SummaryResponse
import com.jotty.android.data.api.TaskChecklist
import com.jotty.android.data.api.TaskResponse
import com.jotty.android.data.api.TaskStatus
import com.jotty.android.data.api.TaskStatusesResponse
import com.jotty.android.data.api.UpdateChecklistRequest
import com.jotty.android.data.api.UpdateNoteRequest
import com.jotty.android.data.api.UpdateTaskItemStatusRequest
import com.jotty.android.data.api.ShareInfoResponse
import com.jotty.android.data.api.UpdateShareInfoRequest
import com.jotty.android.data.api.UpdateTaskStatusRequest

/**
 * Test double for [JottyApi] — only note paths are configurable; other endpoints return minimal stubs.
 */
internal class FakeJottyApi(
    var notesFromGet: List<Note> = emptyList(),
    var createNoteResponse: (suspend (CreateNoteRequest) -> ApiResponse<Note>)? = null,
    var updateNoteHandler: (suspend (String, UpdateNoteRequest) -> ApiResponse<Note>)? = null,
    var deleteNoteResult: suspend (String) -> SuccessResponse = { SuccessResponse(true) },
) : JottyApi {
    override suspend fun health(): HealthResponse = HealthResponse("ok", "1", "")

    override suspend fun getChecklists(
        category: String?,
        type: String?,
        search: String?,
    ): ChecklistsResponse = ChecklistsResponse(emptyList())

    override suspend fun createChecklist(body: CreateChecklistRequest): ApiResponse<Checklist> = error("not used in OfflineNotesRepository tests")

    override suspend fun updateChecklist(
        listId: String,
        body: UpdateChecklistRequest,
    ): ApiResponse<Checklist> = error("not used in OfflineNotesRepository tests")

    override suspend fun deleteChecklist(listId: String): SuccessResponse = error("not used in OfflineNotesRepository tests")

    override suspend fun addChecklistItem(
        listId: String,
        body: AddItemRequest,
    ): SuccessResponse = error("not used in OfflineNotesRepository tests")

    override suspend fun checkItem(
        listId: String,
        itemIndex: String,
    ): SuccessResponse = error("not used in OfflineNotesRepository tests")

    override suspend fun uncheckItem(
        listId: String,
        itemIndex: String,
    ): SuccessResponse = error("not used in OfflineNotesRepository tests")

    override suspend fun deleteItem(
        listId: String,
        itemIndex: String,
    ): SuccessResponse = error("not used in OfflineNotesRepository tests")

    override suspend fun updateItem(
        listId: String,
        itemIndex: String,
        body: com.jotty.android.data.api.UpdateItemRequest,
    ): SuccessResponse = error("not used in OfflineNotesRepository tests")

    override suspend fun reorderItems(
        listId: String,
        body: com.jotty.android.data.api.ReorderItemsRequest,
    ): SuccessResponse = error("not used in OfflineNotesRepository tests")

    override suspend fun search(
        query: String,
        type: String?,
    ): com.jotty.android.data.api.SearchResponse = com.jotty.android.data.api.SearchResponse()

    override suspend fun getNotes(
        category: String?,
        search: String?,
    ): NotesResponse = NotesResponse(notesFromGet)

    override suspend fun createNote(body: CreateNoteRequest): ApiResponse<Note> {
        val handler =
            createNoteResponse ?: return ApiResponse(
                true,
                Note(
                    id = "server-new-id",
                    title = body.title,
                    category = body.category ?: API_CATEGORY_UNCATEGORIZED,
                    content = body.content.orEmpty(),
                    createdAt = "c",
                    updatedAt = "u",
                ),
            )
        return handler(body)
    }

    override suspend fun updateNote(
        noteId: String,
        body: UpdateNoteRequest,
    ): ApiResponse<Note> {
        val handler =
            updateNoteHandler ?: return ApiResponse(
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
        return handler(noteId, body)
    }

    override suspend fun deleteNote(noteId: String): SuccessResponse = deleteNoteResult(noteId)

    override suspend fun getCategories() = CategoriesResponse(Categories(notes = emptyList(), checklists = emptyList()))

    override suspend fun getAdminOverview(): AdminOverviewResponse = AdminOverviewResponse()

    override suspend fun getSummary() = SummaryResponse()

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
        ApiResponse(
            true,
            DEFAULT_TASK_STATUSES.first { it.id == statusId },
        )

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
    ): ShareInfoResponse = ShareInfoResponse(success = false)

    override suspend fun updateSharingInfo(
        itemType: String,
        itemId: String,
        body: UpdateShareInfoRequest,
    ): ShareInfoResponse = ShareInfoResponse(success = false)
}
