package com.jotty.android.ui.checklists

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jotty.android.R

fun isProjectChecklistType(type: String?): Boolean {
    val normalized = type?.trim()?.lowercase().orEmpty()
    if (normalized.isBlank()) return false
    return when (normalized) {
        "simple", "regular", "checklist", "list" -> false
        "project", "task", "kanban" -> true
        else -> true
    }
}

@Composable
fun ChecklistTypeBadge(type: String?) {
    val labelRes =
        if (isProjectChecklistType(type)) {
            R.string.checklist_type_project_kanban
        } else {
            R.string.checklist_type_normal
        }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
