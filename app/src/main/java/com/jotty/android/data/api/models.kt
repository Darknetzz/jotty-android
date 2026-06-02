package com.jotty.android.data.api

import java.time.Instant

/** Category value used by the API for uncategorized items; use when comparing or sending category. */
const val API_CATEGORY_UNCATEGORIZED = "Uncategorized"

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
    val category: String = API_CATEGORY_UNCATEGORIZED,
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

/** Jotty may mark completion via [status] (`completed`) or [completed]; treat either as done. */
fun ChecklistItem.isCompletedForApi(): Boolean =
    completed || status.equals("completed", ignoreCase = true)

/** Align local storage with API completion fields so offline sync can detect already-applied ops. */
fun ChecklistItem.normalizedForLocal(): ChecklistItem {
    val done = isCompletedForApi()
    val normalizedChildren = children?.map { it.normalizedForLocal() }
    return copy(
        completed = done,
        status =
            when {
                done -> status?.takeIf { it.isNotBlank() } ?: "completed"
                else -> status?.takeIf { it.isNotBlank() } ?: "in_progress"
            },
        children = normalizedChildren,
    )
}

fun List<ChecklistItem>.normalizedForLocal(): List<ChecklistItem> = map { it.normalizedForLocal() }

data class CreateChecklistRequest(
    val title: String,
    val category: String? = API_CATEGORY_UNCATEGORIZED,
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
    val text: String? = null,
    val description: String? = null,
)

data class ReorderItemsRequest(
    val activeItemId: String,
    val overItemId: String,
    val position: String? = null,
    val isDropInto: Boolean? = null,
)

// ─── Task checklists (Kanban) ───────────────────────────────────────────────

/** Kanban column definition for a task checklist. */
data class TaskStatus(
    val id: String,
    val label: String,
    val order: Int = 0,
    val color: String? = null,
    val autoComplete: Boolean? = null,
)

data class TaskStatusesResponse(val statuses: List<TaskStatus> = emptyList())

data class CreateTaskStatusRequest(
    val id: String,
    val label: String,
    val color: String? = null,
    val order: Int? = null,
    val autoComplete: Boolean? = null,
)

data class UpdateTaskStatusRequest(
    val label: String? = null,
    val color: String? = null,
    val order: Int? = null,
    val autoComplete: Boolean? = null,
)

data class UpdateTaskItemStatusRequest(val status: String)

data class TaskResponse(val task: TaskChecklist)

/** Task checklist as returned by `/api/tasks` (includes column definitions). */
data class TaskChecklist(
    val id: String,
    val title: String,
    val category: String = API_CATEGORY_UNCATEGORIZED,
    val statuses: List<TaskStatus> = emptyList(),
    val items: List<ChecklistItem> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
)

/** Default Kanban columns when the server does not return custom statuses. */
val DEFAULT_TASK_STATUSES: List<TaskStatus> =
    listOf(
        TaskStatus(id = "todo", label = "To Do", order = 0),
        TaskStatus(id = "in_progress", label = "In Progress", order = 1),
        TaskStatus(id = "completed", label = "Completed", order = 2),
    )

// ─── Search ─────────────────────────────────────────────────────────────────

data class SearchResponse(
    val query: String? = null,
    val total: Int? = null,
    val results: List<SearchResult> = emptyList(),
)

data class SearchResult(
    val id: String,
    val uuid: String? = null,
    val type: String,
    val title: String,
    val category: String = API_CATEGORY_UNCATEGORIZED,
    val excerpt: String? = null,
)

// ─── Notes ──────────────────────────────────────────────────────────────────

data class NotesResponse(val notes: List<Note>)

data class Note(
    val id: String,
    val title: String,
    val category: String = API_CATEGORY_UNCATEGORIZED,
    val content: String = "",
    val createdAt: String,
    val updatedAt: String,
    /** Set by API when the note is stored encrypted (Jotty server may send this). */
    val encrypted: Boolean? = null,
)

/**
 * Some servers can return sparse/corrupt note objects (e.g. null title/content on malformed notes).
 * Normalize note fields so clients can still list/open these notes instead of dropping them.
 */
fun Note.normalizedForClient(): Note {
    val now = Instant.now().toString()
    val safeId = (id as String?)?.takeIf { it.isNotBlank() } ?: "missing-id-${now.hashCode()}"
    val safeTitle = (title as String?)?.ifBlank { "Untitled" } ?: "Untitled"
    val safeCategory = (category as String?)?.ifBlank { API_CATEGORY_UNCATEGORIZED } ?: API_CATEGORY_UNCATEGORIZED
    val safeContent = (content as String?) ?: ""
    val safeCreatedAt = (createdAt as String?)?.ifBlank { now } ?: now
    val safeUpdatedAt = (updatedAt as String?)?.ifBlank { safeCreatedAt } ?: safeCreatedAt
    val safeEncrypted = encrypted as Boolean?
    return copy(
        id = safeId,
        title = safeTitle,
        category = safeCategory,
        content = safeContent,
        createdAt = safeCreatedAt,
        updatedAt = safeUpdatedAt,
        encrypted = safeEncrypted,
    )
}

data class CreateNoteRequest(
    val title: String,
    val content: String? = "",
    val category: String? = API_CATEGORY_UNCATEGORIZED,
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
