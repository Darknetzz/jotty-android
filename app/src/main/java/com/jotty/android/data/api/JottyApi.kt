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
    suspend fun deleteChecklist(@Path("listId") listId: String): SuccessResponse

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

    @GET("api/notes")
    suspend fun getNotes(
        @Query("category") category: String? = null,
        @Query("q") search: String? = null,
    ): NotesResponse

    @POST("api/notes")
    suspend fun createNote(@Body body: CreateNoteRequest): ApiResponse<Note>

    @PUT("api/notes/{noteId}")
    suspend fun updateNote(
        @Path("noteId") noteId: String,
        @Body body: UpdateNoteRequest,
    ): ApiResponse<Note>

    @DELETE("api/notes/{noteId}")
    suspend fun deleteNote(@Path("noteId") noteId: String): SuccessResponse

    @GET("api/categories")
    suspend fun getCategories(): CategoriesResponse
}
