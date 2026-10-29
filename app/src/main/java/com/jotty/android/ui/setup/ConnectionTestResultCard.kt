package com.jotty.android.ui.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.ui.common.InlineAlertVariant
import com.jotty.android.ui.common.inlineAlertColors
import com.jotty.android.util.ConnectionDiagnostics

/** UI state for the instance form's connection test. */
sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState

    data object Running : ConnectionTestState

    data class Done(val result: ConnectionDiagnostics.Result) : ConnectionTestState
}

@Composable
fun ConnectionTestResultCard(
    state: ConnectionTestState,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            (fadeIn() + scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)))
                .togetherWith(fadeOut())
        },
        modifier = modifier,
        label = "connectionTest",
    ) { s ->
        when (s) {
            ConnectionTestState.Idle -> Unit
            ConnectionTestState.Running -> RunningCard()
            is ConnectionTestState.Done ->
                when (val r = s.result) {
                    is ConnectionDiagnostics.Result.Success -> SuccessCard(r)
                    is ConnectionDiagnostics.Result.Failure -> FailureCard(r)
                }
        }
    }
}

@Composable
private fun ResultSurface(
    container: Color,
    border: Color,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .border(1.dp, border, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) { content() }
    }
}

@Composable
private fun RunningCard() {
    val scheme = MaterialTheme.colorScheme
    ResultSurface(scheme.secondaryContainer, scheme.outline.copy(alpha = 0.35f)) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
            color = scheme.onSecondaryContainer,
        )
        Text(
            stringResource(R.string.conn_test_running),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun SuccessCard(r: ConnectionDiagnostics.Result.Success) {
    val (container, content, border) = inlineAlertColors(InlineAlertVariant.Success)
    ResultSurface(container, border ?: content) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(32.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.conn_test_success_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = content,
            )
            r.username?.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.conn_test_success_user, it), style = MaterialTheme.typography.bodyMedium, color = content)
            }
            val details =
                listOfNotNull(
                    r.version?.let { stringResource(R.string.conn_test_success_version, it) },
                    stringResource(R.string.conn_test_success_latency, r.latencyMs),
                    stringResource(R.string.conn_test_success_key),
                ).joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun FailureCard(r: ConnectionDiagnostics.Result.Failure) {
    val scheme = MaterialTheme.colorScheme
    val explanation =
        if (r.formatArgs.isEmpty()) {
            stringResource(r.explanationRes)
        } else {
            stringResource(r.explanationRes, *r.formatArgs.toTypedArray())
        }
    ResultSurface(scheme.errorContainer, scheme.error.copy(alpha = 0.4f)) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = scheme.error,
            modifier = Modifier.size(32.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(r.titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onErrorContainer,
            )
            Text(explanation, style = MaterialTheme.typography.bodyMedium, color = scheme.onErrorContainer)
            r.detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onErrorContainer.copy(alpha = 0.8f))
            }
            if (r.hintResIds.isNotEmpty()) {
                Text(
                    stringResource(R.string.conn_test_things_to_check),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onErrorContainer,
                    modifier = Modifier.padding(top = 6.dp),
                )
                r.hintResIds.forEach { hint ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.list_bullet), color = scheme.onErrorContainer)
                        Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = scheme.onErrorContainer)
                    }
                }
            }
        }
    }
}
