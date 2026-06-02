package com.jotty.android.ui.checklists

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.ReorderItemsRequest
import com.jotty.android.util.reorderRequestForFlatMove
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val TODO_HEADER_LAZY_INDEX = 0

private data class ChecklistDragSession(
    val section: ChecklistSection,
    val itemId: String,
    val startItems: List<ChecklistFlatItem>,
)

@Composable
fun ChecklistDetailItemsList(
    treeItems: List<ChecklistItem>,
    toDo: List<ChecklistFlatItem>,
    completed: List<ChecklistFlatItem>,
    doneCount: Int,
    total: Int,
    modifier: Modifier = Modifier,
    dragReorderEnabled: Boolean = true,
    onReorder: (ReorderItemsRequest) -> Unit,
    itemRow: @Composable (
        flat: ChecklistFlatItem,
        reorderableScope: ReorderableCollectionItemScope?,
        isDragging: Boolean,
        onDragStarted: (() -> Unit)?,
        onDragStopped: (() -> Unit)?,
    ) -> Unit,
) {
    if (total > 0) {
        Text(
            text = stringResource(R.string.done_progress, doneCount, total),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }

    if (dragReorderEnabled) {
        ChecklistDetailItemsListReorderable(
            treeItems = treeItems,
            toDo = toDo,
            completed = completed,
            modifier = modifier,
            onReorder = onReorder,
            itemRow = itemRow,
        )
    } else {
        ChecklistDetailItemsListStatic(
            toDo = toDo,
            completed = completed,
            modifier = modifier,
            itemRow = itemRow,
        )
    }
}

@Composable
private fun ChecklistDetailItemsListStatic(
    toDo: List<ChecklistFlatItem>,
    completed: List<ChecklistFlatItem>,
    modifier: Modifier,
    itemRow: @Composable (ChecklistFlatItem, ReorderableCollectionItemScope?, Boolean, (() -> Unit)?, (() -> Unit)?) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        checklistDetailItemRows(
            toDo = toDo,
            completed = completed,
            dragReorderEnabled = false,
            itemRow = itemRow,
        )
    }
}

@Composable
private fun ChecklistDetailItemsListReorderable(
    treeItems: List<ChecklistItem>,
    toDo: List<ChecklistFlatItem>,
    completed: List<ChecklistFlatItem>,
    modifier: Modifier,
    onReorder: (ReorderItemsRequest) -> Unit,
    itemRow: @Composable (ChecklistFlatItem, ReorderableCollectionItemScope?, Boolean, (() -> Unit)?, (() -> Unit)?) -> Unit,
) {
    var localToDo by remember { mutableStateOf(toDo) }
    var localCompleted by remember { mutableStateOf(completed) }
    var isDragging by remember { mutableStateOf(false) }
    var dragSession by remember { mutableStateOf<ChecklistDragSession?>(null) }

    LaunchedEffect(toDo, isDragging) {
        if (!isDragging) localToDo = toDo
    }
    LaunchedEffect(completed, isDragging) {
        if (!isDragging) localCompleted = completed
    }

    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current

    fun startDrag(
        section: ChecklistSection,
        flat: ChecklistFlatItem,
    ) {
        val itemId = flat.item.id ?: return
        isDragging = true
        dragSession =
            ChecklistDragSession(
                section = section,
                itemId = itemId,
                startItems =
                    when (section) {
                        ChecklistSection.ToDo -> localToDo
                        ChecklistSection.Completed -> localCompleted
                    },
            )
    }

    fun restoreDragSession(session: ChecklistDragSession) {
        when (session.section) {
            ChecklistSection.ToDo -> localToDo = session.startItems
            ChecklistSection.Completed -> localCompleted = session.startItems
        }
    }

    fun finishDrag() {
        val session = dragSession
        isDragging = false
        dragSession = null
        if (session == null) return

        val endItems =
            when (session.section) {
                ChecklistSection.ToDo -> localToDo
                ChecklistSection.Completed -> localCompleted
            }
        val startIdx = session.startItems.indexOfFirst { it.item.id == session.itemId }
        val endIdx = endItems.indexOfFirst { it.item.id == session.itemId }
        if (startIdx < 0 || endIdx < 0) {
            restoreDragSession(session)
            return
        }

        val request =
            reorderRequestForFlatMove(
                treeItems = treeItems,
                sectionItems = session.startItems.map { it.item },
                fromIndex = startIdx,
                toIndex = endIdx,
            )

        if (request != null) {
            onReorder(request)
        } else {
            restoreDragSession(session)
        }
    }

    val reorderableState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            if (from.index == to.index) return@rememberReorderableLazyListState

            val fromSection = lazyIndexSection(from.index, localToDo.size, localCompleted.size)
            val toSection = lazyIndexSection(to.index, localToDo.size, localCompleted.size)
            if (fromSection == null || toSection == null || fromSection != toSection) return@rememberReorderableLazyListState

            when (fromSection) {
                ChecklistSection.ToDo -> {
                    val fromIdx = from.index - 1
                    val toIdx = to.index - 1
                    if (fromIdx !in localToDo.indices || toIdx !in localToDo.indices) return@rememberReorderableLazyListState
                    localToDo =
                        localToDo.toMutableList().apply {
                            add(toIdx, removeAt(fromIdx))
                        }
                }
                ChecklistSection.Completed -> {
                    val completedHeaderIndex = 1 + localToDo.size
                    val fromIdx = from.index - completedHeaderIndex - 1
                    val toIdx = to.index - completedHeaderIndex - 1
                    if (fromIdx !in localCompleted.indices || toIdx !in localCompleted.indices) {
                        return@rememberReorderableLazyListState
                    }
                    localCompleted =
                        localCompleted.toMutableList().apply {
                            add(toIdx, removeAt(fromIdx))
                        }
                }
            }
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        checklistDetailItemRows(
            toDo = localToDo,
            completed = localCompleted,
            dragReorderEnabled = true,
            reorderableState = reorderableState,
            itemRow = itemRow,
            onItemDragStarted = ::startDrag,
            onItemDragStopped = ::finishDrag,
        )
    }
}

