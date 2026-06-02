package com.jotty.android.ui.checklists

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val TODO_HEADER_LAZY_INDEX = 0

@Composable
fun ChecklistDetailItemsList(
    treeItems: List<ChecklistItem>,
    toDo: List<ChecklistFlatItem>,
    completed: List<ChecklistFlatItem>,
    doneCount: Int,
    total: Int,
    modifier: Modifier = Modifier,
    onReorder: (ReorderItemsRequest) -> Unit,
    itemRow: @Composable (
        flat: ChecklistFlatItem,
        reorderableScope: ReorderableCollectionItemScope?,
        isDragging: Boolean,
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

    var localToDo by remember { mutableStateOf(toDo) }
    var localCompleted by remember { mutableStateOf(completed) }
    androidx.compose.runtime.LaunchedEffect(toDo) { localToDo = toDo }
    androidx.compose.runtime.LaunchedEffect(completed) { localCompleted = completed }

    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val completedHeaderIndex = 1 + localToDo.size

    val reorderableState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            if (from.index == to.index) return@rememberReorderableLazyListState

            val fromSection = lazyIndexSection(from.index, localToDo.size, localCompleted.size)
            val toSection = lazyIndexSection(to.index, localToDo.size, localCompleted.size)
            if (fromSection == null || toSection == null || fromSection != toSection) return@rememberReorderableLazyListState

            val request =
                when (fromSection) {
                    ChecklistSection.ToDo -> {
                        val fromIdx = from.index - 1
                        val toIdx = to.index - 1
                        reorderRequestForFlatMove(
                            treeItems = treeItems,
                            sectionItems = localToDo.map { it.item },
                            fromIndex = fromIdx,
                            toIndex = toIdx,
                        )?.also {
                            localToDo =
                                localToDo.toMutableList().apply {
                                    add(toIdx, removeAt(fromIdx))
                                }
                        }
                    }
                    ChecklistSection.Completed -> {
                        val fromIdx = from.index - completedHeaderIndex - 1
                        val toIdx = to.index - completedHeaderIndex - 1
                        reorderRequestForFlatMove(
                            treeItems = treeItems,
                            sectionItems = localCompleted.map { it.item },
                            fromIndex = fromIdx,
                            toIndex = toIdx,
                        )?.also {
                            localCompleted =
                                localCompleted.toMutableList().apply {
                                    add(toIdx, removeAt(fromIdx))
                                }
                        }
                    }
                }

            if (request != null) {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                onReorder(request)
            }
        }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "header-todo") {
            ChecklistSectionHeader(title = stringResource(R.string.section_to_do, localToDo.size))
        }
        items(localToDo, key = { "todo-${it.apiPath}-${it.item.id ?: it.item.text}" }) { flat ->
            ReorderableItem(
                state = reorderableState,
                key = "todo-${flat.apiPath}-${flat.item.id ?: flat.item.text}",
                enabled = flat.item.id != null,
            ) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp, label = "dragElevation")
                Surface(shadowElevation = elevation) {
                    itemRow(flat, this, isDragging)
                }
            }
        }
        item(key = "header-completed") {
            ChecklistSectionHeader(title = stringResource(R.string.section_completed, localCompleted.size))
        }
        items(localCompleted, key = { "done-${it.apiPath}-${it.item.id ?: it.item.text}" }) { flat ->
            ReorderableItem(
                state = reorderableState,
                key = "done-${flat.apiPath}-${flat.item.id ?: flat.item.text}",
                enabled = flat.item.id != null,
            ) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp, label = "dragElevation")
                Surface(shadowElevation = elevation) {
                    itemRow(flat, this, isDragging)
                }
            }
        }
    }
}

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
