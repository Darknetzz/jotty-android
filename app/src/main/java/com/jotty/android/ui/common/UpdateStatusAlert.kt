package com.jotty.android.ui.common

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

enum class UpdateStatusAlertVariant {
    Info,
    Success,
    Danger,
    Loading,
}

@Composable
fun UpdateStatusAlert(
    message: String,
    variant: UpdateStatusAlertVariant,
    modifier: Modifier = Modifier,
) {
    val (containerColor, contentColor) = alertColors(variant)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (variant) {
                UpdateStatusAlertVariant.Loading ->
                    CircularProgressIndicator(
                        modifier =
                            Modifier
                                .size(20.dp)
                                .semantics { contentDescription = message },
                        strokeWidth = 2.dp,
                        color = contentColor,
                        trackColor = contentColor.copy(alpha = 0.3f),
                    )
                UpdateStatusAlertVariant.Info ->
                    Icon(
                        Icons.Default.NewReleases,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = contentColor,
                    )
                UpdateStatusAlertVariant.Success ->
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = contentColor,
                    )
                UpdateStatusAlertVariant.Danger ->
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
                fontWeight = FontWeight.Medium,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun alertColors(variant: UpdateStatusAlertVariant): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (variant) {
        UpdateStatusAlertVariant.Info ->
            scheme.primaryContainer to scheme.onPrimaryContainer
        UpdateStatusAlertVariant.Danger ->
            scheme.errorContainer to scheme.onErrorContainer
        UpdateStatusAlertVariant.Loading ->
            scheme.secondaryContainer to scheme.onSecondaryContainer
        UpdateStatusAlertVariant.Success -> {
            if (isSystemInDarkTheme()) {
                Color(0xFF1B4332) to Color(0xFFB7E4C7)
            } else {
                Color(0xFFD1E7DD) to Color(0xFF0F5132)
            }
        }
    }
}
