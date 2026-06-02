package com.jotty.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.ui.theme.normalizeThemeAccentHex
import com.jotty.android.ui.theme.parseThemeAccentHex
import com.jotty.android.ui.theme.resolveCustomAccentColor

/** Preset swatches offered for quick custom accent selection. */
internal val CUSTOM_ACCENT_PRESETS =
    listOf(
        "#6366F1",
        "#2563EB",
        "#7C3AED",
        "#DB2777",
        "#B91C3C",
        "#EA580C",
        "#D97706",
        "#166534",
        "#0E7490",
        "#14B8A6",
        "#71717A",
        "#0F172A",
    )

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CustomThemeColorSection(
    accentHex: String,
    tintedBackgrounds: Boolean,
    onAccentHexChange: (String) -> Unit,
    onTintedBackgroundsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hexInput by remember(accentHex) { mutableStateOf(accentHex) }
    val parsedAccent = remember(accentHex) { resolveCustomAccentColor(accentHex) }
    val hexError =
        hexInput.isNotBlank() &&
            hexInput.length >= 3 &&
            normalizeThemeAccentHex(hexInput) == null

    Column(
        modifier = modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.theme_custom_pick_color),
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CUSTOM_ACCENT_PRESETS.forEach { preset ->
                val color = parseThemeAccentHex(preset) ?: return@forEach
                val selected = accentHex.equals(preset, ignoreCase = true)
                AccentSwatch(
                    color = color,
                    selected = selected,
                    onClick = {
                        hexInput = preset
                        onAccentHexChange(preset)
                    },
                )
            }
        }
        OutlinedTextField(
            value = hexInput,
            onValueChange = { input ->
                hexInput = input
                normalizeThemeAccentHex(input)?.let { onAccentHexChange(it) }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.theme_custom_hex_label)) },
            placeholder = { Text(stringResource(R.string.theme_custom_hex_hint)) },
            singleLine = true,
            isError = hexError,
            supportingText = {
                if (hexError) {
                    Text(stringResource(R.string.theme_custom_hex_invalid))
                } else {
                    Text(stringResource(R.string.theme_custom_hex_help))
                }
            },
            leadingIcon = {
                Box(
                    modifier =
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(parsedAccent),
                )
            },
        )
        Text(
            stringResource(R.string.theme_custom_background_style),
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !tintedBackgrounds,
                onClick = { onTintedBackgroundsChange(false) },
                label = { Text(stringResource(R.string.theme_custom_background_neutral)) },
            )
            FilterChip(
                selected = tintedBackgrounds,
                onClick = { onTintedBackgroundsChange(true) },
                label = { Text(stringResource(R.string.theme_custom_background_tinted)) },
            )
        }
    }
}

@Composable
private fun AccentSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
    Box(
        modifier =
            modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color)
                .border(width = if (selected) 3.dp else 1.dp, color = borderColor, shape = CircleShape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {}
}
