package com.jotty.android.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jotty.android.R
import com.jotty.android.data.preferences.SettingsRepository
import com.jotty.android.ui.common.mainScreenTabContentPadding
import kotlinx.coroutines.launch

@Composable
fun BehaviorSettingsScreen(settingsRepository: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val startTab by settingsRepository.startTab.collectAsStateWithLifecycle(initialValue = null)
    val swipeToDeleteEnabled by settingsRepository.swipeToDeleteEnabled.collectAsStateWithLifecycle(initialValue = false)
    val checklistDragReorderEnabled by settingsRepository.checklistDragReorderEnabled.collectAsStateWithLifecycle(initialValue = true)
    val kanbanHideEmptyColumns by settingsRepository.kanbanHideEmptyColumns.collectAsStateWithLifecycle(initialValue = false)
    val richNoteEditorEnabled by settingsRepository.richNoteEditorEnabled.collectAsStateWithLifecycle(initialValue = false)
    val noteListPreviewEnabled by settingsRepository.noteListPreviewEnabled.collectAsStateWithLifecycle(initialValue = true)
    val offlineModeEnabled by settingsRepository.offlineModeEnabled.collectAsStateWithLifecycle(initialValue = true)
    val contentPaddingMode by settingsRepository.contentPaddingMode.collectAsStateWithLifecycle(initialValue = "comfortable")
    val contentVerticalDp = if (contentPaddingMode == "compact") 8 else 16

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .mainScreenTabContentPadding(topComfortDp = contentVerticalDp)
                .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.start_screen)) },
                supportingContent = {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            "checklists" to R.string.nav_checklists,
                            "notes" to R.string.nav_notes,
                            "settings" to R.string.nav_settings,
                        ).forEach { (value, labelRes) ->
                            FilterChip(
                                selected = (startTab ?: "checklists") == value,
                                onClick = {
                                    scope.launch {
                                        settingsRepository.setStartTab(value)
                                    }
                                },
                                label = { Text(stringResource(labelRes)) },
                            )
                        }
                    }
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.swipe_to_delete)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.swipe_to_delete_description),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = swipeToDeleteEnabled,
                        onCheckedChange = {
                            scope.launch {
                                settingsRepository.setSwipeToDeleteEnabled(it)
                            }
                        },
                    )
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.checklist_drag_reorder)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.checklist_drag_reorder_description),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = checklistDragReorderEnabled,
                        onCheckedChange = {
                            scope.launch {
                                settingsRepository.setChecklistDragReorderEnabled(it)
                            }
                        },
                    )
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.kanban_hide_empty_columns)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.kanban_hide_empty_columns_description),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = kanbanHideEmptyColumns,
                        onCheckedChange = {
                            scope.launch {
                                settingsRepository.setKanbanHideEmptyColumns(it)
                            }
                        },
                    )
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.rich_note_editor)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.rich_note_editor_description),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = richNoteEditorEnabled,
                        onCheckedChange = {
                            scope.launch {
                                settingsRepository.setRichNoteEditorEnabled(it)
                            }
                        },
                    )
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.note_list_preview)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.note_list_preview_description),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = noteListPreviewEnabled,
                        onCheckedChange = {
                            scope.launch {
                                settingsRepository.setNoteListPreviewEnabled(it)
                            }
                        },
                    )
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.offline_mode)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.offline_mode_description),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = offlineModeEnabled,
                        onCheckedChange = {
                            scope.launch {
                                settingsRepository.setOfflineModeEnabled(it)
                            }
                        },
                    )
                },
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}
