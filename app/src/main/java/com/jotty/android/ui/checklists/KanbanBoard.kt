package com.jotty.android.ui.checklists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.data.api.TaskStatus
import com.jotty.android.data.api.isCompletedForApi
import com.jotty.android.data.api.effectiveColorHex
import com.jotty.android.ui.common.ConfirmDeleteDialog
import com.jotty.android.ui.common.DeleteDropdownMenuItem
import com.jotty.android.util.KanbanCard
import com.jotty.android.util.KanbanColumn
import com.jotty.android.util.kanbanCardReorderRequest

private val ColumnWidth = 280.dp
private val BoardHeightMin = 320.dp
private val BoardHeightMax = 700.dp
private val BoardHeightReserved = 200.dp

@Composable
private fun rememberKanbanBoardHeight(): Dp {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    return (screenHeight - BoardHeightReserved).coerceIn(BoardHeightMin, BoardHeightMax)
}

@Composable
fun TaskKanbanBoard(
    columns: List<KanbanColumn>,
    allStatuses: List<TaskStatus>,
    moveEnabled: Boolean,
    onMoveItem: (apiPath: String, newStatusId: String) -> Unit,
    onDeleteItem: (apiPath: String) -> Unit,
    onOpenItem: (KanbanCard) -> Unit,
    onAddToColumn: (statusId: String, text: String) -> Unit,
    onMoveCardInColumn: (columnCards: List<KanbanCard>, cardIndex: Int, up: Boolean) -> Unit,
    onReorderCardInColumn: (columnCards: List<KanbanCard>, fromIndex: Int, toIndex: Int) -> Unit,
    onUpdateTitle: (apiPath: String, text: String) -> Unit,
    reorderInColumnEnabled: Boolean = true,
    dragReorderEnabled: Boolean = true,
    showChecklistEmojis: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val boardHeight = rememberKanbanBoardHeight()
    var editingCardPath by remember { mutableStateOf<String?>(null) }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(boardHeight)
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        columns.forEach { column ->
            KanbanStatusColumn(
                column = column,
                allStatuses = allStatuses,
                moveEnabled = moveEnabled,
                onMoveItem = onMoveItem,
                onDeleteItem = onDeleteItem,
                onOpenItem = onOpenItem,
                onAddToColumn = onAddToColumn,
                onMoveCardInColumn = onMoveCardInColumn,
                onReorderCardInColumn = onReorderCardInColumn,
                onUpdateTitle = onUpdateTitle,
                reorderInColumnEnabled = reorderInColumnEnabled,
                dragReorderEnabled = dragReorderEnabled,
                showChecklistEmojis = showChecklistEmojis,
                editingCardPath = editingCardPath,
                onEditingCardPathChange = { editingCardPath = it },
            )
        }
    }
}

