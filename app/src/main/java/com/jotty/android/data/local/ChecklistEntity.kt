package com.jotty.android.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.util.reorderChecklistItems

private val gson = Gson()

/**
 * Pending item operation recorded while offline. Replayed against the server on next sync.
 * [path] is the positional API path (e.g. "0", "0.1") at the time the op was created.
 * Paths may be stale if the server was modified concurrently; failing ops are skipped.
 */
data class PendingItemOp(
    /** CHECK, UNCHECK, ADD, DELETE, UPDATE, REORDER */
    val type: String,
    /** Positional path for existing-item ops */
    val path: String? = null,
    /** ADD / UPDATE */
    val text: String? = null,
    /** ADD with parent (project type) */
    val parentIndex: String? = null,
    /** UPDATE */
    val description: String? = null,
    /** REORDER */
    val activeItemId: String? = null,
    val overItemId: String? = null,
    val position: String? = null,
    val isDropInto: Boolean? = null,
)

@Entity(tableName = "checklists", indices = [Index("instanceId")])
data class ChecklistEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val type: String,
    /** Gson-serialized List<ChecklistItem> — the full item tree. */
    val itemsJson: String = "[]",
    /** Gson-serialized List<PendingItemOp> — replayed against the server on sync. */
    val pendingOpsJson: String = "[]",
    val createdAt: String,
    val updatedAt: String,
    val isDirty: Boolean = false,
    val isDeleted: Boolean = false,
    val instanceId: String,
    @ColumnInfo(defaultValue = "0")
    val isLocalOnly: Boolean = false,
)

// ─── Type tokens ────────────────────────────────────────────────────────────

private val itemListType = object : TypeToken<List<ChecklistItem>>() {}.type
private val opListType = object : TypeToken<List<PendingItemOp>>() {}.type

fun ChecklistEntity.items(): List<ChecklistItem> =
    runCatching { gson.fromJson<List<ChecklistItem>>(itemsJson, itemListType) }
        .getOrDefault(emptyList())

fun ChecklistEntity.pendingOps(): List<PendingItemOp> =
    runCatching { gson.fromJson<List<PendingItemOp>>(pendingOpsJson, opListType) }
        .getOrDefault(emptyList())

// ─── Conversion ─────────────────────────────────────────────────────────────

