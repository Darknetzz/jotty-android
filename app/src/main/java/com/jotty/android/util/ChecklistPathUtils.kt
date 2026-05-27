package com.jotty.android.util

import com.jotty.android.data.api.ChecklistItem

fun parentPath(path: String): String? = path.substringBeforeLast('.', missingDelimiterValue = "").ifEmpty { null }

fun appendedPath(
    items: List<ChecklistItem>,
    parentIndex: String?,
): String {
    if (parentIndex == null) return items.size.toString()
    val parent = itemAtPath(items, parentIndex) ?: return parentIndex
    return "$parentIndex.${parent.children.orEmpty().size}"
}

fun itemAtPath(
    items: List<ChecklistItem>,
    path: String,
): ChecklistItem? {
    val segments = pathSegments(path) ?: return null
    var current = items
    var node: ChecklistItem? = null
    for (segment in segments) {
        if (segment !in current.indices) return null
        node = current[segment]
        current = node.children.orEmpty()
    }
    return node
}

private fun pathSegments(path: String): List<Int>? = path.split(".").map { it.toIntOrNull() ?: return null }

/**
 * Deletes a single item at [path] from a checklist tree (positional, e.g. "0" or "0.1").
 * Returns a new list with children reindexed.
 */
fun deleteAtPath(
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
                deleteAtSegments(parent.children.orEmpty(), segments.drop(1))
                    .mapIndexed { i, item -> item.copy(index = i) }
            list[idx] = parent.copy(children = newChildren)
        }
    }
}
