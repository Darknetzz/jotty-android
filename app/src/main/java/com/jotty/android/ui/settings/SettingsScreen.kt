package com.jotty.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jotty.android.BuildConfig
import com.jotty.android.R
import com.jotty.android.data.api.AdminOverviewResponse
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.SummaryData
import com.jotty.android.data.preferences.SettingsRepository
import kotlinx.coroutines.launch
import retrofit2.HttpException

@Composable
fun SettingsScreen(
    api: JottyApi?,
    settingsRepository: SettingsRepository,
    onDisconnect: () -> Unit,
    onManageInstances: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val currentInstance by settingsRepository.currentInstance.collectAsState(initial = null)
    val theme by settingsRepository.theme.collectAsState(initial = null)
    val startTab by settingsRepository.startTab.collectAsState(initial = null)
    val swipeToDeleteEnabled by settingsRepository.swipeToDeleteEnabled.collectAsState(initial = false)
    val defaultInstanceId by settingsRepository.defaultInstanceId.collectAsState(initial = null)
    var adminOverview by remember { mutableStateOf<AdminOverviewResponse?>(null) }
    var summary by remember { mutableStateOf<SummaryData?>(null) }
    var healthOk by remember { mutableStateOf<Boolean?>(null) }
    var showAboutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(api) {
        if (api == null) return@LaunchedEffect
        try {
            api.health()
            healthOk = true
        } catch (_: Exception) {
            healthOk = false
        }
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
            stringResource(R.string.settings),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Connection
        SettingsSectionTitle(stringResource(R.string.connection))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column {
                ListItem(
                    headlineContent = { Text(currentInstance?.name ?: stringResource(R.string.instance_label)) },
                    supportingContent = {
                        Column {
                            Text(currentInstance?.serverUrl ?: "\u2014", maxLines = 2)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when (healthOk) {
                                    true -> stringResource(R.string.connected)
                                    false -> stringResource(R.string.server_unreachable)
                                    null -> "\u2014"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when (healthOk) {
                                    true -> MaterialTheme.colorScheme.primary
                                    false -> MaterialTheme.colorScheme.error
                                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    },
                    leadingContent = { Icon(Icons.Default.Link, contentDescription = null) },
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.manage_instances)) },
                    supportingContent = {
                        Text(
                            stringResource(R.string.manage_instances_description),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    leadingContent = { Icon(Icons.Default.ManageAccounts, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onManageInstances),
                )
                if (currentInstance != null) {
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.set_as_default_instance)) },
                        supportingContent = {
                            Text(
                                stringResource(R.string.open_to_default_instance),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = if (defaultInstanceId == currentInstance?.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier.clickable {
                            scope.launch {
                                settingsRepository.setDefaultInstanceId(currentInstance?.id)
                            }
                        },
                    )
                }
            }
        }

        if (summary != null || adminOverview != null) {
            Spacer(modifier = Modifier.height(24.dp))
            SettingsSectionTitle(stringResource(R.string.dashboard_overview))
            summary?.let { DashboardSummaryCard(it) }
            adminOverview?.let { AdminOverviewCard(it) }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Appearance
        SettingsSectionTitle(stringResource(R.string.appearance))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.theme)) },
                supportingContent = {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            null to R.string.theme_system,
                            "light" to R.string.theme_light,
                            "dark" to R.string.theme_dark,
                        ).forEach { (value, labelRes) ->
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
                                label = { Text(stringResource(labelRes)) },
                            )
                        }
                    }
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.start_screen)) },
                supportingContent = {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            "checklists" to R.string.nav_checklists,
                            "notes" to R.string.nav_notes,
                            "settings" to R.string.nav_settings,
                        ).forEach { (value, labelRes) ->
                            FilterChip(
                                selected = (startTab ?: "checklists") == value,
                                onClick = {
                                    scope.launch {
                                        settingsRepository.setStartTab(value)
                                    }
                                },
                                label = { Text(stringResource(labelRes)) },
                            )
                        }
                    }
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.swipe_to_delete)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.swipe_to_delete_description),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = swipeToDeleteEnabled,
                        onCheckedChange = {
                            scope.launch {
                                settingsRepository.setSwipeToDeleteEnabled(it)
                            }
                        },
                    )
                },
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Account
        SettingsSectionTitle(stringResource(R.string.account))
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
                    Text(stringResource(R.string.disconnect), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.disconnect_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // About
        SettingsSectionTitle(stringResource(R.string.about))
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
                    Text(stringResource(R.string.about), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.about_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            stringResource(R.string.jotty_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false },
            versionName = BuildConfig.VERSION_NAME ?: "\u2014",
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
                    Text(stringResource(R.string.user_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(u, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (notesTotal > 0 || summary.notes != null) StatChip(stringResource(R.string.stat_notes), notesTotal)
                if (listsTotal > 0 || summary.checklists != null) StatChip(stringResource(R.string.stat_checklists), listsTotal)
                completionRate?.let { StatChip(stringResource(R.string.stat_done_percent), it) }
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
                    Text(stringResource(R.string.server_version), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(v, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                overview.users?.let { StatChip(stringResource(R.string.stat_users), it) }
                overview.checklists?.let { StatChip(stringResource(R.string.stat_checklists), it) }
                overview.notes?.let { StatChip(stringResource(R.string.stat_notes), it) }
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
        title = { Text(stringResource(R.string.about_jotty_android)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.about_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.version), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.version_format, versionName, versionCode), style = MaterialTheme.typography.bodyMedium)
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
                    Text(stringResource(R.string.view_source_github))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}
