package com.jotty.android.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Use as Material3 [androidx.compose.material3.Scaffold]'s `contentWindowInsets` when that scaffold is
 * nested under [com.jotty.android.ui.main.MainScreen]'s outer scaffold. The outer scaffold already
 * accounts for system bars and the bottom navigation bar; the default nested insets add extra bottom
 * padding (a visible gap above the nav bar).
 */
val MainNestedScaffoldContentWindowInsets: WindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)

/**
 * Padding for tab content inside MainScreen: optional inner scaffold padding, horizontal inset, and
 * top "comfort" inset only — no bottom comfort padding so lists can extend to the bottom nav.
 */
fun Modifier.mainScreenTabContentPadding(
    topComfortDp: Int,
    horizontal: Dp = 16.dp,
    scaffoldInnerPadding: PaddingValues? = null,
): Modifier {
    var m = this
    if (scaffoldInnerPadding != null) m = m.padding(scaffoldInnerPadding)
    return m.padding(start = horizontal, end = horizontal, top = topComfortDp.dp)
}
