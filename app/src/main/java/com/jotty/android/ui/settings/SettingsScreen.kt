package com.jotty.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.jeziellago.compose.markdowntext.MarkdownText
import com.jotty.android.BuildConfig
import com.jotty.android.R
import com.jotty.android.data.updates.InstallResult
import com.jotty.android.data.updates.UpdateCheckResult
import com.jotty.android.data.updates.UpdateChecker
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
    val themeMode by settingsRepository.themeMode.collectAsState(initial = null)
    val themeColor by settingsRepository.themeColor.collectAsState(initial = "default")
    val startTab by settingsRepository.startTab.collectAsState(initial = null)
    val swipeToDeleteEnabled by settingsRepository.swipeToDeleteEnabled.collectAsState(initial = false)
    val contentPaddingMode by settingsRepository.contentPaddingMode.collectAsState(initial = "comfortable")
    val defaultInstanceId by settingsRepository.defaultInstanceId.collectAsState(initial = null)
    var adminOverview by remember { mutableStateOf<AdminOverviewResponse?>(null) }
    var summary by remember { mutableStateOf<SummaryData?>(null) }
    var healthOk by remember { mutableStateOf<Boolean?>(null) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    fun refreshOverview(showRefreshingIndicator: Boolean) {
        scope.launch {
            if (showRefreshingIndicator) isRefreshing = true
            try {
                api?.let { a ->
                    try {
                        a.health()
                        healthOk = true
                    } catch (_: Exception) {
                        healthOk = false
                    }
                    try {
                        summary = a.getSummary().summary
                    } catch (_: Exception) {
                        summary = null
                    }
                    try {
                        adminOverview = a.getAdminOverview()
                    } catch (e: HttpException) {
                        if (e.code() != 403) adminOverview = null
                    } catch (_: Exception) {
                        adminOverview = null
                    }
                }
            } finally {
                if (showRefreshingIndicator) isRefreshing = false
            }
        }
    }

    LaunchedEffect(api) {
        refreshOverview(showRefreshingIndicator = false)
    }

    val pullRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { refreshOverview(showRefreshingIndicator = true) },
        state = pullRefreshState,
    ) {
        val contentVerticalDp = if (contentPaddingMode == "compact") 8 else 16
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = contentVerticalDp.dp)
                .verticalScroll(rememberScrollState()),
        ) {
        Text(
            stringResource(R.string.settings),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Overview (connection + dashboard) ─────────────────────────────────
        SettingsSectionTitle(stringResource(R.string.settings_category_overview))
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
            Spacer(modifier = Modifier.height(12.dp))
            SettingsSectionSubtitle(stringResource(R.string.dashboard_overview))
            summary?.let { DashboardSummaryCard(it) }
            if (summary != null && adminOverview != null) Spacer(modifier = Modifier.height(8.dp))
            adminOverview?.let { AdminOverviewCard(it) }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ─── General (appearance & behavior) ───────────────────────────────────
        SettingsSectionTitle(stringResource(R.string.settings_category_general))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.theme_mode_label)) },
                supportingContent = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            null to R.string.theme_system,
                            "light" to R.string.theme_light,
                            "dark" to R.string.theme_dark,
                        ).forEach { (value, labelRes) ->
                            val isSelected = when (value) {
                                null -> themeMode.isNullOrBlank()
                                else -> themeMode == value
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    scope.launch {
                                        settingsRepository.setThemeMode(value)
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
                headlineContent = { Text(stringResource(R.string.theme_color_label)) },
                supportingContent = {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            "default" to R.string.theme_color_default,
                            "amoled" to R.string.theme_amoled,
                            "sepia" to R.string.theme_sepia,
                            "midnight" to R.string.theme_midnight,
                            "rose" to R.string.theme_rose,
                            "ocean" to R.string.theme_ocean,
                            "forest" to R.string.theme_forest,
                        ).forEach { (value, labelRes) ->
                            FilterChip(
                                selected = themeColor == value,
                                onClick = {
                                    scope.launch {
                                        settingsRepository.setThemeColor(value)
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
                headlineContent = { Text(stringResource(R.string.content_padding)) },
                supportingContent = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            "comfortable" to R.string.content_padding_comfortable,
                            "compact" to R.string.content_padding_compact,
                        ).forEach { (value, labelRes) ->
                            FilterChip(
                                selected = contentPaddingMode == value,
                                onClick = {
                                    scope.launch {
                                        settingsRepository.setContentPaddingMode(value)
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
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
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
    }

    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false },
            versionName = BuildConfig.VERSION_NAME ?: "\u2014",
            versionCode = BuildConfig.VERSION_CODE,
        )
    }
}

private sealed class UpdateUiState {
    data object Idle : UpdateUiState()
    data object Checking : UpdateUiState()
    data object Downloading : UpdateUiState()
    data class Result(val value: UpdateCheckResult) : UpdateUiState()
    data class InstallFailed(
        val versionName: String,
        val downloadUrl: String,
        val userMessage: String,
        val releaseNotes: String? = null,
    ) : UpdateUiState()
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

@Composable
private fun SettingsSectionSubtitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

private const val GITHUB_REPO_URL = "https://github.com/Darknetzz/jotty-android"
private const val GITHUB_RELEASES_URL = "$GITHUB_REPO_URL/releases"

@Composable
private fun AboutDialog(
    onDismiss: () -> Unit,
    versionName: String,
    versionCode: Int,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
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
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                when (val state = updateState) {
                    UpdateUiState.Idle -> {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    updateState = UpdateUiState.Checking
                                    updateState = UpdateUiState.Result(UpdateChecker.checkForUpdate(context))
                                }
                            },
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Icon(
                                Icons.Default.Update,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.check_for_updates))
                        }
                    }
                    is UpdateUiState.Checking, is UpdateUiState.Downloading -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(
                                    if (state is UpdateUiState.Downloading) stringResource(R.string.downloading)
                                    else stringResource(R.string.checking_for_updates),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (state is UpdateUiState.Downloading) {
                                if (downloadProgress != null) {
                                    LinearProgressIndicator(
                                        progress = { downloadProgress ?: 0f },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                    is UpdateUiState.Result -> when (val r = state.value) {
                        is UpdateCheckResult.UpdateAvailable -> UpdateAvailableContent(
                            versionName = r.versionName,
                            downloadUrl = r.downloadUrl,
                            releaseNotes = r.releaseNotes,
                            installFailedMessage = null,
                            onDownloadAndInstall = {
                                scope.launch {
                                    updateState = UpdateUiState.Downloading
                                    downloadProgress = null
                                    when (val result = UpdateChecker.downloadAndInstall(context, r.downloadUrl) { p ->
                                        downloadProgress = p
                                    }) {
                                        is InstallResult.Started -> updateState = UpdateUiState.Idle
                                        is InstallResult.Failed -> updateState = UpdateUiState.InstallFailed(
                                            r.versionName, r.downloadUrl, result.userMessage, r.releaseNotes,
                                        )
                                    }
                                    downloadProgress = null
                                }
                            },
                            onOpenReleasePage = { uriHandler.openUri(GITHUB_RELEASES_URL) },
                        )
                        is UpdateCheckResult.UpToDate -> {
                            Text(
                                stringResource(R.string.you_are_up_to_date),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = { updateState = UpdateUiState.Idle },
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(stringResource(R.string.check_for_updates))
                            }
                        }
                        is UpdateCheckResult.Error -> {
                            Text(
                                stringResource(R.string.update_check_error, r.message),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        updateState = UpdateUiState.Checking
                                        updateState = UpdateUiState.Result(UpdateChecker.checkForUpdate(context))
                                    }
                                },
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                    is UpdateUiState.InstallFailed -> UpdateAvailableContent(
                        versionName = state.versionName,
                        downloadUrl = state.downloadUrl,
                        releaseNotes = state.releaseNotes,
                        installFailedMessage = state.userMessage,
                        onDownloadAndInstall = {
                            scope.launch {
                                updateState = UpdateUiState.Downloading
                                downloadProgress = null
                                when (val result = UpdateChecker.downloadAndInstall(context, state.downloadUrl) { p ->
                                    downloadProgress = p
                                }) {
                                    is InstallResult.Started -> updateState = UpdateUiState.Idle
                                    is InstallResult.Failed -> updateState = UpdateUiState.InstallFailed(
                                        state.versionName, state.downloadUrl, result.userMessage, state.releaseNotes,
                                    )
                                }
                                downloadProgress = null
                            }
                        },
                        onOpenReleasePage = { uriHandler.openUri(GITHUB_RELEASES_URL) },
                    )
                }
            }
        },
    )
}

@Composable
private fun UpdateAvailableContent(
    versionName: String,
    downloadUrl: String,
    releaseNotes: String?,
    installFailedMessage: String?,
    onDownloadAndInstall: () -> Unit,
    onOpenReleasePage: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.update_available, versionName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        releaseNotes?.takeIf { it.isNotBlank() }?.let { notes ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.whats_new),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    MarkdownText(
                        markdown = notes,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        syntaxHighlightColor = MaterialTheme.colorScheme.surfaceVariant,
                        syntaxHighlightTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        installFailedMessage?.let { msg ->
            Text(
                stringResource(R.string.install_failed, msg),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onDownloadAndInstall,
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.download_and_install))
            }
            if (installFailedMessage != null) {
                TextButton(
                    onClick = onOpenReleasePage,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(
                        Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.open_release_page))
                }
            }
        }
    }
}