@Composable
private fun KanbanStatusColumn(
    column: KanbanColumn,
    allStatuses: List<TaskStatus>,
    moveEnabled: Boolean,
    onMoveItem: (apiPath: String, newStatusId: String) -> Unit,
    onDeleteItem: (apiPath: String) -> Unit,
    onOpenItem: (KanbanCard) -> Unit,
    onAddToColumn: (statusId: String, text: String) -> Unit,
    onMoveCardInColumn: (columnCards: List<KanbanCard>, cardIndex: Int, up: Boolean) -> Unit,
    onReorderCardInColumn: (columnCards: List<KanbanCard>, fromIndex: Int, toIndex: Int) -> Unit,
    onUpdateTitle: (apiPath: String, text: String) -> Unit,
    reorderInColumnEnabled: Boolean,
    dragReorderEnabled: Boolean,
    showChecklistEmojis: Boolean,
    editingCardPath: String?,
    onEditingCardPathChange: (String?) -> Unit,
) {
    var newTaskText by remember(column.status.id) { mutableStateOf("") }
    Column(
        modifier =
            Modifier
                .width(ColumnWidth)
                .fillMaxHeight()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .padding(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KanbanStatusDot(
                colorHex = column.status.effectiveColorHex(),
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = column.status.label,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = column.cards.size.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (column.cards.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.kanban_no_tasks),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val lazyListState = rememberLazyListState()
            var localCards by remember(column.status.id, column.cards) { mutableStateOf(column.cards) }
            var isDragging by remember(column.status.id) { mutableStateOf(false) }
            var dragStartCards by remember(column.status.id) { mutableStateOf<List<KanbanCard>?>(null) }
            var dragFromIndex by remember(column.status.id) { mutableStateOf(-1) }

            LaunchedEffect(column.cards, isDragging) {
                if (!isDragging) localCards = column.cards
            }

            val haptic = LocalHapticFeedback.current
            val reorderableState =
                if (dragReorderEnabled && reorderInColumnEnabled) {
                    rememberReorderableLazyListState(lazyListState) { from, to ->
                        if (from.index == to.index) return@rememberReorderableLazyListState
                        if (from.index !in localCards.indices || to.index !in localCards.indices) {
                            return@rememberReorderableLazyListState
                        }
                        localCards =
                            localCards.toMutableList().apply {
                                add(to.index, removeAt(from.index))
                            }
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    }
                } else {
                    null
                }

            @Composable
            fun KanbanCardRow(
                card: KanbanCard,
                cardIndex: Int,
                reorderableScope: ReorderableCollectionItemScope?,
            ) {
                KanbanTaskCard(
                    card = card,
                    cardIndex = cardIndex,
                    columnCards = localCards,
                    currentStatusId = column.status.id,
                    allStatuses = allStatuses,
                    moveEnabled = moveEnabled,
                    reorderInColumnEnabled = reorderInColumnEnabled,
                    dragReorderEnabled = dragReorderEnabled,
                    showChecklistEmojis = showChecklistEmojis,
                    reorderableScope = reorderableScope,
                    onDragStarted = {
                        isDragging = true
                        dragStartCards = localCards.toList()
                        dragFromIndex = cardIndex
                    },
                    onDragStopped = {
                        isDragging = false
                        val start = dragStartCards
                        val fromIdx = dragFromIndex
                        dragStartCards = null
                        dragFromIndex = -1
                        if (start == null || fromIdx < 0) return@KanbanTaskCard
                        val movedId = start[fromIdx].item.id
                        val endIdx =
                            if (movedId != null) {
                                localCards.indexOfFirst { it.item.id == movedId }
                            } else {
                                -1
                            }
                        if (endIdx < 0 || endIdx == fromIdx) {
                            localCards = start
                            return@KanbanTaskCard
                        }
                        if (kanbanCardReorderRequest(start, fromIdx, endIdx) != null) {
                            onReorderCardInColumn(start, fromIdx, endIdx)
                        } else {
                            localCards = start
                        }
                    },
                    editingCardPath = editingCardPath,
                    onEditingCardPathChange = onEditingCardPathChange,
                    onUpdateTitle = onUpdateTitle,
                    onMoveItem = onMoveItem,
                    onDeleteItem = onDeleteItem,
                    onOpenItem = onOpenItem,
                    onMoveCardInColumn = onMoveCardInColumn,
                )
            }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                itemsIndexed(localCards, key = { _, card -> "${column.status.id}-${card.index}" }) { cardIndex, card ->
                    val cardKey = "${column.status.id}-${card.index}"
                    if (reorderableState != null && card.item.id != null) {
                        ReorderableItem(state = reorderableState, key = cardKey) {
                            KanbanCardRow(card, cardIndex, this)
                        }
                    } else {
                        KanbanCardRow(card, cardIndex, reorderableScope = null)
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newTaskText,
                onValueChange = { newTaskText = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.kanban_add_task_hint)) },
            )
            IconButton(
                onClick = {
                    val trimmed = newTaskText.trim()
                    if (trimmed.isNotEmpty()) {
                        onAddToColumn(column.status.id, trimmed)
                        newTaskText = ""
                    }
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
            }
        }
    }
}

@Composable
private fun KanbanTaskCard(
    card: KanbanCard,
    cardIndex: Int,
    columnCards: List<KanbanCard>,
    currentStatusId: String,
    allStatuses: List<TaskStatus>,
    moveEnabled: Boolean,
    reorderInColumnEnabled: Boolean,
    dragReorderEnabled: Boolean,
    showChecklistEmojis: Boolean,
    reorderableScope: ReorderableCollectionItemScope?,
    onDragStarted: () -> Unit,
    onDragStopped: () -> Unit,
    editingCardPath: String?,
    onEditingCardPathChange: (String?) -> Unit,
    onUpdateTitle: (apiPath: String, text: String) -> Unit,
    onMoveItem: (apiPath: String, newStatusId: String) -> Unit,
    onDeleteItem: (apiPath: String) -> Unit,
    onOpenItem: (KanbanCard) -> Unit,
    onMoveCardInColumn: (columnCards: List<KanbanCard>, cardIndex: Int, up: Boolean) -> Unit,
) {
    var menuExpanded by remember(card.index) { mutableStateOf(false) }
    var showDeleteConfirm by remember(card.index) { mutableStateOf(false) }
    val apiPath = "${card.index}"
    val isEditingTitle = editingCardPath == apiPath
    var editTitle by remember(card.item.text, isEditingTitle) { mutableStateOf(card.item.text) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val taskLabel = card.item.text.ifBlank { stringResource(R.string.untitled) }
    val subtasks = card.item.children.orEmpty()
    val subtaskTotal = subtasks.size
    val subtaskDone = subtasks.count { it.isCompletedForApi() }
    val subtaskProgress = if (subtaskTotal > 0) subtaskDone.toFloat() / subtaskTotal else 0f
    val moveTargets = allStatuses.filterNot { it.id.equals(currentStatusId, ignoreCase = true) }

    LaunchedEffect(isEditingTitle) {
        if (isEditingTitle) {
            editTitle = card.item.text
            focusRequester.requestFocus()
        }
    }

    fun commitTitleEdit() {
        val trimmed = editTitle.trim()
        onEditingCardPathChange(null)
        focusManager.clearFocus()
        if (trimmed.isNotEmpty() && trimmed != card.item.text.trim()) {
            onUpdateTitle(apiPath, trimmed)
        }
    }

    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            message = stringResource(R.string.delete_task_named_confirm, taskLabel),
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                onDeleteItem(apiPath)
            },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (dragReorderEnabled && reorderableScope != null && card.item.id != null) {
                IconButton(
                    onClick = {},
                    modifier =
                        with(reorderableScope) {
                            Modifier
                                .size(36.dp)
                                .draggableHandle(
                                    onDragStarted = { onDragStarted() },
                                    onDragStopped = { onDragStopped() },
                                )
                        },
                ) {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = stringResource(R.string.cd_drag_to_reorder),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .then(
                            if (!isEditingTitle) {
                                Modifier.pointerInput(apiPath) {
                                    detectTapGestures(
                                        onTap = { onOpenItem(card) },
                                        onDoubleTap = { onEditingCardPathChange(apiPath) },
                                    )
                                }
                            } else {
                                Modifier
                            },
                        ),
            ) {
                if (isEditingTitle) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions =
                            KeyboardActions(
                                onDone = { commitTitleEdit() },
                            ),
                    )
                } else {
                    val displayTitle =
                        checklistDisplayText(card.item.text, showChecklistEmojis)
                            .ifBlank { stringResource(R.string.untitled) }
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (subtaskTotal > 0) {
                    LinearProgressIndicator(
                        progress = { subtaskProgress },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .height(3.dp),
                    )
                    Text(
                        text = stringResource(R.string.done_progress, subtaskDone, subtaskTotal),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_options),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEditingCardPathChange(apiPath)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.kanban_edit_details)) },
                            onClick = {
                                menuExpanded = false
                                onOpenItem(card)
                            },
                        )
                        if (reorderInColumnEnabled && cardIndex > 0) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cd_move_item_up)) },
                                leadingIcon = {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onMoveCardInColumn(columnCards, cardIndex, true)
                                },
                            )
                        }
                        if (reorderInColumnEnabled && cardIndex < columnCards.lastIndex) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cd_move_item_down)) },
                                leadingIcon = {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onMoveCardInColumn(columnCards, cardIndex, false)
                                },
                            )
                        }
                        if (moveEnabled) {
                            moveTargets.forEach { target ->
                                DropdownMenuItem(
                                    text = {
                                        KanbanMoveMenuLabel(status = target)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onMoveItem(apiPath, target.id)
                                    },
                                )
                            }
                        }
                        DeleteDropdownMenuItem(
                            onClick = {
                                menuExpanded = false
                                showDeleteConfirm = true
                            },
                        )
                    }
            }
        }
    }
}

