package com.jotty.android.ui.checklists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jotty.android.R
import com.jotty.android.ui.common.DismissibleInfoBanner
import com.jotty.android.util.ServerCapabilities

@Composable
fun ChecklistPatchCapabilityBanner(
    capabilitiesKey: String,
    modifier: Modifier = Modifier,
) {
    var dismissed by rememberSaveable(capabilitiesKey) { mutableStateOf(false) }
    if (dismissed || !ServerCapabilities.isItemPatchLimited(capabilitiesKey)) return

    DismissibleInfoBanner(
        message = stringResource(R.string.server_patch_limited_banner),
        onDismiss = { dismissed = true },
        modifier = modifier,
    )
}
