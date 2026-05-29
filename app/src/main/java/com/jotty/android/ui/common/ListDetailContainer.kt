package com.jotty.android.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Animates the master/detail switch for list screens. [target] is the selected item (null = list).
 * Entering the detail slides in from the end; going back slides toward the start. Honors
 * [LocalReducedMotionEnabled] (no animation when on) and only animates the list↔detail switch,
 * not updates to an already-open detail. Shared by the notes and checklists screens.
 */
@Composable
fun <T> ListDetailContainer(
    target: T?,
    modifier: Modifier = Modifier,
    content: @Composable (T?) -> Unit,
) {
    val reducedMotion = LocalReducedMotionEnabled.current
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        transitionSpec = {
            if (reducedMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val direction = if (targetState != null) SlideDirection.Start else SlideDirection.End
                (slideIntoContainer(direction, tween(250)) + fadeIn(tween(250))) togetherWith
                    (slideOutOfContainer(direction, tween(250)) + fadeOut(tween(250)))
            }
        },
        contentKey = { it != null },
        label = "list-detail",
    ) { state ->
        content(state)
    }
}
