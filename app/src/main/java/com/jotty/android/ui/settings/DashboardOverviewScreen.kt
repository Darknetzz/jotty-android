package com.jotty.android.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jotty.android.R
import com.jotty.android.data.api.AdminOverviewResponse
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.SummaryData
import com.jotty.android.data.preferences.SettingsRepository
import com.jotty.android.ui.common.EmptyState
import com.jotty.android.ui.common.LoadingState
import com.jotty.android.ui.common.mainScreenTabContentPadding
import kotlinx.coroutines.launch
import retrofit2.HttpException

@Composable
fun DashboardOverviewScreen(
    api: JottyApi?,
    settingsRepository: SettingsRepository,
) {
    val scope = rememberCoroutineScope()
    val contentPaddingMode by settingsRepository.contentPaddingMode.collectAsStateWithLifecycle(initialValue = "comfortable")
    val contentVerticalDp = if (contentPaddingMode == "compact") 8 else 16
    var adminOverview by remember { mutableStateOf<AdminOverviewResponse?>(null) }
    var summary by remember { mutableStateOf<SummaryData?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var initialLoadDone by remember { mutableStateOf(false) }

    fun refreshDashboard(showRefreshingIndicator: Boolean) {
        scope.launch {
            if (showRefreshingIndicator) isRefreshing = true
            try {
                api?.let { a ->
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
                } ?: run {
                    summary = null
                    adminOverview = null
                }
            } finally {
                initialLoadDone = true
                if (showRefreshingIndicator) isRefreshing = false
            }
        }
    }

    LaunchedEffect(api) {
        refreshDashboard(showRefreshingIndicator = false)
    }

    val hasContent = dashboardHasVisibleContent(summary, adminOverview)
    val pullRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { refreshDashboard(showRefreshingIndicator = true) },
        state = pullRefreshState,
    ) {
        when {
            !initialLoadDone && !hasContent ->
                LoadingState(Modifier.fillMaxSize(), stringResource(R.string.loading))
            initialLoadDone && !hasContent ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                ) {
                    EmptyState(
                        icon = Icons.Default.Dashboard,
                        title = stringResource(R.string.dashboard_overview_empty_title),
                        subtitle = stringResource(R.string.dashboard_overview_empty_subtitle),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            else ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .mainScreenTabContentPadding(topComfortDp = contentVerticalDp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    summary?.let { DashboardSummaryCard(it) }
                    if (summary != null && adminOverview != null) Spacer(modifier = Modifier.height(8.dp))
                    adminOverview?.let { AdminOverviewCard(it) }
                    Spacer(modifier = Modifier.height(32.dp))
                }
        }
    }
}

private fun dashboardHasVisibleContent(
    summary: SummaryData?,
    adminOverview: AdminOverviewResponse?,
): Boolean {
    summary?.let {
        val notesTotal = it.notes?.total ?: 0
        val listsTotal = it.checklists?.total ?: 0
        if (notesTotal > 0 || listsTotal > 0 || it.items != null || it.tasks != null || it.username != null) {
            return true
        }
    }
    adminOverview?.let {
        if (it.users != null || it.checklists != null || it.notes != null) return true
    }
    return false
}

@Composable
private fun DashboardSummaryCard(summary: SummaryData) {
    val notesTotal = summary.notes?.total ?: 0
    val listsTotal = summary.checklists?.total ?: 0
    val items = summary.items
    val tasks = summary.tasks
    val hasAny =
        notesTotal > 0 || listsTotal > 0 || items != null || tasks != null

    if (!hasAny) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitleRow(
                text = stringResource(R.string.dashboard_personal_section_title),
                icon = Icons.Default.Person,
            )
            summary.username?.let { u ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.user_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(u, style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            }
            DashboardStatGrid(
                chips =
                    buildList {
                        if (notesTotal > 0 || summary.notes != null) add(stringResource(R.string.stat_notes) to notesTotal)
                        if (listsTotal > 0 || summary.checklists != null) add(stringResource(R.string.stat_checklists) to listsTotal)
                    },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )

            items?.let { i ->
                DashboardBreakdown(
                    title = stringResource(R.string.dashboard_items_title),
                    icon = Icons.Default.Checklist,
                    completionRate = i.completionRate,
                    chips =
                        buildList {
                            i.total?.let { add(stringResource(R.string.stat_total) to it) }
                            i.completed?.let { add(stringResource(R.string.stat_completed) to it) }
                            i.pending?.let { add(stringResource(R.string.stat_pending) to it) }
                        },
                )
            }

            tasks?.let { t ->
                DashboardBreakdown(
                    title = stringResource(R.string.dashboard_tasks_title),
                    icon = Icons.Default.TaskAlt,
                    completionRate = t.completionRate,
                    chips =
                        buildList {
                            t.total?.let { add(stringResource(R.string.stat_total) to it) }
                            t.completed?.let { add(stringResource(R.string.stat_completed) to it) }
                            t.inProgress?.let { add(stringResource(R.string.stat_in_progress) to it) }
                            t.todo?.let { add(stringResource(R.string.stat_todo) to it) }
                        },
                )
            }
        }
    }
}

@Composable
private fun DashboardBreakdown(
    title: String,
    icon: ImageVector,
    completionRate: Int?,
    chips: List<Pair<String, Int>>,
) {
    if (chips.isEmpty() && completionRate == null) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitleRow(
                text = title,
                icon = icon,
                textStyle = MaterialTheme.typography.labelLarge,
                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DashboardStatGrid(
                chips = chips,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
            completionRate?.let { rate ->
                LinearProgressIndicator(
                    progress = { (rate.coerceIn(0, 100)) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Text(
                    stringResource(R.string.dashboard_completion_format, rate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AdminOverviewCard(overview: AdminOverviewResponse) {
    val hasAny = overview.users != null || overview.checklists != null || overview.notes != null
    if (!hasAny) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitleRow(
                text = stringResource(R.string.dashboard_admin_section_title),
                icon = Icons.Default.AdminPanelSettings,
            )
            DashboardStatGrid(
                chips =
                    buildList {
                        overview.users?.let { add(stringResource(R.string.stat_users) to it) }
                        overview.checklists?.let { add(stringResource(R.string.stat_checklists) to it) }
                        overview.notes?.let { add(stringResource(R.string.stat_notes) to it) }
                    },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SectionTitleRow(
    text: String,
    icon: ImageVector,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp),
        )
        Text(text = text, style = textStyle, color = textColor)
    }
}

@Composable
private fun DashboardStatGrid(
    chips: List<Pair<String, Int>>,
    containerColor: Color,
    contentColor: Color,
) {
    if (chips.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { (label, value) ->
                    StatChip(
                        label = label,
                        value = value,
                        modifier = Modifier.weight(1f),
                        containerColor = containerColor,
                        contentColor = contentColor,
                    )
                }
                repeat(2 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text("$value", style = MaterialTheme.typography.titleMedium, color = contentColor)
            Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.9f))
        }
    }
}
