package com.jotty.android.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jotty.android.data.preferences.SettingsRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onDisconnect: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val serverUrl by settingsRepository.serverUrl.collectAsState(initial = null)
    val theme by settingsRepository.theme.collectAsState(initial = null)
    val startTab by settingsRepository.startTab.collectAsState(initial = null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Connection
        SettingsSectionTitle("Connection")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            ListItem(
                headlineContent = { Text("Server") },
                supportingContent = { Text(serverUrl ?: "—", maxLines = 2) },
                leadingContent = { Icon(Icons.Default.Link, contentDescription = null) },
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Appearance
        SettingsSectionTitle("Appearance")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            ListItem(
                headlineContent = { Text("Theme") },
                supportingContent = {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            null to "System",
                            "light" to "Light",
                            "dark" to "Dark",
                        ).forEach { (value, label) ->
                            val isSelected = when (value) {
                                null -> theme.isNullOrBlank()
                                else -> theme == value
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    scope.launch {
                                        settingsRepository.setTheme(value)
                                    }
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Start screen") },
                supportingContent = {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            "checklists" to "Checklists",
                            "notes" to "Notes",
                            "settings" to "Settings",
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = (startTab ?: "checklists") == value,
                                onClick = {
                                    scope.launch {
                                        settingsRepository.setStartTab(value)
                                    }
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Account
        SettingsSectionTitle("Account")
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    settingsRepository.clear()
                    onDisconnect()
                }
            },
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(Icons.Default.Logout, contentDescription = null)
                Text("Disconnect", style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            "Jotty Android • Connects to self-hosted Jotty servers",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
