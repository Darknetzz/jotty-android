package com.jotty.android.ui.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.data.api.ApiClient
import com.jotty.android.data.preferences.JottyInstance
import com.jotty.android.data.preferences.SettingsRepository
import com.jotty.android.util.ApiErrorHelper
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun SetupScreen(
    settingsRepository: SettingsRepository,
    onConfigured: () -> Unit,
    standaloneMode: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    val instances by settingsRepository.instances.collectAsState(initial = emptyList())
    val defaultInstanceId by settingsRepository.defaultInstanceId.collectAsState(initial = null)
    var showAddForm by remember { mutableStateOf(instances.isEmpty()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(instances) {
        if (instances.isNotEmpty() && showAddForm) showAddForm = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        if (standaloneMode && onBack != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(
                    text = stringResource(R.string.manage_instances),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        } else {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.connect_to_jotty),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.choose_saved_instance),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        var instanceToEdit by remember { mutableStateOf<JottyInstance?>(null) }
        if (instanceToEdit != null) {
            InstanceForm(
                initialInstance = instanceToEdit,
                settingsRepository = settingsRepository,
                setAsCurrentOnConnect = !standaloneMode,
                onDone = onConfigured,
                onSaved = { instanceToEdit = null },
                onCancel = { instanceToEdit = null },
            )
        } else if (showAddForm) {
            InstanceForm(
                initialInstance = null,
                settingsRepository = settingsRepository,
                setAsCurrentOnConnect = !standaloneMode,
                onDone = onConfigured,
                onSaved = if (standaloneMode) { { showAddForm = false } } else null,
                onCancel = { if (instances.isNotEmpty()) showAddForm = false },
            )
        } else {
            if (instances.isNotEmpty()) {
                var instanceToDelete by remember { mutableStateOf<JottyInstance?>(null) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(instances, key = { it.id }) { instance ->
                        InstanceCard(
                            instance = instance,
                            isDefault = instance.id == defaultInstanceId,
                            onClick = {
                                scope.launch {
                                    settingsRepository.setCurrentInstanceId(instance.id)
                                    if (standaloneMode) onBack?.invoke() else onConfigured()
                                }
                            },
                            onSetDefault = {
                                scope.launch {
                                    settingsRepository.setDefaultInstanceId(instance.id)
                                }
                            },
                            onEdit = { instanceToEdit = instance },
                            onDelete = { instanceToDelete = instance },
                        )
                    }
                    item {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showAddForm = true },
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Text(stringResource(R.string.add_new_instance), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                instanceToDelete?.let { instance ->
                    AlertDialog(
                        onDismissRequest = { instanceToDelete = null },
                        title = { Text(stringResource(R.string.remove_instance_title)) },
                        text = { Text(stringResource(R.string.remove_instance_message, instance.name)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        settingsRepository.removeInstance(instance.id)
                                        instanceToDelete = null
                                    }
                                },
                            ) {
                                Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { instanceToDelete = null }) {
                                Text(stringResource(R.string.cancel))
                            }
                        },
                    )
                }
            } else {
                InstanceForm(
                    initialInstance = null,
                    settingsRepository = settingsRepository,
                    setAsCurrentOnConnect = !standaloneMode,
                    onDone = onConfigured,
                    onSaved = null,
                    onCancel = null,
                )
            }
        }
    }
}

@Composable
private fun InstanceCard(
    instance: JottyInstance,
    isDefault: Boolean,
    onClick: () -> Unit,
    onSetDefault: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (instance.colorHex != null) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            Color((instance.colorHex.toInt() and 0xFFFFFF) or 0xFF000000.toInt()),
                            CircleShape,
                        ),
                )
            }
            Icon(Icons.Default.Link, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = instance.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = instance.serverUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(
                onClick = onSetDefault,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    if (isDefault) Icons.Default.Star else Icons.Outlined.Star,
                    contentDescription = if (isDefault) stringResource(R.string.default_instance) else stringResource(R.string.set_as_default_instance),
                    tint = if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_instance_action))
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.remove_instance),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun InstanceForm(
    initialInstance: JottyInstance?,
    settingsRepository: SettingsRepository,
    setAsCurrentOnConnect: Boolean = true,
    onDone: () -> Unit,
    onSaved: (() -> Unit)?,
    onCancel: (() -> Unit)?,
) {
    var name by remember(initialInstance) { mutableStateOf(initialInstance?.name ?: "") }
    var serverUrl by remember(initialInstance) { mutableStateOf(initialInstance?.serverUrl ?: "") }
    var apiKey by remember(initialInstance) { mutableStateOf(initialInstance?.apiKey ?: "") }
    var colorHex by remember(initialInstance) { mutableStateOf(initialInstance?.colorHex) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isEdit = initialInstance != null
    val context = LocalContext.current
    val fillUrlAndKeyMsg = stringResource(R.string.fill_url_and_key)

    val instanceColors: List<Long?> = listOf(
        null,
        0xFF6200EEL,
        0xFF03DAC6L,
        0xFF018786L,
        0xFFBB86FCL,
        0xFFCF6679L,
    )

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        if (isEdit) {
            Text(
                text = stringResource(R.string.edit_instance),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = null },
            label = { Text(stringResource(R.string.name)) },
            placeholder = { Text(stringResource(R.string.name_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it; error = null },
            label = { Text(stringResource(R.string.server_url)) },
            placeholder = { Text(stringResource(R.string.server_url_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = error != null,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it; error = null },
            label = { Text(stringResource(R.string.api_key)) },
            placeholder = { Text(stringResource(R.string.api_key_placeholder)) },
            singleLine = true,
            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                TextButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                    Text(if (apiKeyVisible) stringResource(R.string.hide) else stringResource(R.string.show))
                }
            },
            isError = error != null,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.color),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            instanceColors.forEach { hex ->
                val selected = colorHex == hex
                Surface(
                    modifier = Modifier
                        .size(if (hex == null) 36.dp else 32.dp)
                        .clip(CircleShape)
                        .then(
                            if (hex != null) Modifier.background(Color(((hex and 0xFFFFFFFFL).toInt() and 0xFFFFFF) or 0xFF000000.toInt()))
                            else Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                    shape = CircleShape,
                    onClick = { colorHex = hex },
                ) {
                    if (hex == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.no_color), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        error?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onCancel != null) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        try {
                            val url = serverUrl.trim()
                            val key = apiKey.trim()
                            if (url.isBlank() || key.isBlank()) {
                                error = fillUrlAndKeyMsg
                                return@launch
                            }

                            val api = ApiClient.create(url, key)
                            api.health()

                            val instance = JottyInstance(
                                id = initialInstance?.id ?: UUID.randomUUID().toString(),
                                name = name.ifBlank { url.replace(Regex("^https?://"), "").split("/").firstOrNull() ?: "Jotty" },
                                serverUrl = url,
                                apiKey = key,
                                colorHex = colorHex,
                            )
                            settingsRepository.addInstance(instance, setAsCurrent = setAsCurrentOnConnect)
                            if (isEdit) onSaved?.invoke() else if (setAsCurrentOnConnect) onDone() else onSaved?.invoke()
                        } catch (e: Exception) {
                            error = context.getString(R.string.connection_failed, ApiErrorHelper.userMessage(context, e))
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !loading,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isEdit) stringResource(R.string.save) else stringResource(R.string.connect))
                }
            }
        }
    }
}
