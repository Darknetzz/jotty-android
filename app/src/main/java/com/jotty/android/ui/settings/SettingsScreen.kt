package com.jotty.android.ui.settings

import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jotty.android.BuildConfig
import com.jotty.android.R
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.encryption.BiometricPassphraseStore
import com.jotty.android.data.preferences.SettingsRepository
import com.jotty.android.data.updates.BundledChangelog
import com.jotty.android.data.updates.InstallResult
import com.jotty.android.data.updates.UpdateChannel
import com.jotty.android.data.updates.UpdateCheckResult
import com.jotty.android.data.updates.UpdateChecker
import com.jotty.android.data.updates.parseUpdateChannel
import com.jotty.android.ui.common.ChangelogDialog
import com.jotty.android.ui.common.UpdateStatusAlert
import com.jotty.android.ui.common.UpdateStatusAlertVariant
import com.jotty.android.ui.common.mainScreenTabContentPadding
import com.jotty.android.util.DebugLogExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun SettingsScreen(
    api: JottyApi?,
    settingsRepository: SettingsRepository,
    onDisconnect: () -> Unit,
    onManageInstances: () -> Unit = {},
    onAppearance: () -> Unit = {},
    onDashboard: () -> Unit = {},
    onBehavior: () -> Unit = {},
) {
    val settingsVm: SettingsViewModel = viewModel { SettingsViewModel() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val biometricStore = remember { BiometricPassphraseStore(context.applicationContext) }
    val biometricAvailability = remember { biometricStore.availabilityStatus() }
    val storedPassphraseCount by settingsVm.storedPassphraseCount.collectAsStateWithLifecycle()

    LaunchedEffect(biometricStore) {
        settingsVm.refreshBiometricCount(biometricStore)
    }
    var showClearBiometricConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val biometricClearedMsg = stringResource(R.string.biometric_passphrase_forgotten)
    val currentInstance by settingsRepository.currentInstance.collectAsStateWithLifecycle(initialValue = null)
    val contentPaddingMode by settingsRepository.contentPaddingMode.collectAsStateWithLifecycle(initialValue = "comfortable")
    val biometricAutoUnlockEnabled by settingsRepository.biometricAutoUnlockEnabled.collectAsStateWithLifecycle(initialValue = true)
    val biometricSaveOfferEnabled by settingsRepository.biometricSaveOfferEnabled.collectAsStateWithLifecycle(initialValue = true)
    val updateChannelPref by settingsRepository.updateChannel.collectAsStateWithLifecycle(initialValue = "stable")
    val isExportingLogs by settingsVm.isExportingLogs.collectAsStateWithLifecycle()
    val pendingLogFile by settingsVm.pendingLogFile.collectAsStateWithLifecycle()
    val logSavedPickerMsg = stringResource(R.string.export_debug_logs_saved_picker)
    val logSavedDownloadsFormat = stringResource(R.string.export_debug_logs_saved_downloads)
    val logSaveFailedMsg = stringResource(R.string.export_debug_logs_save_failed)
    val saveLogLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/plain"),
        ) { uri ->
            val file = pendingLogFile
            settingsVm.setPendingLogFile(null)
            if (uri == null || file == null) return@rememberLauncherForActivityResult
            scope.launch {
                val ok =
                    withContext(Dispatchers.IO) {
                        DebugLogExporter.copyToUri(context, file, uri)
                    }
                snackbarHostState.showSnackbar(
                    if (ok) logSavedPickerMsg else logSaveFailedMsg,
                )
            }
        }
    val healthOk by settingsVm.healthOk.collectAsStateWithLifecycle()
    val serverVersion by settingsVm.serverVersion.collectAsStateWithLifecycle()
    var showAboutDialog by remember { mutableStateOf(false) }
    val isRefreshing by settingsVm.isRefreshing.collectAsStateWithLifecycle()

    fun refreshConnection(showRefreshingIndicator: Boolean) {
        settingsVm.refreshConnection(api, showRefreshingIndicator)
    }

    LaunchedEffect(api) {
        refreshConnection(showRefreshingIndicator = false)
    }

    val pullRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { refreshConnection(showRefreshingIndicator = true) },
        state = pullRefreshState,
    ) {
        Box(Modifier.fillMaxSize()) {
            val contentVerticalDp = if (contentPaddingMode == "compact") 8 else 16
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .mainScreenTabContentPadding(topComfortDp = contentVerticalDp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Spacer(modifier = Modifier.height(8.dp))

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
                                        text =
                                            when (healthOk) {
                                                true -> stringResource(R.string.connected)
                                                false -> stringResource(R.string.server_unreachable)
                                                null -> "\u2014"
                                            },
                                        style = MaterialTheme.typography.labelSmall,
                                        color =
                                            when (healthOk) {
                                                true -> MaterialTheme.colorScheme.primary
                                                false -> MaterialTheme.colorScheme.error
                                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                    )
                                }
                            },
                            leadingContent = { Icon(Icons.Default.Link, contentDescription = stringResource(R.string.cd_link)) },
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
                            leadingContent = {
                                Icon(
                                    Icons.Default.ManageAccounts,
                                    contentDescription = stringResource(R.string.manage_instances),
                                )
                            },
                            modifier = Modifier.clickable(onClick = onManageInstances),
                        )
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.dashboard_overview)) },
                            supportingContent = {
                                Text(
                                    stringResource(R.string.dashboard_overview_description),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Default.Dashboard,
                                    contentDescription = stringResource(R.string.dashboard_overview),
                                )
                            },
                            modifier = Modifier.clickable(onClick = onDashboard),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ─── Appearance ───────────────────────────────────────────────────────
                SettingsSectionTitle(stringResource(R.string.appearance))
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onAppearance,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = stringResource(R.string.appearance))
                        Column {
                            Text(stringResource(R.string.appearance), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.appearance_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ─── Behavior ───────────────────────────────────────────────────────────
                SettingsSectionTitle(stringResource(R.string.settings_category_behavior))
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBehavior,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.settings_category_behavior))
                        Column {
                            Text(stringResource(R.string.settings_category_behavior), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.behavior_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ─── Security (biometric note passphrases) ─────────────────────────────
                SettingsSectionTitle(stringResource(R.string.settings_category_security))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    val biometricAvailable =
                        biometricAvailability == BiometricPassphraseStore.BiometricAvailability.Available
                    val biometricStatusText =
                        when (biometricAvailability) {
                            BiometricPassphraseStore.BiometricAvailability.Available ->
                                stringResource(R.string.biometric_status_available)
                            BiometricPassphraseStore.BiometricAvailability.NotEnrolled ->
                                stringResource(R.string.biometric_status_not_enrolled)
                            BiometricPassphraseStore.BiometricAvailability.NotSupported ->
                                stringResource(R.string.biometric_status_not_supported)
                        }
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.biometric_unlock_status_label)) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(biometricStatusText, style = MaterialTheme.typography.bodySmall)
                                if (biometricAvailable && storedPassphraseCount > 0) {
                                    Text(
                                        stringResource(R.string.biometric_stored_count, storedPassphraseCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                    )
                    if (biometricAvailable) {
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.biometric_auto_unlock)) },
                            supportingContent = {
                                Text(
                                    stringResource(R.string.biometric_auto_unlock_description),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = biometricAutoUnlockEnabled,
                                    onCheckedChange = {
                                        scope.launch {
                                            settingsRepository.setBiometricAutoUnlockEnabled(it)
                                        }
                                    },
                                )
                            },
                        )
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.biometric_save_offer)) },
                            supportingContent = {
                                Text(
                                    stringResource(R.string.biometric_save_offer_description),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = biometricSaveOfferEnabled,
                                    onCheckedChange = {
                                        scope.launch {
                                            settingsRepository.setBiometricSaveOfferEnabled(it)
                                        }
                                    },
                                )
                            },
                        )
                        if (storedPassphraseCount > 0) {
                            HorizontalDivider()
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.biometric_clear_all)) },
                                modifier = Modifier.clickable { showClearBiometricConfirm = true },
                            )
                        }
                    }
                }

                if (showClearBiometricConfirm) {
                    AlertDialog(
                        onDismissRequest = { showClearBiometricConfirm = false },
                        title = { Text(stringResource(R.string.biometric_clear_all_confirm_title)) },
                        text = { Text(stringResource(R.string.biometric_clear_all_confirm_message)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showClearBiometricConfirm = false
                                    settingsVm.clearBiometricPassphrases(biometricStore)
                                    scope.launch { snackbarHostState.showSnackbar(biometricClearedMsg) }
                                },
                            ) {
                                Text(stringResource(R.string.clear))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearBiometricConfirm = false }) {
                                Text(stringResource(R.string.cancel))
                            }
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
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.disconnect))
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

                // ─── Troubleshooting ────────────────────────────────────────────────────
                SettingsSectionTitle(stringResource(R.string.settings_category_troubleshooting))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.export_debug_logs)) },
                        supportingContent = {
                            Text(
                                stringResource(R.string.export_debug_logs_description),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.BugReport,
                                contentDescription = stringResource(R.string.export_debug_logs),
                            )
                        },
                        trailingContent = {
                            if (isExportingLogs) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                settingsVm.setExportingLogs(true)
                                                val instance = currentInstance
                                                val writeResult =
                                                    withContext(Dispatchers.IO) {
                                                        DebugLogExporter.writeReport(context, instance)
                                                    }
                                                settingsVm.setExportingLogs(false)
                                                when (writeResult) {
                                                    is DebugLogExporter.WriteResult.Failed ->
                                                        snackbarHostState.showSnackbar(writeResult.message)
                                                    is DebugLogExporter.WriteResult.Ok ->
                                                        when (
                                                            val saveResult =
                                                                withContext(Dispatchers.IO) {
                                                                    DebugLogExporter.saveToDownloads(
                                                                        context,
                                                                        writeResult.file,
                                                                    )
                                                                }
                                                        ) {
                                                            is DebugLogExporter.SaveResult.Saved ->
                                                                snackbarHostState.showSnackbar(
                                                                    String.format(
                                                                        Locale.getDefault(),
                                                                        logSavedDownloadsFormat,
                                                                        saveResult.displayName,
                                                                    ),
                                                                )
                                                            is DebugLogExporter.SaveResult.NeedsPicker -> {
                                                                settingsVm.setPendingLogFile(saveResult.file)
                                                                saveLogLauncher.launch(saveResult.suggestedName)
                                                            }
                                                            is DebugLogExporter.SaveResult.Failed ->
                                                                snackbarHostState.showSnackbar(saveResult.message)
                                                        }
                                                }
                                            }
                                        },
                                    ) {
                                        Text(stringResource(R.string.export_debug_logs_save))
                                    }
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                settingsVm.setExportingLogs(true)
                                                val instance = currentInstance
                                                val writeResult =
                                                    withContext(Dispatchers.IO) {
                                                        DebugLogExporter.writeReport(context, instance)
                                                    }
                                                settingsVm.setExportingLogs(false)
                                                when (writeResult) {
                                                    is DebugLogExporter.WriteResult.Failed ->
                                                        snackbarHostState.showSnackbar(writeResult.message)
                                                    is DebugLogExporter.WriteResult.Ok ->
                                                        when (
                                                            val shareResult =
                                                                DebugLogExporter.shareReport(
                                                                    context,
                                                                    writeResult.file,
                                                                )
                                                        ) {
                                                            is DebugLogExporter.ShareResult.Failed ->
                                                                snackbarHostState.showSnackbar(shareResult.message)
                                                            is DebugLogExporter.ShareResult.Started -> Unit
                                                        }
                                                }
                                            }
                                        },
                                    ) {
                                        Text(stringResource(R.string.export_debug_logs_action))
                                    }
                                }
                            }
                        },
                    )
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
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.about))
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
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false },
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            currentBuildDateUtc = BuildConfig.BUILD_DATE_UTC,
            serverVersion = serverVersion,
            updateChannelPref = updateChannelPref,
            onUpdateChannelChange = { ch ->
                scope.launch { settingsRepository.setUpdateChannel(ch) }
            },
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
        val buildDateUtc: String?,
        val downloadUrl: String,
        val userMessage: String,
        val releaseNotes: String? = null,
        val changelogMarkdown: String? = null,
    ) : UpdateUiState()
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
private const val GITHUB_DEV_RELEASE_URL = "$GITHUB_REPO_URL/releases/tag/dev-latest"
private const val ABOUT_EASTER_EGG_TAPS = 7
private const val ABOUT_EASTER_EGG_RESET_MS = 700L

