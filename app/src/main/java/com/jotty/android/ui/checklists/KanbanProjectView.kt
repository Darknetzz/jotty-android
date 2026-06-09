package com.jotty.android.ui.checklists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jotty.android.R

/** Project checklist detail: Kanban board or tree/list view. */
enum class KanbanProjectView {
    Board,
    List,
}

@Composable
fun KanbanProjectViewToggle(
    view: KanbanProjectView,
    onViewChange: (KanbanProjectView) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = view == KanbanProjectView.Board,
            onClick = { onViewChange(KanbanProjectView.Board) },
            label = { Text(stringResource(R.string.kanban_view_board)) },
        )
        FilterChip(
            selected = view == KanbanProjectView.List,
            onClick = { onViewChange(KanbanProjectView.List) },
            label = { Text(stringResource(R.string.kanban_view_list)) },
        )
    }
}