@Composable
internal fun KanbanStatusDot(
    colorHex: String?,
    modifier: Modifier = Modifier,
) {
    val accent = parseHexColorOrNull(colorHex) ?: MaterialTheme.colorScheme.primary
    Box(
        modifier =
            modifier
                .clip(CircleShape)
                .background(accent)
                .height(10.dp)
                .width(10.dp),
    )
}

@Composable
private fun KanbanMoveMenuLabel(status: TaskStatus) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KanbanStatusDot(colorHex = status.effectiveColorHex())
        Text(
            text = stringResource(R.string.kanban_move_to, status.label),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Parses `#RRGGBB`, `#AARRGGBB`, or bare `RRGGBB` for status column accent dots. */
fun parseHexColorOrNull(hex: String?): Color? {
    val trimmed = hex?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val normalized = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    val raw = normalized.removePrefix("#")
    return runCatching {
        when (raw.length) {
            6 -> {
                val rgb = raw.toLong(16)
                Color(
                    red = ((rgb shr 16) and 0xFF) / 255f,
                    green = ((rgb shr 8) and 0xFF) / 255f,
                    blue = (rgb and 0xFF) / 255f,
                )
            }
            8 -> {
                val argb = raw.toLong(16)
                Color(
                    alpha = ((argb shr 24) and 0xFF) / 255f,
                    red = ((argb shr 16) and 0xFF) / 255f,
                    green = ((argb shr 8) and 0xFF) / 255f,
                    blue = (argb and 0xFF) / 255f,
                )
            }
            else -> null
        }
    }.getOrNull()
}
