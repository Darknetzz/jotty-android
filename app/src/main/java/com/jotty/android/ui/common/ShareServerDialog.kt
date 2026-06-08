package com.jotty.android.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.ShareInfo
import com.jotty.android.util.JottySharingProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

@Composable
fun ShareServerDialog(
    itemType: String,
    itemId: String,
    itemTitle: String,
    api: JottyApi,
    capabilitiesKey: String?,
    onDismiss: () -> Unit,
    onExportText: () -> Unit,
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var shareInfo by remember { mutableStateOf<ShareInfo?>(null) }
    var serverSupported by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(itemId, capabilitiesKey) {
        loading = true
        errorMessage = null
        val key = capabilitiesKey.orEmpty()
        serverSupported =
            if (key.isNotBlank()) {
                JottySharingProbe.probeSharingApi(api, key)
            } else {
                false
            }
        if (serverSupported) {
            shareInfo =
                runCatching {
                    withContext(Dispatchers.IO) {
                        api.getSharingInfo(itemType, itemId).data
                    }
                }.getOrNull()
        }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_with_server_title, itemTitle)) },
        text = {
            when {
                loading ->
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                !serverSupported -> {
                    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.share_server_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.share_server_use_export),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                shareInfo != null -> {
                    val info = shareInfo
                    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        Text(
                            text =
                                if (info?.isPubliclyShared == true) {
                                    stringResource(R.string.share_public_enabled)
                                } else {
                                    stringResource(R.string.share_public_disabled)
                                },
                        )
                        info?.publicUrl?.takeIf { it.isNotBlank() }?.let { url ->
                            Text(
                                text = url,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (!info?.sharedWith.isNullOrEmpty()) {
                            Text(
                                text =
                                    stringResource(
                                        R.string.share_with_users,
                                        info?.sharedWith?.joinToString(", ").orEmpty(),
                                    ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                else ->
                    Text(
                        text = errorMessage ?: stringResource(R.string.share_server_load_failed),
                        color = MaterialTheme.colorScheme.error,
                    )
            }
        },
        confirmButton = {
            when {
                loading -> Unit
                shareInfo?.publicUrl?.isNotBlank() == true ->
                    TextButton(
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("share link", shareInfo?.publicUrl),
                            )
                            onDismiss()
                        },
                    ) {
                        Text(stringResource(R.string.copy_public_link))
                    }
                else ->
                    TextButton(onClick = onExportText) {
                        Text(stringResource(R.string.export_share))
                    }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

suspend fun togglePublicShare(
    api: JottyApi,
    itemType: String,
    itemId: String,
    enable: Boolean,
): Result<ShareInfo?> =
    runCatching {
        api.updateSharingInfo(
            itemType = itemType,
            itemId = itemId,
            body =
                com.jotty.android.data.api.UpdateShareInfoRequest(
                    isPubliclyShared = enable,
                ),
        ).data
    }.recoverCatching { error ->
        if (error is HttpException && error.code() in setOf(404, 405)) {
            throw UnsupportedOperationException("sharing not supported")
        } else {
            throw error
        }
    }