fun ChecklistEntity.toChecklist(): Checklist =
    Checklist(
        id = id,
        title = title,
        category = category,
        type = type,
        items = items(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun Checklist.toEntity(
    instanceId: String,
    isDirty: Boolean = false,
): ChecklistEntity =
    ChecklistEntity(
        id = id,
        title = title,
        category = category,
        type = type,
        itemsJson = gson.toJson(items),
        pendingOpsJson = "[]",
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDirty = isDirty,
        isDeleted = false,
        instanceId = instanceId,
        isLocalOnly = false,
    )

// ─── Local item tree mutation ────────────────────────────────────────────────

/** Apply a [PendingItemOp] to a list of items, returning the modified list. */
fun applyOpToItems(
    items: List<ChecklistItem>,
    op: PendingItemOp,
): List<ChecklistItem> =
    when (op.type) {
        "CHECK" -> op.path?.let { updateAtPath(items, it) { i -> i.copy(completed = true) } } ?: items
        "UNCHECK" -> op.path?.let { updateAtPath(items, it) { i -> i.copy(completed = false) } } ?: items
        "DELETE" -> op.path?.let { deleteAtPath(items, it) } ?: items
        "ADD" -> {
            if (op.parentIndex == null) {
                items + ChecklistItem(index = items.size, text = op.text ?: "")
            } else {
                // Child index must be relative to the parent's children, not the root list.
                updateAtPath(items, op.parentIndex) { parent ->
                    val children = parent.children ?: emptyList()
                    parent.copy(children = children + ChecklistItem(index = children.size, text = op.text ?: ""))
                }
            }
        }
        "UPDATE" ->
            op.path?.let { path ->
                updateAtPath(items, path) { item ->
                    item.copy(
                        text = op.text ?: item.text,
                    )
                }
            } ?: items
        "REORDER" -> {
            val activeId = op.activeItemId
            val overId = op.overItemId
            if (activeId == null || overId == null) {
                items
            } else {
                reorderChecklistItems(
                    items = items,
                    activeItemId = activeId,
                    overItemId = overId,
                    position = op.position ?: "before",
                    isDropInto = op.isDropInto ?: false,
                )
            }
        }
        else -> items
    }

/**
 * Returns null if any segment of [path] is not a valid integer.
 * Callers treat null as a no-op (stale or malformed path).
 */
private fun pathSegments(path: String): List<Int>? = path.split(".").map { it.toIntOrNull() ?: return null }

/** Item at a positional API path (e.g. `"0"`, `"0.1"`), or null if the path is invalid or out of range. */
fun itemAtPath(
    items: List<ChecklistItem>,
    path: String,
): ChecklistItem? {
    val segments = pathSegments(path) ?: return null
    return itemAtSegments(items, segments)
}

private fun itemAtSegments(
    items: List<ChecklistItem>,
    segments: List<Int>,
): ChecklistItem? {
    if (segments.isEmpty()) return null
    val idx = segments[0]
    if (idx < 0 || idx >= items.size) return null
    return if (segments.size == 1) {
        items[idx]
    } else {
        itemAtSegments(items[idx].children.orEmpty(), segments.drop(1))
    }
}

/** True when [op] is already reflected in [items] (e.g. CHECK and the item is already completed). */
fun isPendingOpSatisfied(
    items: List<ChecklistItem>,
    op: PendingItemOp,
): Boolean =
    when (op.type) {
        "CHECK" -> op.path?.let { itemAtPath(items, it)?.completed == true } == true
        "UNCHECK" -> op.path?.let { itemAtPath(items, it)?.completed == false } == true
        else -> false
    }

private fun updateAtPath(
    items: List<ChecklistItem>,
    path: String,
    transform: (ChecklistItem) -> ChecklistItem,
): List<ChecklistItem> {
    val segments = pathSegments(path) ?: return items
    return updateAtSegments(items, segments, transform)
}

private fun updateAtSegments(
    items: List<ChecklistItem>,
    segments: List<Int>,
    transform: (ChecklistItem) -> ChecklistItem,
): List<ChecklistItem> {
    if (segments.isEmpty()) return items
    val idx = segments[0]
    if (idx < 0 || idx >= items.size) return items
    return if (segments.size == 1) {
        items.toMutableList().also { it[idx] = transform(it[idx]) }
    } else {
        items.toMutableList().also { list ->
            val parent = list[idx]
            list[idx] =
                parent.copy(
                    children = updateAtSegments(parent.children ?: emptyList(), segments.drop(1), transform),
                )
        }
    }
}

private fun deleteAtPath(
    items: List<ChecklistItem>,
    path: String,
): List<ChecklistItem> {
    val segments = pathSegments(path) ?: return items
    return deleteAtSegments(items, segments)
}

private fun deleteAtSegments(
    items: List<ChecklistItem>,
    segments: List<Int>,
): List<ChecklistItem> {
    if (segments.isEmpty()) return items
    val idx = segments[0]
    if (idx < 0 || idx >= items.size) return items
    return if (segments.size == 1) {
        items.toMutableList().also { it.removeAt(idx) }
            .mapIndexed { i, item -> item.copy(index = i) }
    } else {
        items.toMutableList().also { list ->
            val parent = list[idx]
            val newChildren =
                deleteAtSegments(parent.children ?: emptyList(), segments.drop(1))
                    .mapIndexed { i, item -> item.copy(index = i) }
            list[idx] = parent.copy(children = newChildren)
        }
    }
}
