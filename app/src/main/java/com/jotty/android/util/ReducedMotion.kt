package com.jotty.android.util

import android.content.Context
import android.provider.Settings

/** Whether device settings disable animations (Remove animations / animator duration scale 0). */
fun Context.isSystemReducedMotionEnabled(): Boolean =
    try {
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (_: Exception) {
        false
    }

/**
 * Resolves whether decorative transitions should be skipped.
 *
 * - Unset / `"on"`: reduced motion (default; motion effects opt-in only).
 * - `"off"`: motion effects enabled.
 * - `"system"`: follows [systemReducedMotion].
 */
fun resolveReducedMotionEnabled(
    mode: String?,
    systemReducedMotion: Boolean,
): Boolean =
    when (mode?.lowercase()) {
        "off" -> false
        "system" -> systemReducedMotion
        else -> true
    }

fun resolveReducedMotionEnabled(
    mode: String?,
    context: Context,
): Boolean = resolveReducedMotionEnabled(mode, context.isSystemReducedMotionEnabled())
