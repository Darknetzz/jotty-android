package com.jotty.android.ui.notes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jotty.android.R
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

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = stringResource(R.string.note_images_auth_blocked_banner),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
        )
        TextButton(
            onClick = { dismissed = true },
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        ) {
            Text(stringResource(R.string.close))
        }
    }
}
