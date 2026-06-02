package com.jotty.android.ui.checklists

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableCollectionItemScope

/** Checklist detail row with optional drag handle when the item has a server id. */
@Composable
fun ChecklistDetailItemRow(
    flat: ChecklistFlatItem,
    editingItemKey: String?,
    onEditingItemKeyChange: (String?) -> Unit,
    isProject: Boolean,
    reorderableScope: ReorderableCollectionItemScope?,
    onDragStopped: (() -> Unit)?,
    onCheck: () -> Unit,
    onUncheck: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (String) -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onAddSubItem: (() -> Unit)?,
    actionIconSize: Dp = 48.dp,
    actionGlyphSize: Dp = 22.dp,
) {
    ChecklistItemRow(
        item = flat.item,
        itemKey = flat.apiPath,
        editingItemKey = editingItemKey,
        onEditingItemKeyChange = onEditingItemKeyChange,
        depth = flat.depth,
        isProject = isProject,
        onCheck = onCheck,
        onUncheck = onUncheck,
        onDelete = onDelete,
        onUpdate = onUpdate,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
        onAddSubItem = onAddSubItem,
        reorderableScope = if (flat.item.id != null) reorderableScope else null,
        onDragStopped = onDragStopped,
        actionIconSize = actionIconSize,
        actionGlyphSize = actionGlyphSize,
    )
}
