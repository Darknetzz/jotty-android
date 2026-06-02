package com.jotty.android.util

import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.ReorderItemsRequest

private data class ItemLocation(
    val item: ChecklistItem,
    val siblings: MutableList<ChecklistItem>,
    val index: Int,
)

/**
 * Reorders checklist items in-memory using the same semantics as the Jotty REST reorder endpoint.
 */
fun reorderChecklistItems(
    items: List<ChecklistItem>,
    activeItemId: String,
    overItemId: String,
    position: String = "before",
    isDropInto: Boolean = false,
): List<ChecklistItem> {
    if (activeItemId == overItemId) return items
    if (isDescendantOf(items, activeItemId, overItemId)) return items

    val cloned = cloneChecklistItems(items)
    val activeInfo = findItemLocation(cloned, activeItemId) ?: return items
    val overInfo = findItemLocation(cloned, overItemId) ?: return items

    activeInfo.siblings.removeAt(activeInfo.index)

    if (isDropInto) {
        val children = overInfo.item.children?.toMutableList() ?: mutableListOf()
        children.add(activeInfo.item)
        overInfo.siblings[overInfo.index] = overInfo.item.copy(children = children)
    } else {
        val targetSiblings = overInfo.siblings
        var newIndex = targetSiblings.indexOfFirst { it.id == overItemId }.coerceAtLeast(0)
        if (position == "after") newIndex += 1
        targetSiblings.add(newIndex, activeInfo.item)
    }

    return reindexChecklistItems(cloned)
}

/** Returns a reorder request to move [itemId] up among its siblings, or null if already first. */
fun moveChecklistItemUpRequest(
    items: List<ChecklistItem>,
    itemId: String,
): ReorderItemsRequest? {
    val location = findItemLocation(items.toMutableList(), itemId) ?: return null
    if (location.index <= 0) return null
    val overId = location.siblings[location.index - 1].id ?: return null
    return ReorderItemsRequest(activeItemId = itemId, overItemId = overId, position = "before")
}

/** True when [id1] and [id2] share the same parent in [items]. */
fun areSiblingChecklistItems(
    items: List<ChecklistItem>,
    id1: String,
    id2: String,
): Boolean {
    if (id1 == id2) return false
    val cloned = cloneChecklistItems(items)
    val loc1 = findItemLocation(cloned, id1) ?: return false
    val loc2 = findItemLocation(cloned, id2) ?: return false
    return loc1.siblings === loc2.siblings
}

/**
 * Builds a reorder request after a drag within a flat section list, or null when the move is invalid
 * (e.g. across different sibling groups or into an illegal position).
 */
fun reorderRequestForFlatMove(
    treeItems: List<ChecklistItem>,
    sectionItems: List<ChecklistItem>,
    fromIndex: Int,
    toIndex: Int,
): ReorderItemsRequest? {
    if (fromIndex == toIndex) return null
    if (fromIndex !in sectionItems.indices || toIndex !in sectionItems.indices) return null

    val activeId = sectionItems[fromIndex].id ?: return null
    val reordered =
        sectionItems.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
    val newIndex = reordered.indexOfFirst { it.id == activeId }
    if (newIndex < 0) return null

    val overIndex =
        when {
            fromIndex < toIndex -> newIndex - 1
            else -> newIndex + 1
        }
    if (overIndex !in reordered.indices) return null

    val overId = reordered[overIndex].id ?: return null
    if (!areSiblingChecklistItems(treeItems, activeId, overId)) return null
    if (isDescendantOf(treeItems, activeId, overId)) return null

    val position = if (fromIndex < toIndex) "after" else "before"
    return ReorderItemsRequest(activeItemId = activeId, overItemId = overId, position = position)
}

/** Returns a reorder request to move [itemId] down among its siblings, or null if already last. */
fun moveChecklistItemDownRequest(
    items: List<ChecklistItem>,
    itemId: String,
): ReorderItemsRequest? {
    val location = findItemLocation(items.toMutableList(), itemId) ?: return null
    if (location.index >= location.siblings.lastIndex) return null
    val overId = location.siblings[location.index + 1].id ?: return null
    return ReorderItemsRequest(activeItemId = itemId, overItemId = overId, position = "after")
}

private fun cloneChecklistItems(items: List<ChecklistItem>): MutableList<ChecklistItem> =
    items.map { item ->
        item.copy(
            children = item.children?.let { cloneChecklistItems(it) },
        )
    }.toMutableList()

private fun findItemLocation(
    items: MutableList<ChecklistItem>,
    targetId: String,
): ItemLocation? {
    for (index in items.indices) {
        val item = items[index]
        if (item.id == targetId) {
            return ItemLocation(item, items, index)
        }
        val children = item.children?.toMutableList()
        if (children != null) {
            findItemLocation(children, targetId)?.let { return it }
        }
    }
    return null
}

private fun isDescendantOf(
    items: List<ChecklistItem>,
    ancestorId: String,
    descendantId: String,
): Boolean {
    val ancestor = findItemById(items, ancestorId) ?: return false
    return containsDescendant(ancestor, descendantId)
}

private fun findItemById(
    items: List<ChecklistItem>,
    id: String,
): ChecklistItem? {
    for (item in items) {
        if (item.id == id) return item
        item.children?.let { findItemById(it, id) }?.let { return it }
    }
    return null
}

private fun containsDescendant(
    item: ChecklistItem,
    targetId: String,
): Boolean {
    for (child in item.children.orEmpty()) {
        if (child.id == targetId) return true
        if (containsDescendant(child, targetId)) return true
    }
    return false
}

private fun reindexChecklistItems(items: List<ChecklistItem>): List<ChecklistItem> =
    items.mapIndexed { index, item ->
        item.copy(
            index = index,
            children = item.children?.let { reindexChecklistItems(it) },
        )
    }
