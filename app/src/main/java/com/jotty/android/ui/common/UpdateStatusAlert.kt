package com.jotty.android.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

typealias UpdateStatusAlertVariant = InlineAlertVariant

@Composable
fun UpdateStatusAlert(
    message: String,
    variant: UpdateStatusAlertVariant,
    modifier: Modifier = Modifier,
) {
    InlineAlert(message = message, variant = variant, modifier = modifier)
}
