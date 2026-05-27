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
