package com.jotty.android.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.data.api.ItemShares
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.ShareGrantRequest
import com.jotty.android.data.api.SharePermission
import com.jotty.android.data.api.itemSharesFromListing
import com.jotty.android.util.JottySharingProbe
import com.jotty.android.util.publicShareUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareServerDialog(
    itemType: String,
    itemId: String,
    itemTitle: String,
    api: JottyApi,
    capabilitiesKey: String?,
    baseUrl: String? = null,
    onDismiss: () -> Unit,
    onExportText: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var shareInfo by remember { mutableStateOf<ItemShares?>(null) }
    var serverSupported by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var addUsername by remember { mutableStateOf("") }
    var permission by remember { mutableStateOf(SharePermission.READ) }
    var permissionMenuExpanded by remember { mutableStateOf(false) }

    val permissionReadLabel = stringResource(R.string.share_permission_read)
    val permissionWriteLabel = stringResource(R.string.share_permission_write)
    val permissionDeleteLabel = stringResource(R.string.share_permission_delete)
    val permissionLabel =
        when (permission) {
            SharePermission.WRITE -> permissionWriteLabel
            SharePermission.DELETE -> permissionDeleteLabel
            else -> permissionReadLabel
        }

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
                        loadItemShares(api, itemType, itemId)
                    }
                }.onFailure { error ->
                    errorMessage =
                        when {
                            error is HttpException && error.code() == 403 ->
                                resources.getString(R.string.share_forbidden)
                            else -> resources.getString(R.string.share_server_load_failed)
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
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    val info = shareInfo!!
                    val publicUrl = publicShareUrl(baseUrl, itemType, itemId)
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text =
                                    if (info.isPublic) {
                                        stringResource(R.string.share_public_enabled)
                                    } else {
                                        stringResource(R.string.share_public_disabled)
                                    },
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = info.isPublic,
                                enabled = !busy,
                                onCheckedChange = { enable ->
                                    scope.launch {
                                        busy = true
                                        errorMessage = null
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                if (enable) {
                                                    api.grantShare(
                                                        itemId,
                                                        ShareGrantRequest(
                                                            username = SharePermission.PUBLIC_USER,
                                                            permission = SharePermission.READ,
                                                        ),
                                                    ).data
                                                } else {
                                                    api.revokeShare(itemId, SharePermission.PUBLIC_USER).data
                                                }
                                            }
                                        }.onSuccess { updated ->
                                            if (updated != null) shareInfo = updated
                                        }.onFailure { error ->
                                            errorMessage =
                                                when {
                                                    error is HttpException && error.code() == 403 ->
                                                        resources.getString(R.string.share_forbidden)
                                                    else ->
                                                        resources.getString(R.string.share_update_failed)
                                                }
                                        }
                                        busy = false
                                    }
                                },
                            )
                        }
                        if (info.isPublic && !publicUrl.isNullOrBlank()) {
                            Text(
                                text = publicUrl,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            TextButton(
                                onClick = {
                                    val clipboard =
                                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText("share link", publicUrl),
                                    )
                                },
                                enabled = !busy,
                            ) {
                                Text(stringResource(R.string.copy_public_link))
                            }
                        }

                        val holders =
                            info.sharedWith.filter {
                                !it.username.equals(SharePermission.PUBLIC_USER, ignoreCase = true)
                            }
                        if (holders.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.share_holders_heading),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            holders.forEach { holder ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(holder.username)
                                        Text(
                                            text = permissionSummary(holder.permissions),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                busy = true
                                                errorMessage = null
                                                runCatching {
                                                    withContext(Dispatchers.IO) {
                                                        api.revokeShare(itemId, holder.username).data
                                                    }
                                                }.onSuccess { updated ->
                                                    if (updated != null) shareInfo = updated
                                                }.onFailure { error ->
                                                    errorMessage =
                                                        when {
                                                            error is HttpException && error.code() == 403 ->
                                                                resources.getString(R.string.share_forbidden)
                                                            else ->
                                                                resources.getString(R.string.share_update_failed)
                                                        }
                                                }
                                                busy = false
                                            }
                                        },
                                        enabled = !busy,
                                    ) {
                                        Text(stringResource(R.string.share_remove_user))
                                    }
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.share_add_user_heading),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        OutlinedTextField(
                            value = addUsername,
                            onValueChange = { addUsername = it },
                            label = { Text(stringResource(R.string.share_username_label)) },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ExposedDropdownMenuBox(
                            expanded = permissionMenuExpanded,
                            onExpandedChange = { if (!busy) permissionMenuExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = permissionLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.share_permission_label)) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = permissionMenuExpanded)
                                },
                                enabled = !busy,
                                modifier =
                                    Modifier
                                        .menuAnchor()
                                        .fillMaxWidth(),
                            )
                            ExposedDropdownMenu(
                                expanded = permissionMenuExpanded,
                                onDismissRequest = { permissionMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(permissionReadLabel) },
                                    onClick = {
                                        permission = SharePermission.READ
                                        permissionMenuExpanded = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(permissionWriteLabel) },
                                    onClick = {
                                        permission = SharePermission.WRITE
                                        permissionMenuExpanded = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(permissionDeleteLabel) },
                                    onClick = {
                                        permission = SharePermission.DELETE
                                        permissionMenuExpanded = false
                                    },
                                )
                            }
                        }
                        TextButton(
                            onClick = {
                                val username = addUsername.trim()
                                if (username.isEmpty()) return@TextButton
                                scope.launch {
                                    busy = true
                                    errorMessage = null
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            api.grantShare(
                                                itemId,
                                                ShareGrantRequest(
                                                    username = username,
                                                    permission = permission,
                                                ),
                                            ).data
                                        }
                                    }.onSuccess { updated ->
                                        if (updated != null) {
                                            shareInfo = updated
                                            addUsername = ""
                                        }
                                    }.onFailure { error ->
                                        errorMessage =
                                            when {
                                                error is HttpException && error.code() == 403 ->
                                                    resources.getString(R.string.share_forbidden)
                                                else ->
                                                    resources.getString(R.string.share_update_failed)
                                            }
                                    }
                                    busy = false
                                }
                            },
                            enabled = !busy && addUsername.trim().isNotEmpty(),
                        ) {
                            Text(stringResource(R.string.share_add_user))
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
                !serverSupported || shareInfo == null ->
                    TextButton(onClick = onExportText) {
                        Text(stringResource(R.string.export_share))
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

@Composable
private fun permissionSummary(permissions: com.jotty.android.data.api.SharePermissions): String =
    when {
        permissions.canDelete -> stringResource(R.string.share_permission_delete)
        permissions.canEdit -> stringResource(R.string.share_permission_write)
        else -> stringResource(R.string.share_permission_read)
    }

private suspend fun loadItemShares(
    api: JottyApi,
    itemType: String,
    itemId: String,
): ItemShares {
    val response =
        api.getShares(
            direction = "byMe",
            item = itemId,
        )
    val listing = response.shares.firstOrNull { it.uuid.equals(itemId, ignoreCase = true) }
        ?: response.shares.firstOrNull()
    return itemSharesFromListing(itemId, itemType, listing)
}