@Composable
private fun AboutDialog(
    onDismiss: () -> Unit,
    versionName: String,
    versionCode: Int,
    currentBuildDateUtc: String?,
    serverVersion: String? = null,
    updateChannelPref: String,
    onUpdateChannelChange: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val resources = context.resources
    val scope = rememberCoroutineScope()
    val parsedChannel = parseUpdateChannel(updateChannelPref)
    val releasePageUrl = if (parsedChannel == UpdateChannel.Dev) GITHUB_DEV_RELEASE_URL else GITHUB_RELEASES_URL
    var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var versionTapCount by remember { mutableIntStateOf(0) }
    var versionLastTapElapsedMs by remember { mutableLongStateOf(0L) }
    var showAboutEasterEgg by remember { mutableStateOf(false) }
    var bundledChangelog by remember { mutableStateOf<BundledChangelog?>(null) }
    var changelogDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showChangelogUnavailable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        bundledChangelog = BundledChangelog.load(context)
    }

    LaunchedEffect(updateChannelPref) {
        updateState = UpdateUiState.Idle
    }

    fun openInstalledChangelog() {
        val key = BundledChangelog.sectionKeyForInstalled(versionName)
        val markdown =
            BundledChangelog.resolveMarkdown(
                changelog = bundledChangelog,
                sectionKey = key,
                useDevRollingSection = false,
                fallbackMarkdown = null,
            )
        if (markdown != null) {
            changelogDialog = resources.getString(R.string.changelog_title_installed, key) to markdown
        } else {
            showChangelogUnavailable = true
        }
    }

    fun openUpdateChangelog(
        remoteVersionLabel: String,
        releaseNotes: String?,
        preResolvedMarkdown: String? = null,
    ) {
        val sectionKey = BundledChangelog.sectionKeyForRemote(remoteVersionLabel, parsedChannel)
        val markdown =
            preResolvedMarkdown?.trim()?.takeIf { it.isNotBlank() }
                ?: BundledChangelog.resolveMarkdown(
                    changelog = bundledChangelog,
                    sectionKey = sectionKey,
                    useDevRollingSection = parsedChannel == UpdateChannel.Dev,
                    fallbackMarkdown = releaseNotes,
                )
        if (markdown != null) {
            changelogDialog =
                resources.getString(R.string.changelog_title_update, remoteVersionLabel) to markdown
        } else {
            showChangelogUnavailable = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
        title = { Text(stringResource(R.string.about_jotty_android)) },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.about_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        val now = SystemClock.elapsedRealtime()
                                        if (versionLastTapElapsedMs != 0L && now - versionLastTapElapsedMs > ABOUT_EASTER_EGG_RESET_MS) {
                                            versionTapCount = 0
                                        }
                                        versionLastTapElapsedMs = now
                                        versionTapCount++
                                        if (versionTapCount >= ABOUT_EASTER_EGG_TAPS) {
                                            versionTapCount = 0
                                            versionLastTapElapsedMs = 0L
                                            showAboutEasterEgg = true
                                        }
                                    },
                                )
                            },
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.version), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.version_format, versionName, versionCode), style = MaterialTheme.typography.bodyMedium)
                }
                formatBuildDateLabel(currentBuildDateUtc)?.let { buildDate ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.build_date), style = MaterialTheme.typography.bodyMedium)
                        Text(buildDate, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                serverVersion?.let { version ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.server_version), style = MaterialTheme.typography.bodyMedium)
                        Text(version, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                HorizontalDivider()
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    ViewChangelogButton(
                        label = stringResource(R.string.view_changelog),
                        onClick = { openInstalledChangelog() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ViewSourceOnGitHubButton(
                        onClick = { uriHandler.openUri(GITHUB_REPO_URL) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                HorizontalDivider()
                SettingsSectionSubtitle(stringResource(R.string.update_channel_label))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = parsedChannel == UpdateChannel.Stable,
                        onClick = { onUpdateChannelChange("stable") },
                        label = { Text(stringResource(R.string.update_channel_stable)) },
                    )
                    FilterChip(
                        selected = parsedChannel == UpdateChannel.Dev,
                        onClick = { onUpdateChannelChange("dev") },
                        label = { Text(stringResource(R.string.update_channel_dev)) },
                    )
                }
                if (parsedChannel == UpdateChannel.Dev) {
                    Text(
                        text = stringResource(R.string.update_dev_channel_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (UpdateChecker.isDevBuild() && parsedChannel == UpdateChannel.Stable) {
                    UpdateStatusAlert(
                        message = stringResource(R.string.update_dev_on_stable_channel_hint),
                        variant = UpdateStatusAlertVariant.Info,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                when (val state = updateState) {
                    UpdateUiState.Idle -> {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    updateState = UpdateUiState.Checking
                                    updateState =
                                        UpdateUiState.Result(
                                            UpdateChecker.checkForUpdate(context, parsedChannel),
                                        )
                                }
                            },
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Icon(
                                Icons.Default.Update,
                                contentDescription = stringResource(R.string.check_for_updates),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.check_for_updates))
                        }
                    }
                    is UpdateUiState.Checking, is UpdateUiState.Downloading -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            UpdateStatusAlert(
                                message =
                                    if (state is UpdateUiState.Downloading) {
                                        stringResource(R.string.downloading)
                                    } else {
                                        stringResource(R.string.checking_for_updates)
                                    },
                                variant = UpdateStatusAlertVariant.Loading,
                            )
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
                    is UpdateUiState.Result ->
                        when (val r = state.value) {
                            is UpdateCheckResult.UpdateAvailable ->
                                UpdateAvailableContent(
                                    versionName = r.versionName,
                                    buildDateUtc = r.buildDateUtc,
                                    downloadUrl = r.downloadUrl,
                                    releaseNotes = r.releaseNotes,
                                    installFailedMessage = null,
                                    inAppInstallBlocked = r.inAppInstallBlocked,
                                    inAppInstallBlockedMessage = r.inAppInstallBlockedMessage,
                                    showSigningHints = parsedChannel == UpdateChannel.Dev,
                                    onViewChangelog = {
                                        openUpdateChangelog(
                                            r.versionName,
                                            r.releaseNotes,
                                            r.changelogMarkdown,
                                        )
                                    },
                                    onDownloadAndInstall = {
                                        scope.launch {
                                            updateState = UpdateUiState.Downloading
                                            downloadProgress = null
                                            when (
                                                val result =
                                                    UpdateChecker.downloadAndInstall(context, r.downloadUrl) { p ->
                                                        downloadProgress = p
                                                    }
                                            ) {
                                                is InstallResult.Started -> updateState = UpdateUiState.Idle
                                                is InstallResult.Failed ->
                                                    updateState =
                                                        UpdateUiState.InstallFailed(
                                                            r.versionName,
                                                            r.buildDateUtc,
                                                            r.downloadUrl,
                                                            result.userMessage,
                                                            r.releaseNotes,
                                                            r.changelogMarkdown,
                                                        )
                                            }
                                            downloadProgress = null
                                        }
                                    },
                                    onOpenReleasePage = { uriHandler.openUri(releasePageUrl) },
                                )
                            is UpdateCheckResult.UpToDate -> {
                                UpdateStatusAlert(
                                    message = stringResource(R.string.you_are_up_to_date),
                                    variant = UpdateStatusAlertVariant.Success,
                                )
                                TextButton(
                                    onClick = { updateState = UpdateUiState.Idle },
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text(stringResource(R.string.check_for_updates))
                                }
                            }
                            is UpdateCheckResult.Error -> {
                                UpdateStatusAlert(
                                    message = stringResource(R.string.update_check_error, r.message),
                                    variant = UpdateStatusAlertVariant.Danger,
                                )
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            updateState = UpdateUiState.Checking
                                            updateState =
                                                UpdateUiState.Result(
                                                    UpdateChecker.checkForUpdate(context, parsedChannel),
                                                )
                                        }
                                    },
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        }
                    is UpdateUiState.InstallFailed ->
                        UpdateAvailableContent(
                            versionName = state.versionName,
                            buildDateUtc = state.buildDateUtc,
                            downloadUrl = state.downloadUrl,
                            releaseNotes = state.releaseNotes,
                            installFailedMessage = state.userMessage,
                            inAppInstallBlocked = UpdateChecker.stableUpdateRequiresFreshInstall(),
                            inAppInstallBlockedMessage = null,
                            showSigningHints = true,
                            onViewChangelog = {
                                openUpdateChangelog(
                                    state.versionName,
                                    state.releaseNotes,
                                    state.changelogMarkdown,
                                )
                            },
                            onDownloadAndInstall = {
                                scope.launch {
                                    updateState = UpdateUiState.Downloading
                                    downloadProgress = null
                                    when (
                                        val result =
                                            UpdateChecker.downloadAndInstall(context, state.downloadUrl) { p ->
                                                downloadProgress = p
                                            }
                                    ) {
                                        is InstallResult.Started -> updateState = UpdateUiState.Idle
                                        is InstallResult.Failed ->
                                            updateState =
                                                UpdateUiState.InstallFailed(
                                                    state.versionName,
                                                    state.buildDateUtc,
                                                    state.downloadUrl,
                                                    result.userMessage,
                                                    state.releaseNotes,
                                                    state.changelogMarkdown,
                                                )
                                    }
                                    downloadProgress = null
                                }
                            },
                            onOpenReleasePage = { uriHandler.openUri(releasePageUrl) },
                        )
                }
            }
        },
    )

    changelogDialog?.let { (title, markdown) ->
        ChangelogDialog(
            title = title,
            markdown = markdown,
            onDismiss = { changelogDialog = null },
        )
    }

    if (showChangelogUnavailable) {
        AlertDialog(
            onDismissRequest = { showChangelogUnavailable = false },
            confirmButton = {
                TextButton(onClick = { showChangelogUnavailable = false }) {
                    Text(stringResource(R.string.close))
                }
            },
            title = { Text(stringResource(R.string.view_changelog)) },
            text = { Text(stringResource(R.string.changelog_unavailable)) },
        )
    }

    if (showAboutEasterEgg) {
        AlertDialog(
            onDismissRequest = { showAboutEasterEgg = false },
            confirmButton = {
                TextButton(onClick = { showAboutEasterEgg = false }) {
                    Text(stringResource(R.string.close))
                }
            },
            title = { Text(stringResource(R.string.about_easter_egg_title)) },
            text = {
                Text(
                    stringResource(R.string.about_easter_egg_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
        )
    }
}

@Composable
private fun ViewChangelogButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AboutLinkButton(
        label = label,
        onClick = onClick,
        modifier = modifier,
        icon = {
            Icon(
                Icons.AutoMirrored.Filled.Article,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
    )
}

@Composable
private fun ViewSourceOnGitHubButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.view_source_github)
    AboutLinkButton(
        label = label,
        onClick = onClick,
        modifier = modifier,
        icon = {
            Icon(
                Icons.Default.Link,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
    )
}

@Composable
private fun AboutLinkButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 36.dp, minWidth = 0.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        icon()
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, maxLines = 2)
    }
}

