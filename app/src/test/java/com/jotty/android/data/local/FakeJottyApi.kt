package com.jotty.android.data.local

import com.jotty.android.data.api.API_CATEGORY_UNCATEGORIZED
import com.jotty.android.data.api.AddItemRequest
import com.jotty.android.data.api.AdminOverviewResponse
import com.jotty.android.data.api.ApiResponse
import com.jotty.android.data.api.CategoriesResponse
import com.jotty.android.data.api.Categories
import com.jotty.android.data.api.Checklist
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

/**
 * Test double for [JottyApi] — only note paths are configurable; other endpoints return minimal stubs.
 */
internal class FakeJottyApi(
    var notesFromGet: List<Note> = emptyList(),
    var createNoteResponse: (suspend (CreateNoteRequest) -> ApiResponse<Note>)? = null,
    var updateNoteHandler: (suspend (String, UpdateNoteRequest) -> ApiResponse<Note>)? = null,
    var deleteNoteResult: suspend (String) -> SuccessResponse = { SuccessResponse(true) },
) : JottyApi {

    override suspend fun health(): HealthResponse =
        HealthResponse("ok", "1", "")

    override suspend fun getChecklists(category: String?, type: String?, search: String?): ChecklistsResponse =
        ChecklistsResponse(emptyList())

    override suspend fun createChecklist(body: CreateChecklistRequest): ApiResponse<Checklist> =
        error("not used in OfflineNotesRepository tests")

    override suspend fun updateChecklist(listId: String, body: UpdateChecklistRequest): ApiResponse<Checklist> =
        error("not used in OfflineNotesRepository tests")

    override suspend fun deleteChecklist(listId: String): SuccessResponse =
        error("not used in OfflineNotesRepository tests")

    override suspend fun addChecklistItem(listId: String, body: AddItemRequest): SuccessResponse =
        error("not used in OfflineNotesRepository tests")

    override suspend fun checkItem(listId: String, itemIndex: String): SuccessResponse =
        error("not used in OfflineNotesRepository tests")

    override suspend fun uncheckItem(listId: String, itemIndex: String): SuccessResponse =
        error("not used in OfflineNotesRepository tests")

    override suspend fun updateItem(listId: String, itemIndex: String, body: UpdateItemRequest): SuccessResponse =
        error("not used in OfflineNotesRepository tests")

    override suspend fun deleteItem(listId: String, itemIndex: String): SuccessResponse =
        error("not used in OfflineNotesRepository tests")

    override suspend fun getNotes(category: String?, search: String?): NotesResponse =
        NotesResponse(notesFromGet)

    override suspend fun createNote(body: CreateNoteRequest): ApiResponse<Note> {
        val handler = createNoteResponse ?: return ApiResponse(
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

    override suspend fun updateNote(noteId: String, body: UpdateNoteRequest): ApiResponse<Note> {
        val handler = updateNoteHandler ?: return ApiResponse(
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
        return handler(noteId, body)
    }

    override suspend fun deleteNote(noteId: String): SuccessResponse =
        deleteNoteResult(noteId)

    override suspend fun getCategories() =
        CategoriesResponse(Categories(notes = emptyList(), checklists = emptyList()))

    override suspend fun getAdminOverview(): AdminOverviewResponse =
        AdminOverviewResponse()

    override suspend fun getSummary() =
        SummaryResponse()
}
