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

data class UpdateItemRequest(
    val text: String,
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
    /** Set by API when the note is stored encrypted (Jotty server may send this). */
    val encrypted: Boolean? = null,
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

// ─── Admin / Summary ───────────────────────────────────────────────────────

/** Admin dashboard overview (only returned for admin users). May not exist on all servers. */
data class AdminOverviewResponse(
    val users: Int? = null,
    val checklists: Int? = null,
    val notes: Int? = null,
    val version: String? = null,
)

/** GET api/summary – user summary (notes, checklists, items). Used for dashboard overview. */
data class SummaryResponse(
    val summary: SummaryData? = null,
)

data class SummaryData(
    val username: String? = null,
    val notes: SummaryNotes? = null,
    val checklists: SummaryChecklists? = null,
    val items: SummaryItems? = null,
    val tasks: SummaryTasks? = null,
)

data class SummaryNotes(val total: Int? = null)
data class SummaryChecklists(val total: Int? = null)
data class SummaryItems(
    val total: Int? = null,
    val completed: Int? = null,
    val pending: Int? = null,
    val completionRate: Int? = null,
)
data class SummaryTasks(
    val total: Int? = null,
    val completed: Int? = null,
    val inProgress: Int? = null,
    val todo: Int? = null,
    val completionRate: Int? = null,
)
