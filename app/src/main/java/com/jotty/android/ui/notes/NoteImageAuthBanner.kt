package com.jotty.android.ui.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jotty.android.R
import com.jotty.android.ui.common.DismissibleInfoBanner
import com.jotty.android.util.ServerCapabilities
import com.jotty.android.util.noteContainsJottyMediaUrls

@Composable
internal fun NoteImageAuthBanner(
    capabilitiesKey: String,
    noteContent: String,
    modifier: Modifier = Modifier,
) {
    val blockedKeys by ServerCapabilities.privateImagesAuthBlocked.collectAsStateWithLifecycle()
    var dismissed by rememberSaveable(capabilitiesKey) { mutableStateOf(false) }
    val showBanner =
        !dismissed &&
            noteContainsJottyMediaUrls(noteContent) &&
            blockedKeys.contains(capabilitiesKey)
    if (!showBanner) return

    DismissibleInfoBanner(
        message = stringResource(R.string.note_images_auth_blocked_banner),
        onDismiss = { dismissed = true },
        modifier = modifier,
    )
}
