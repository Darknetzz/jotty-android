package com.jotty.android.ui.common

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier

/**
 * Switches between list (null) and detail (non-null) without composing both at once.
 *
 * Avoids [AnimatedContent], which keeps enter and exit children alive during transitions and can
 * crash when both sides use scrollables (infinite height constraints).
 */
@Composable
fun <T> ListDetailContainer(
    target: T?,
    modifier: Modifier = Modifier,
    contentKey: (T?) -> Any = { it ?: "list" },
    content: @Composable (T?) -> Unit,
) {
    val reducedMotion = LocalReducedMotionEnabled.current
    if (reducedMotion) {
        Box(modifier) {
            key(contentKey(target)) {
                content(target)
            }
        }
    } else {
        Crossfade(
            targetState = contentKey(target),
            modifier = modifier,
            label = "listDetailCrossfade",
        ) {
            content(target)
        }
    }
}
