package com.jotty.android.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Switches between list (null) and detail (non-null) without composing both at once.
 *
 * Avoids [AnimatedContent], which keeps enter and exit children alive during transitions and can
 * crash when both sides use scrollables (infinite height constraints). We only animate the
 * incoming keyed content so exactly one scrollable subtree is composed at a time.
 */
@Composable
fun <T> ListDetailContainer(
    target: T?,
    modifier: Modifier = Modifier,
    contentKey: (T?) -> Any = { it ?: "list" },
    content: @Composable (T?) -> Unit,
) {
    val reducedMotion = LocalReducedMotionEnabled.current
    Box(modifier) {
        key(contentKey(target)) {
            var visible by remember { mutableStateOf(reducedMotion) }
            LaunchedEffect(reducedMotion) {
                if (!reducedMotion) visible = true
            }
            val alpha =
                animateFloatAsState(
                    targetValue = if (visible) 1f else 0f,
                    animationSpec = if (reducedMotion) snap() else tween(durationMillis = 180),
                    label = "list-detail-fade-in",
                )
            Box(Modifier.graphicsLayer(alpha = alpha.value)) {
                content(target)
            }
        }
    }
}
