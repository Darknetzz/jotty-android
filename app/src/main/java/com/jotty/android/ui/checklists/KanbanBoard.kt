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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.data.api.TaskStatus
import com.jotty.android.data.api.effectiveColorHex
import com.jotty.android.ui.common.ConfirmDeleteDialog
import com.jotty.android.ui.common.DeleteDropdownMenuItem
import com.jotty.android.util.KanbanCard
import com.jotty.android.util.KanbanColumn

private val ColumnWidth = 280.dp
private val BoardHeight = 420.dp

@Composable
fun TaskKanbanBoard(
    columns: List<KanbanColumn>,
    allStatuses: List<TaskStatus>,
    moveEnabled: Boolean,
    onMoveItem: (apiPath: String, newStatusId: String) -> Unit,
    onDeleteItem: (apiPath: String) -> Unit,
    onOpenItem: (KanbanCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(BoardHeight)
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
) {
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(column.cards, key = { "${column.status.id}-${it.index}" }) { card ->
                    KanbanTaskCard(
                        card = card,
                        currentStatusId = column.status.id,
                        allStatuses = allStatuses,
                        moveEnabled = moveEnabled,
                        onMoveItem = onMoveItem,
                        onDeleteItem = onDeleteItem,
                        onOpenItem = onOpenItem,
                    )
                }
            }
        }
    }
}

@Composable
private fun KanbanTaskCard(
    card: KanbanCard,
    currentStatusId: String,
    allStatuses: List<TaskStatus>,
    moveEnabled: Boolean,
    onMoveItem: (apiPath: String, newStatusId: String) -> Unit,
    onDeleteItem: (apiPath: String) -> Unit,
    onOpenItem: (KanbanCard) -> Unit,
) {
    var menuExpanded by remember(card.index) { mutableStateOf(false) }
    var showDeleteConfirm by remember(card.index) { mutableStateOf(false) }
    val apiPath = "${card.index}"
    val taskLabel = card.item.text.ifBlank { stringResource(R.string.untitled) }
    val subtaskCount = card.item.children.orEmpty().size
    val moveTargets = allStatuses.filterNot { it.id.equals(currentStatusId, ignoreCase = true) }

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
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onOpenItem(card) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.item.text.ifBlank { stringResource(R.string.untitled) },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtaskCount > 0) {
                    Text(
                        text = stringResource(R.string.kanban_subtasks_count, subtaskCount),
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
                            onClick = {
                                menuExpanded = false
                                onOpenItem(card)
                            },
                        )
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
private fun KanbanStatusDot(
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
