package com.jotty.android.data.api

// ─── Common ─────────────────────────────────────────────────────────────────

data class HealthResponse(
    val status: String,
    val version: String?,
    val timestamp: String,
)

data class SuccessResponse(val success: Boolean = true)

data class ApiResponse<T>(val success: Boolean, val data: T)

// ─── Checklists ─────────────────────────────────────────────────────────────

data class ChecklistsResponse(val checklists: List<Checklist>)

data class Checklist(
    val id: String,
    val title: String,
    val category: String = "Uncategorized",
    val type: String = "regular",
    val items: List<ChecklistItem> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
)

data class ChecklistItem(
    val id: String? = null,
    val index: Int,
    val text: String,
    val completed: Boolean = false,
    val status: String? = null,
    val time: Any? = null,
    val children: List<ChecklistItem>? = null,
)

data class CreateChecklistRequest(
    val title: String,
    val category: String? = "Uncategorized",
    val type: String = "simple",
)

data class UpdateChecklistRequest(
    val title: String? = null,
    val category: String? = null,
)

data class AddItemRequest(
    val text: String,
    val status: String? = null,
    val parentIndex: String? = null,
)

// ─── Notes ──────────────────────────────────────────────────────────────────

data class NotesResponse(val notes: List<Note>)

data class Note(
    val id: String,
    val title: String,
    val category: String = "Uncategorized",
    val content: String = "",
    val createdAt: String,
    val updatedAt: String,
)

data class CreateNoteRequest(
    val title: String,
    val content: String? = "",
    val category: String? = "Uncategorized",
)

data class UpdateNoteRequest(
    val title: String,
    val content: String? = null,
    val category: String? = null,
    val originalCategory: String? = null,
)

// ─── Categories ─────────────────────────────────────────────────────────────

data class CategoriesResponse(
    val categories: Categories,
)

data class Categories(
    val notes: List<CategoryInfo> = emptyList(),
    val checklists: List<CategoryInfo> = emptyList(),
)

data class CategoryInfo(
    val name: String,
    val path: String,
    val count: Int,
    val level: Int,
)
