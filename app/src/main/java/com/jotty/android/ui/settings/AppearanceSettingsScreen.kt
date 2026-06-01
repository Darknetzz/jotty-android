package com.jotty.android.ui.settings

import android.os.Build
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
fun AppearanceSettingsScreen(settingsRepository: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val themeMode by settingsRepository.themeMode.collectAsStateWithLifecycle(initialValue = null)
    val themeColor by settingsRepository.themeColor.collectAsStateWithLifecycle(initialValue = "default")
    val readerTextScale by settingsRepository.readerTextScale.collectAsStateWithLifecycle(initialValue = 1.0f)
    val contentPaddingMode by settingsRepository.contentPaddingMode.collectAsStateWithLifecycle(initialValue = "comfortable")
    val reducedMotionMode by settingsRepository.reducedMotionMode.collectAsStateWithLifecycle(initialValue = null)
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
                headlineContent = { Text(stringResource(R.string.theme_mode_label)) },
                supportingContent = {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            null to R.string.theme_system,
                            "light" to R.string.theme_light,
                            "dark" to R.string.theme_dark,
                        ).forEach { (value, labelRes) ->
                            val isSelected =
                                when (value) {
                                    null -> themeMode.isNullOrBlank()
                                    else -> themeMode == value
                                }
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    scope.launch {
                                        settingsRepository.setThemeMode(value)
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
                headlineContent = { Text(stringResource(R.string.theme_color_label)) },
                supportingContent = {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        buildList {
                            add("default" to R.string.theme_color_default)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                add("dynamic" to R.string.theme_dynamic)
                            }
                            add("amoled" to R.string.theme_amoled)
                            add("sepia" to R.string.theme_sepia)
                            add("midnight" to R.string.theme_midnight)
                            add("rose" to R.string.theme_rose)
                            add("ocean" to R.string.theme_ocean)
                            add("forest" to R.string.theme_forest)
                        }.forEach { (value, labelRes) ->
                            FilterChip(
                                selected = themeColor == value,
                                onClick = {
                                    scope.launch {
                                        settingsRepository.setThemeColor(value)
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
                headlineContent = { Text(stringResource(R.string.content_padding)) },
                supportingContent = {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            "comfortable" to R.string.content_padding_comfortable,
                            "compact" to R.string.content_padding_compact,
                        ).forEach { (value, labelRes) ->
                            FilterChip(
                                selected = contentPaddingMode == value,
                                onClick = {
                                    scope.launch {
                                        settingsRepository.setContentPaddingMode(value)
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
                headlineContent = { Text(stringResource(R.string.reader_text_size)) },
                supportingContent = {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            0.85f to R.string.text_size_small,
                            1.0f to R.string.text_size_medium,
                            1.15f to R.string.text_size_large,
                            1.3f to R.string.text_size_xlarge,
                        ).forEach { (value, labelRes) ->
                            FilterChip(
                                selected = readerTextScale == value,
                                onClick = {
                                    scope.launch {
                                        settingsRepository.setReaderTextScale(value)
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
                headlineContent = { Text(stringResource(R.string.motion_effects_label)) },
                supportingContent = {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text(
                            stringResource(R.string.motion_effects_description),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                null to R.string.motion_effects_off,
                                "off" to R.string.motion_effects_on,
                                "system" to R.string.motion_effects_system,
                            ).forEach { (storedValue, labelRes) ->
                                val isSelected =
                                    when (storedValue) {
                                        null ->
                                            reducedMotionMode.isNullOrBlank() || reducedMotionMode == "on"
                                        "off" -> reducedMotionMode == "off"
                                        "system" -> reducedMotionMode == "system"
                                        else -> false
                                    }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        scope.launch {
                                            settingsRepository.setReducedMotionMode(storedValue)
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
        Spacer(modifier = Modifier.height(32.dp))
    }
}
