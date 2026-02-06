package com.jotty.android.ui.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jotty.android.data.api.ApiClient
import com.jotty.android.data.preferences.JottyInstance
import com.jotty.android.data.preferences.SettingsRepository
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun SetupScreen(
    settingsRepository: SettingsRepository,
    onConfigured: () -> Unit,
) {
    val instances by settingsRepository.instances.collectAsState(initial = emptyList())
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
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Connect to Jotty",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Choose a saved instance or add a new one",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (showAddForm) {
            AddInstanceForm(
                settingsRepository = settingsRepository,
                onDone = onConfigured,
                onCancel = { if (instances.isNotEmpty()) showAddForm = false },
            )
        } else {
            if (instances.isNotEmpty()) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(instances, key = { it.id }) { instance ->
                        InstanceCard(
                            instance = instance,
                            onClick = {
                                scope.launch {
                                    settingsRepository.setCurrentInstanceId(instance.id)
                                    onConfigured()
                                }
                            },
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
                                Text("Add new instance", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            } else {
                AddInstanceForm(
                    settingsRepository = settingsRepository,
                    onDone = onConfigured,
                    onCancel = null,
                )
            }
        }
    }
}

@Composable
private fun InstanceCard(
    instance: JottyInstance,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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
        }
    }
}

@Composable
private fun AddInstanceForm(
    settingsRepository: SettingsRepository,
    onDone: () -> Unit,
    onCancel: (() -> Unit)?,
) {
    var name by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = null },
            label = { Text("Name") },
            placeholder = { Text("e.g. Work, Personal") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it; error = null },
            label = { Text("Server URL") },
            placeholder = { Text("https://jotty.example.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = error != null,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it; error = null },
            label = { Text("API Key") },
            placeholder = { Text("ck_...") },
            singleLine = true,
            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                TextButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                    Text(if (apiKeyVisible) "Hide" else "Show")
                }
            },
            isError = error != null,
        )

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
                    Text("Cancel")
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
                                error = "Please fill in server URL and API key"
                                return@launch
                            }

                            val api = ApiClient.create(url, key)
                            api.health()

                            val instance = JottyInstance(
                                id = UUID.randomUUID().toString(),
                                name = name.ifBlank { url.replace(Regex("^https?://"), "").split("/").firstOrNull() ?: "Jotty" },
                                serverUrl = url,
                                apiKey = key,
                            )
                            settingsRepository.addInstance(instance)
                            onDone()
                        } catch (e: Exception) {
                            error = "Connection failed: ${e.message ?: "Unknown error"}"
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
                    Text("Connect")
                }
            }
        }
    }
}
