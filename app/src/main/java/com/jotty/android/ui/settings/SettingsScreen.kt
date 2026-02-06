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
import com.jotty.android.data.api.AdminOverviewResponse
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.preferences.SettingsRepository
import kotlinx.coroutines.launch
import retrofit2.HttpException

@Composable
fun SettingsScreen(
    api: JottyApi?,
    settingsRepository: SettingsRepository,
    onDisconnect: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentInstance by settingsRepository.currentInstance.collectAsState(initial = null)
    val theme by settingsRepository.theme.collectAsState(initial = null)
    val startTab by settingsRepository.startTab.collectAsState(initial = null)
    var adminOverview by remember { mutableStateOf<AdminOverviewResponse?>(null) }

    LaunchedEffect(api) {
        if (api == null) return@LaunchedEffect
        try {
            adminOverview = api.getAdminOverview()
        } catch (e: HttpException) {
            if (e.code() != 403) adminOverview = null
        } catch (_: Exception) {
            adminOverview = null
        }
    }

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
                headlineContent = { Text(currentInstance?.name ?: "Instance") },
                supportingContent = { Text(currentInstance?.serverUrl ?: "—", maxLines = 2) },
                leadingContent = { Icon(Icons.Default.Link, contentDescription = null) },
            )
        }

        if (adminOverview != null) {
            Spacer(modifier = Modifier.height(24.dp))
            SettingsSectionTitle("Admin Dashboard Overview")
            AdminOverviewCard(adminOverview!!)
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
                    settingsRepository.disconnect()
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
                Column {
                    Text("Disconnect", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Switch to another instance (this one is saved)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
private fun AdminOverviewCard(overview: AdminOverviewResponse) {
    val hasAny = overview.users != null || overview.checklists != null || overview.notes != null || overview.version != null
    if (!hasAny) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            overview.version?.let { v ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Server version", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(v, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                overview.users?.let { StatChip("Users", it) }
                overview.checklists?.let { StatChip("Checklists", it) }
                overview.notes?.let { StatChip("Notes", it) }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
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
