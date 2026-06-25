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
    val showChecklistEmojis by settingsRepository.showChecklistEmojis.collectAsStateWithLifecycle(initialValue = true)
    val kanbanHideEmptyColumns by settingsRepository.kanbanHideEmptyColumns.collectAsStateWithLifecycle(initialValue = false)
    val noteSnapshotsEnabled by settingsRepository.noteSnapshotsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val richNoteEditorEnabled by settingsRepository.richNoteEditorEnabled.collectAsStateWithLifecycle(initialValue = false)
    val visualEditorSaveAsMarkdown by settingsRepository.visualEditorSaveAsMarkdownEnabled.collectAsStateWithLifecycle(initialValue = false)
    val noteListPreviewEnabled by settingsRepository.noteListPreviewEnabled.collectAsStateWithLifecycle(initialValue = true)
    val notePreviewMaxLines by settingsRepository.notePreviewMaxLines.collectAsStateWithLifecycle(
        initialValue = SettingsRepository.DEFAULT_NOTE_PREVIEW_MAX_LINES,
    )
    val offlineModeEnabled by settingsRepository.offlineModeEnabled.collectAsStateWithLifecycle(initialValue = true)
    val openNotesInEditMode by settingsRepository.openNotesInEditMode.collectAsStateWithLifecycle(initialValue = false)
    val defaultNoteEditMode by settingsRepository.defaultNoteEditMode.collectAsStateWithLifecycle(initialValue = "markdown")
    val defaultNoteCategory by settingsRepository.defaultNoteCategory.collectAsStateWithLifecycle(initialValue = null)
    val contentPaddingMode by settingsRepository.contentPaddingMode.collectAsStateWithLifecycle(initialValue = "comfortable")
    val contentVerticalDp = if (contentPaddingMode == "compact") 8 else 16
    val cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .mainScreenTabContentPadding(topComfortDp = contentVerticalDp)
                .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        SettingsSectionTitle(stringResource(R.string.settings_category_general))
        Card(modifier = Modifier.fillMaxWidth(), colors = cardColors) {
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

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSectionTitle(stringResource(R.string.behavior_category_lists))
        Card(modifier = Modifier.fillMaxWidth(), colors = cardColors) {
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
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSectionTitle(stringResource(R.string.nav_checklists))
        Card(modifier = Modifier.fillMaxWidth(), colors = cardColors) {
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
                headlineContent = { Text(stringResource(R.string.show_checklist_emojis)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.show_checklist_emojis_description),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = showChecklistEmojis,
                        onCheckedChange = {
                            scope.launch {
                                settingsRepository.setShowChecklistEmojis(it)
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
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSectionTitle(stringResource(R.string.nav_notes))
        Card(modifier = Modifier.fillMaxWidth(), colors = cardColors) {
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
                headlineContent = { Text(stringResource(R.string.visual_editor_save_as_markdown)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.visual_editor_save_as_markdown_description),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = visualEditorSaveAsMarkdown,
                        onCheckedChange = {
                            scope.launch {
                                settingsRepository.setVisualEditorSaveAsMarkdownEnabled(it)
                            }
                        },
                        enabled = richNoteEditorEnabled,
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
            if (noteListPreviewEnabled) {
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.note_preview_lines)) },
                    supportingContent = {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Text(
                                stringResource(R.string.note_preview_lines_description),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(
                                    0 to R.string.note_preview_lines_none,
                                    1 to R.string.note_preview_lines_1,
                                    2 to R.string.note_preview_lines_2,
                                    4 to R.string.note_preview_lines_4,
                                ).forEach { (value, labelRes) ->
                                    FilterChip(
                                        selected = notePreviewMaxLines == value,
                                        onClick = {
                                            scope.launch {
                                                settingsRepository.setNotePreviewMaxLines(value)
                                            }
                                        },
                                        label = { Text(stringResource(labelRes)) },
                                    )
                                }
                            }
                        }
                    },
                )
            }
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.open_notes_in_edit_mode)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.open_notes_in_edit_mode_description),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = openNotesInEditMode,
                        onCheckedChange = {
                            scope.launch {
                                settingsRepository.setOpenNotesInEditMode(it)
                            }
                        },
                    )
                },
            )
            if (richNoteEditorEnabled) {
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.default_note_edit_mode)) },
                    supportingContent = {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Text(
                                stringResource(R.string.default_note_edit_mode_description),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(
                                    "markdown" to R.string.note_edit_mode_markdown,
                                    "visual" to R.string.note_edit_mode_visual,
                                ).forEach { (value, labelRes) ->
                                    FilterChip(
                                        selected = defaultNoteEditMode == value,
                                        onClick = {
                                            scope.launch {
                                                settingsRepository.setDefaultNoteEditMode(value)
                                            }
                                        },
                                        label = { Text(stringResource(labelRes)) },
                                    )
                                }
                            }
                        }
                    },
                )
            }
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.default_note_category)) },
                supportingContent = {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text(
                            stringResource(R.string.default_note_category_description),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(
                            value = defaultNoteCategory.orEmpty(),
                            onValueChange = { newValue ->
                                scope.launch {
                                    settingsRepository.setDefaultNoteCategory(newValue)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.category)) },
                        )
                    }
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.note_snapshots)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.note_snapshots_description),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = noteSnapshotsEnabled,
                        onCheckedChange = {
                            scope.launch {
                                settingsRepository.setNoteSnapshotsEnabled(it)
                            }
                        },
                    )
                },
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
