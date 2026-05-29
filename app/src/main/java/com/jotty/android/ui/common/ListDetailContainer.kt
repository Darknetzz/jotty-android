package com.jotty.android.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier

/**
 * Switches between list (null) and detail (non-null) without composing both at once.
 *
 * [AnimatedContent] was avoided here because it keeps enter and exit children alive during the
 * transition. Our list uses [LazyColumn] and detail uses a vertically scrolling note view; measuring
 * both scrollables together triggers "infinity maximum height constraints" crashes when opening a note.
 * Only the detail pane gets a slide-in animation (when reduced motion is off).
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
        if (target == null) {
            content(null)
        } else {
            key(contentKey(target)) {
                if (reducedMotion) {
                    content(target)
                } else {
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInHorizontally(animationSpec = tween(250)) { it / 2 } + fadeIn(tween(250)),
                        exit = slideOutHorizontally(animationSpec = tween(250)) { it / 2 } + fadeOut(tween(250)),
                    ) {
                        content(target)
                    }
                }
            }
        }
    }
}
