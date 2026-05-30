package com.jotty.android.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * Animated shimmer [Brush] for skeleton placeholders. Honors [LocalReducedMotionEnabled]: when
 * reduced motion is on, returns a static surface tint instead of an animation.
 */
@Composable
fun rememberShimmerBrush(): Brush {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val colors = listOf(base, highlight, base)
    if (LocalReducedMotionEnabled.current) {
        return Brush.horizontalGradient(listOf(base, base))
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1200),
                repeatMode = RepeatMode.Restart,
            ),
        label = "shimmer-translate",
    )
    return Brush.linearGradient(
        colors = colors,
        start = Offset(translate - 300f, 0f),
        end = Offset(translate, 0f),
    )
}

/** A single shimmering rounded block of the given [height]. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    height: Int = 16,
    brush: Brush = rememberShimmerBrush(),
) {
    Spacer(
        modifier =
            modifier
                .height(height.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush),
    )
}

/** Placeholder list of shimmering card rows, shown while a list loads with no cached data. */
@Composable
fun ListLoadingSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 6,
) {
    val brush = rememberShimmerBrush()
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                // Placeholders carry no information; keep them out of the accessibility tree.
                .clearAndSetSemantics {},
    ) {
        repeat(rows) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                SkeletonBlock(modifier = Modifier.fillMaxWidth(0.6f), height = 18, brush = brush)
                Spacer(modifier = Modifier.height(8.dp))
                SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 12, brush = brush)
                Spacer(modifier = Modifier.height(6.dp))
                SkeletonBlock(modifier = Modifier.fillMaxWidth(0.85f), height = 12, brush = brush)
            }
        }
    }
}
