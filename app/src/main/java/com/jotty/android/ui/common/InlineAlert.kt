package com.jotty.android.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class InlineAlertVariant {
    Info,
    Success,
    Danger,
    Loading,
}

/** Bootstrap-style inline alert (tinted background, optional border, icon + message). */
@Composable
fun InlineAlert(
    message: String,
    variant: InlineAlertVariant,
    modifier: Modifier = Modifier,
) {
    val (containerColor, contentColor, borderColor) = inlineAlertColors(variant)
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    borderColor?.let { color ->
                        Modifier.border(1.dp, color, RoundedCornerShape(8.dp))
                    } ?: Modifier,
                ),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (variant) {
                InlineAlertVariant.Loading ->
                    CircularProgressIndicator(
                        modifier =
                            Modifier
                                .size(20.dp)
                                .semantics { contentDescription = message },
                        strokeWidth = 2.dp,
                        color = contentColor,
                        trackColor = contentColor.copy(alpha = 0.3f),
                    )
                InlineAlertVariant.Info ->
                    Icon(
                        Icons.Default.NewReleases,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = contentColor,
                    )
                InlineAlertVariant.Success ->
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = contentColor,
                    )
                InlineAlertVariant.Danger ->
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = contentColor,
                    )
            }
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (variant == InlineAlertVariant.Danger) FontWeight.Normal else FontWeight.Medium,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun inlineAlertColors(variant: InlineAlertVariant): Triple<Color, Color, Color?> {
    val scheme = MaterialTheme.colorScheme
    return when (variant) {
        InlineAlertVariant.Info ->
            Triple(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary.copy(alpha = 0.35f))
        InlineAlertVariant.Danger ->
            Triple(scheme.errorContainer, scheme.onErrorContainer, scheme.error.copy(alpha = 0.4f))
        InlineAlertVariant.Loading ->
            Triple(scheme.secondaryContainer, scheme.onSecondaryContainer, scheme.outline.copy(alpha = 0.35f))
        InlineAlertVariant.Success -> {
            if (isSystemInDarkTheme()) {
                Triple(Color(0xFF1B4332), Color(0xFFB7E4C7), Color(0xFF40916C).copy(alpha = 0.5f))
            } else {
                Triple(Color(0xFFD1E7DD), Color(0xFF0F5132), Color(0xFF0F5132).copy(alpha = 0.25f))
            }
        }
    }
}