private fun LazyListScope.checklistDetailItemRows(
    toDo: List<ChecklistFlatItem>,
    completed: List<ChecklistFlatItem>,
    dragReorderEnabled: Boolean,
    reorderableState: ReorderableLazyListState? = null,
    itemRow: @Composable (ChecklistFlatItem, ReorderableCollectionItemScope?, Boolean, (() -> Unit)?, (() -> Unit)?) -> Unit,
    onItemDragStarted: ((ChecklistSection, ChecklistFlatItem) -> Unit)? = null,
    onItemDragStopped: (() -> Unit)? = null,
) {
    item(key = "header-todo") {
        ChecklistSectionHeader(title = stringResource(R.string.section_to_do, toDo.size))
    }
    items(
        items = toDo,
        key = { flat -> reorderableItemKey("todo", flat) },
    ) { flat ->
        if (dragReorderEnabled && reorderableState != null && flat.item.id != null) {
            ReorderableItem(
                state = reorderableState,
                key = reorderableItemKey("todo", flat),
            ) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp, label = "dragElevation")
                Surface(shadowElevation = elevation) {
                    itemRow(
                        flat,
                        this,
                        isDragging,
                        onDragStarted = { onItemDragStarted?.invoke(ChecklistSection.ToDo, flat) },
                        onDragStopped = { onItemDragStopped?.invoke() },
                    )
                }
            }
        } else {
            itemRow(flat, null, false, null, null)
        }
    }
    item(key = "header-completed") {
        ChecklistSectionHeader(title = stringResource(R.string.section_completed, completed.size))
    }
    items(
        items = completed,
        key = { flat -> reorderableItemKey("done", flat) },
    ) { flat ->
        if (dragReorderEnabled && reorderableState != null && flat.item.id != null) {
            ReorderableItem(
                state = reorderableState,
                key = reorderableItemKey("done", flat),
            ) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp, label = "dragElevation")
                Surface(shadowElevation = elevation) {
                    itemRow(
                        flat,
                        this,
                        isDragging,
                        onDragStarted = { onItemDragStarted?.invoke(ChecklistSection.Completed, flat) },
                        onDragStopped = { onItemDragStopped?.invoke() },
                    )
                }
            }
        } else {
            itemRow(flat, null, false, null, null)
        }
    }
}

private fun reorderableItemKey(
    prefix: String,
    flat: ChecklistFlatItem,
): String = flat.item.id?.let { "$prefix-$it" } ?: "$prefix-${flat.apiPath}-${flat.item.text}"

@Composable
private fun ChecklistSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
    )
}

private enum class ChecklistSection {
    ToDo,
    Completed,
}

private fun lazyIndexSection(
    lazyIndex: Int,
    toDoCount: Int,
    completedCount: Int,
): ChecklistSection? {
    val completedHeaderIndex = 1 + toDoCount
    return when {
        lazyIndex == TODO_HEADER_LAZY_INDEX || lazyIndex == completedHeaderIndex -> null
        lazyIndex in 1..toDoCount -> ChecklistSection.ToDo
        lazyIndex > completedHeaderIndex && lazyIndex <= completedHeaderIndex + completedCount ->
            ChecklistSection.Completed
        else -> null
    }
}
