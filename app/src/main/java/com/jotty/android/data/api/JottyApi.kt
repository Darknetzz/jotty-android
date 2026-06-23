package com.jotty.android.data.api

import retrofit2.http.*

interface JottyApi {
    @GET("api/health")
    suspend fun health(): HealthResponse

    @GET("api/checklists")
    suspend fun getChecklists(
        @Query("category") category: String? = null,
        @Query("type") type: String? = null,
        @Query("q") search: String? = null,
    ): ChecklistsResponse

    @POST("api/checklists")
    suspend fun createChecklist(
        @Body body: CreateChecklistRequest,
    ): ApiResponse<Checklist>

    @PUT("api/checklists/{listId}")
    suspend fun updateChecklist(
        @Path("listId") listId: String,
        @Body body: UpdateChecklistRequest,
    ): ApiResponse<Checklist>

    @DELETE("api/checklists/{listId}")
    suspend fun deleteChecklist(
        @Path("listId") listId: String,
    ): SuccessResponse

    @POST("api/checklists/{listId}/items")
    suspend fun addChecklistItem(
        @Path("listId") listId: String,
        @Body body: AddItemRequest,
    ): SuccessResponse

    @PUT("api/checklists/{listId}/items/{itemIndex}/check")
    suspend fun checkItem(
        @Path("listId") listId: String,
        @Path("itemIndex") itemIndex: String,
    ): SuccessResponse

    @PUT("api/checklists/{listId}/items/{itemIndex}/uncheck")
    suspend fun uncheckItem(
        @Path("listId") listId: String,
        @Path("itemIndex") itemIndex: String,
    ): SuccessResponse

    @DELETE("api/checklists/{listId}/items/{itemIndex}")
    suspend fun deleteItem(
        @Path("listId") listId: String,
        @Path("itemIndex") itemIndex: String,
    ): SuccessResponse

    @PATCH("api/checklists/{listId}/items/{itemIndex}")
    suspend fun updateItem(
        @Path("listId") listId: String,
        @Path("itemIndex") itemIndex: String,
        @Body body: UpdateItemRequest,
    ): SuccessResponse

    /** Partial PATCH body for rich Kanban fields (supports explicit null to clear). */
    @PATCH("api/checklists/{listId}/items/{itemIndex}")
    suspend fun updateItemPatch(
        @Path("listId") listId: String,
        @Path("itemIndex") itemIndex: String,
        @Body body: okhttp3.RequestBody,
    ): SuccessResponse

    @PUT("api/checklists/{listId}/items/reorder")
    suspend fun reorderItems(
        @Path("listId") listId: String,
        @Body body: ReorderItemsRequest,
    ): SuccessResponse

    @GET("api/tasks/{taskId}")
    suspend fun getTask(
        @Path("taskId") taskId: String,
    ): TaskResponse

    /** Single Kanban item with expanded fields (404/405 on older Jotty servers). */
    @GET("api/tasks/{taskId}/items/{itemIndex}")
    suspend fun getTaskItem(
        @Path("taskId") taskId: String,
        @Path("itemIndex") itemIndex: String,
    ): TaskItemResponse

    @GET("api/tasks/{taskId}/statuses")
    suspend fun getTaskStatuses(
        @Path("taskId") taskId: String,
    ): TaskStatusesResponse

    @POST("api/tasks/{taskId}/statuses")
    suspend fun createTaskStatus(
        @Path("taskId") taskId: String,
        @Body body: CreateTaskStatusRequest,
    ): ApiResponse<TaskStatus>

    @PUT("api/tasks/{taskId}/statuses/{statusId}")
    suspend fun updateTaskStatus(
        @Path("taskId") taskId: String,
        @Path("statusId") statusId: String,
        @Body body: UpdateTaskStatusRequest,
    ): ApiResponse<TaskStatus>

    @DELETE("api/tasks/{taskId}/statuses/{statusId}")
    suspend fun deleteTaskStatus(
        @Path("taskId") taskId: String,
        @Path("statusId") statusId: String,
    ): SuccessResponse

    @PUT("api/tasks/{taskId}/items/{itemIndex}/status")
    suspend fun updateTaskItemStatus(
        @Path("taskId") taskId: String,
        @Path("itemIndex") itemIndex: String,
        @Body body: UpdateTaskItemStatusRequest,
    ): SuccessResponse

    @GET("api/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("type") type: String? = null,
    ): SearchResponse

    @GET("api/notes")
    suspend fun getNotes(
        @Query("category") category: String? = null,
        @Query("q") search: String? = null,
    ): NotesResponse

    @GET("api/notes/{noteId}")
    suspend fun getNote(
        @Path("noteId") noteId: String,
    ): ApiResponse<Note>

    @POST("api/notes")
    suspend fun createNote(
        @Body body: CreateNoteRequest,
    ): ApiResponse<Note>

    @PUT("api/notes/{noteId}")
    suspend fun updateNote(
        @Path("noteId") noteId: String,
        @Body body: UpdateNoteRequest,
    ): ApiResponse<Note>

    @DELETE("api/notes/{noteId}")
    suspend fun deleteNote(
        @Path("noteId") noteId: String,
    ): SuccessResponse

    @GET("api/categories")
    suspend fun getCategories(): CategoriesResponse

    /** Admin-only: dashboard overview. Returns 403 if not admin. May not exist on all servers. */
    @GET("api/admin/overview")
    suspend fun getAdminOverview(): AdminOverviewResponse

    /** User summary (notes, checklists, items). Works for all authenticated users. */
    @GET("api/summary")
    suspend fun getSummary(): SummaryResponse

    /** Optional: server sharing metadata (404 on older Jotty without REST sharing). */
    @GET("api/sharing/items/{itemType}/{itemId}")
    suspend fun getSharingInfo(
        @Path("itemType") itemType: String,
        @Path("itemId") itemId: String,
    ): ShareInfoResponse

    @PUT("api/sharing/items/{itemType}/{itemId}")
    suspend fun updateSharingInfo(
        @Path("itemType") itemType: String,
        @Path("itemId") itemId: String,
        @Body body: UpdateShareInfoRequest,
    ): ShareInfoResponse
}