@Composable
private fun UpdateAvailableContent(
    versionName: String,
    buildDateUtc: String?,
    downloadUrl: String,
    releaseNotes: String?,
    installFailedMessage: String?,
    inAppInstallBlocked: Boolean,
    inAppInstallBlockedMessage: String?,
    showSigningHints: Boolean = false,
    onViewChangelog: () -> Unit,
    onDownloadAndInstall: () -> Unit,
    onOpenReleasePage: () -> Unit,
) {
    val showReleasePageAction = inAppInstallBlocked || installFailedMessage != null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UpdateStatusAlert(
            message = stringResource(R.string.update_available, versionName),
            variant = UpdateStatusAlertVariant.Info,
        )
        formatBuildDateLabel(buildDateUtc)?.let { buildDate ->
            Text(
                text = stringResource(R.string.update_build_date, buildDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ViewChangelogButton(
            label = stringResource(R.string.view_changelog_for_version, versionName),
            onClick = onViewChangelog,
        )
        if (showSigningHints) {
            Text(
                text = stringResource(R.string.update_signing_mismatch_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        inAppInstallBlockedMessage?.let { msg ->
            UpdateStatusAlert(
                message = msg,
                variant = UpdateStatusAlertVariant.Danger,
            )
        }
        installFailedMessage?.let { msg ->
            UpdateStatusAlert(
                message = stringResource(R.string.install_failed, msg),
                variant = UpdateStatusAlertVariant.Danger,
            )
            if (!inAppInstallBlocked) {
                Text(
                    text = stringResource(R.string.install_failed_open_release_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!inAppInstallBlocked) {
                TextButton(
                    onClick = onDownloadAndInstall,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = stringResource(R.string.cd_download),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.download_and_install))
                }
            }
            if (showReleasePageAction) {
                TextButton(
                    onClick = onOpenReleasePage,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(
                        Icons.Default.Link,
                        contentDescription = stringResource(R.string.open_release_page),
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

private fun formatBuildDateLabel(rawUtcIso: String?): String? {
    if (rawUtcIso.isNullOrBlank()) return null
    val formatter =
        DateTimeFormatter
            .ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
    return try {
        OffsetDateTime.parse(rawUtcIso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate().format(formatter)
    } catch (_: DateTimeParseException) {
        try {
            Instant.parse(rawUtcIso).atZone(ZoneId.systemDefault()).toLocalDate().format(formatter)
        } catch (_: DateTimeParseException) {
            rawUtcIso
        }
    }
}
