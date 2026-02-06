package com.jotty.android.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.jotty.android.BuildConfig
import com.jotty.android.data.api.AdminOverviewResponse
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.SummaryData
import com.jotty.android.data.api.SummaryResponse
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
    var summary by remember { mutableStateOf<SummaryData?>(null) }
    var showAboutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(api) {
        if (api == null) return@LaunchedEffect
        try {
            summary = api.getSummary().summary
        } catch (_: Exception) {
            summary = null
        }
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

        if (summary != null || adminOverview != null) {
            Spacer(modifier = Modifier.height(24.dp))
            SettingsSectionTitle("Dashboard Overview")
            summary?.let { DashboardSummaryCard(it) }
            adminOverview?.let { AdminOverviewCard(it) }
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

        Spacer(modifier = Modifier.height(24.dp))

        // About
        SettingsSectionTitle("About")
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showAboutDialog = true },
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
                Column {
                    Text("About", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Version, source code, and more",
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

    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false },
            versionName = BuildConfig.VERSION_NAME ?: "—",
            versionCode = BuildConfig.VERSION_CODE,
        )
    }
}

@Composable
private fun DashboardSummaryCard(summary: SummaryData) {
    val notesTotal = summary.notes?.total ?: 0
    val listsTotal = summary.checklists?.total ?: 0
    val completionRate = summary.items?.completionRate
    val hasAny = notesTotal > 0 || listsTotal > 0 || completionRate != null

    if (!hasAny) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            summary.username?.let { u ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("User", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(u, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (notesTotal > 0 || summary.notes != null) StatChip("Notes", notesTotal)
                if (listsTotal > 0 || summary.checklists != null) StatChip("Checklists", listsTotal)
                completionRate?.let { StatChip("Done %", it) }
            }
        }
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

private const val GITHUB_REPO_URL = "https://github.com/Darknetzz/jotty-android"

@Composable
private fun AboutDialog(
    onDismiss: () -> Unit,
    versionName: String,
    versionCode: Int,
) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About Jotty Android") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Android client for Jotty — self-hosted checklists and notes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Version", style = MaterialTheme.typography.bodyMedium)
                    Text("$versionName ($versionCode)", style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                TextButton(
                    onClick = { uriHandler.openUri(GITHUB_REPO_URL) },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(
                        Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View source on GitHub")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
