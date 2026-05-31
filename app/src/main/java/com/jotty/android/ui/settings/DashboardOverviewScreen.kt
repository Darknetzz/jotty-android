package com.jotty.android.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            summary.username?.let { u ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.user_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(u, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (notesTotal > 0 || summary.notes != null) StatChip(stringResource(R.string.stat_notes), notesTotal)
                if (listsTotal > 0 || summary.checklists != null) StatChip(stringResource(R.string.stat_checklists), listsTotal)
            }

            items?.let { i ->
                DashboardBreakdown(
                    title = stringResource(R.string.dashboard_items_title),
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
    completionRate: Int?,
    chips: List<Pair<String, Int>>,
) {
    if (chips.isEmpty() && completionRate == null) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        if (chips.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                chips.forEach { (label, value) -> StatChip(label, value) }
            }
        }
        completionRate?.let { rate ->
            LinearProgressIndicator(
                progress = { (rate.coerceIn(0, 100)) / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
            )
            Text(
                stringResource(R.string.dashboard_completion_format, rate),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun AdminOverviewCard(overview: AdminOverviewResponse) {
    val hasAny = overview.users != null || overview.checklists != null || overview.notes != null
    if (!hasAny) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun StatChip(
    label: String,
    value: Int,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
