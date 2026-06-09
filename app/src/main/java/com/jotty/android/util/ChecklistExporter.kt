package com.jotty.android.util

import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.TaskStatus
import com.jotty.android.data.api.isCompletedForApi
import com.jotty.android.ui.checklists.flattenChecklistItems
import com.jotty.android.ui.checklists.isProjectChecklistType

/** Plain-text export for sharing a checklist or Kanban board. */
fun exportChecklistAsPlainText(
    checklist: Checklist,
    taskStatuses: List<TaskStatus> = emptyList(),
): String {
    val title = checklist.title.ifBlank { "Untitled" }
    val builder = StringBuilder("# $title\n\n")
    if (isProjectChecklistType(checklist.type) && taskStatuses.isNotEmpty()) {
        val columns = buildKanbanColumns(checklist.items, taskStatuses)
        columns.forEach { column ->
            builder.append("## ${column.status.label}\n")
            if (column.cards.isEmpty()) {
                builder.append("- (empty)\n")
            } else {
                column.cards.forEach { card ->
                    appendItemLine(builder, card.item, indent = 0)
                }
            }
            builder.append('\n')
        }
    } else {
        flattenChecklistItems(checklist.items).forEach { flat ->
            val indent = "  ".repeat(flat.depth)
            val mark = if (flat.item.isCompletedForApi()) "x" else " "
            builder.append("$indent- [$mark] ${flat.item.text}\n")
        }
    }
    return builder.toString().trimEnd()
}

private fun appendItemLine(
    builder: StringBuilder,
    item: ChecklistItem,
    indent: Int,
) {
    val prefix = "  ".repeat(indent)
    val mark = if (item.isCompletedForApi()) "x" else " "
    builder.append("$prefix- [$mark] ${item.text.ifBlank { "Untitled" }}\n")
    item.children.orEmpty().forEachIndexed { _, child ->
        appendItemLine(builder, child, indent + 1)
    }
}
