package com.jotty.android.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jotty.android.R
import com.jotty.android.data.api.ApiClient
import com.jotty.android.data.preferences.JottyInstance
import com.jotty.android.data.preferences.SettingsRepository
import com.jotty.android.ui.checklists.OfflineChecklistsScreen
import com.jotty.android.ui.common.LoadingState
import com.jotty.android.ui.common.LocalReducedMotionEnabled
import com.jotty.android.ui.common.accentColor
import com.jotty.android.ui.common.navEnterTransition
import com.jotty.android.ui.common.navExitTransition
import com.jotty.android.ui.common.navPopEnterTransition
import com.jotty.android.ui.common.navPopExitTransition
import com.jotty.android.ui.common.LocalMainTabTopBarController
import com.jotty.android.ui.common.MainTabTopBarActions
import com.jotty.android.ui.common.MainTabTopBarSyncSlot
import com.jotty.android.ui.common.ProvideMainTabTopBarController
import com.jotty.android.ui.notes.OfflineNotesScreen
import com.jotty.android.ui.settings.AppearanceSettingsScreen
import com.jotty.android.ui.settings.BehaviorSettingsScreen
import com.jotty.android.ui.settings.DashboardOverviewScreen
import com.jotty.android.ui.settings.SettingsScreen
import com.jotty.android.ui.setup.SetupScreen
import com.jotty.android.util.createNoteImageLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val ROUTE_MANAGE_INSTANCES = "manage_instances"
private const val ROUTE_APPEARANCE = "appearance"
private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_BEHAVIOR = "behavior"

sealed class MainRoute(val route: String, val titleRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Checklists : MainRoute("checklists", R.string.nav_checklists, Icons.Default.Checklist)

    data object Notes : MainRoute("notes", R.string.nav_notes, Icons.AutoMirrored.Filled.Note)

    data object Settings : MainRoute("settings", R.string.nav_settings, Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    settingsRepository: SettingsRepository,
    onDisconnect: () -> Unit = {},
    deepLinkNoteId: MutableState<String?>? = null,
    sharedNoteText: MutableState<String?>? = null,
) {
    val navController = rememberNavController()
    var startDestination by rememberSaveable { mutableStateOf<String?>(null) }
    var checklistsTabReselectToken by rememberSaveable { mutableIntStateOf(0) }
    var notesTabReselectToken by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        if (startDestination == null) {
            startDestination = settingsRepository.startTab.first() ?: MainRoute.Checklists.route
        }
    }
    LaunchedEffect(deepLinkNoteId?.value) {
        val id = deepLinkNoteId?.value
        if (!id.isNullOrBlank()) {
            navController.navigate(MainRoute.Notes.route) {
                popUpTo(MainRoute.Checklists.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    LaunchedEffect(sharedNoteText?.value) {
        if (sharedNoteText?.value != null) {
            navController.navigate(MainRoute.Notes.route) {
                popUpTo(MainRoute.Checklists.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val instances by settingsRepository.instances.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentInstance by settingsRepository.currentInstance.collectAsStateWithLifecycle(initialValue = null)
    val serverUrl by settingsRepository.serverUrl.collectAsStateWithLifecycle(initialValue = null)
    val apiKey by settingsRepository.apiKey.collectAsStateWithLifecycle(initialValue = null)
    val swipeToDeleteEnabled by settingsRepository.swipeToDeleteEnabled.collectAsStateWithLifecycle(initialValue = false)
    val reducedMotion = LocalReducedMotionEnabled.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val selectedRoute =
        when (currentRoute) {
            ROUTE_MANAGE_INSTANCES, ROUTE_APPEARANCE, ROUTE_DASHBOARD, ROUTE_BEHAVIOR -> MainRoute.Settings.route
            else -> currentRoute
        }
    val titleRes =
        when (currentRoute) {
            MainRoute.Checklists.route -> MainRoute.Checklists.titleRes
            MainRoute.Notes.route -> MainRoute.Notes.titleRes
            MainRoute.Settings.route -> MainRoute.Settings.titleRes
            ROUTE_MANAGE_INSTANCES -> R.string.manage_instances
            ROUTE_APPEARANCE -> R.string.appearance
            ROUTE_DASHBOARD -> R.string.dashboard_overview
            ROUTE_BEHAVIOR -> R.string.settings_category_behavior
            else -> R.string.app_name
        }

    val imageLoader =
        remember(context, serverUrl, apiKey) {
            val url = serverUrl
            val key = apiKey
            createNoteImageLoader(context, url, key)
        }

    val api =
        remember(serverUrl, apiKey) {
            val url = serverUrl
            val key = apiKey
            if (!url.isNullOrBlank() && !key.isNullOrBlank()) {
                ApiClient.create(url, key)
            } else {
                null
            }
        }

    val mainRoutes = listOf(MainRoute.Checklists, MainRoute.Notes, MainRoute.Settings)
    val showBottomBar = currentRoute in mainRoutes.map { it.route }
    fun onMainRouteClick(route: MainRoute) {
        if (selectedRoute == route.route) {
            when (route) {
                MainRoute.Checklists -> checklistsTabReselectToken++
                MainRoute.Notes -> notesTabReselectToken++
                MainRoute.Settings -> {
                    if (currentRoute != MainRoute.Settings.route) {
                        navController.popBackStack(MainRoute.Settings.route, false)
                    }
                }
            }
        } else {
            navController.navigate(route.route) {
                popUpTo(MainRoute.Checklists.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    ProvideMainTabTopBarController {
        val topBarController = LocalMainTabTopBarController.current
        val tabTopBarState = topBarController.state

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(titleRes))
                            val barState = tabTopBarState
                            if (barState != null && barState.showSyncStatus) {
                                Spacer(modifier = Modifier.weight(1f))
                                MainTabTopBarSyncSlot(
                                    isOnline = barState.isOnline,
                                    isSyncing = barState.isSyncing,
                                    lastSyncAttemptEpochMs = barState.lastSyncAttemptEpochMs,
                                    lastSyncDurationText = barState.lastSyncDurationText,
                                    lastSyncError = barState.lastSyncError,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (
                            currentRoute == ROUTE_MANAGE_INSTANCES ||
                                currentRoute == ROUTE_APPEARANCE ||
                                currentRoute == ROUTE_DASHBOARD ||
                                currentRoute == ROUTE_BEHAVIOR
                        ) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        } else {
                            InstanceSwitcher(
                                instances = instances,
                                currentInstance = currentInstance,
                                onSelectInstance = { id ->
                                    scope.launch { settingsRepository.setCurrentInstanceId(id) }
                                },
                                onManageInstances = { navController.navigate(ROUTE_MANAGE_INSTANCES) },
                            )
                        }
                    },
                    actions = {
                        tabTopBarState?.let { MainTabTopBarActions(it) }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                )
            },
            bottomBar = {
                if (showBottomBar && reducedMotion) {
                    Surface(
                        modifier = Modifier.windowInsetsPadding(NavigationBarDefaults.windowInsets),
                        tonalElevation = 3.dp,
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            mainRoutes.forEach { route ->
                                val selected = selectedRoute == route.route
                                val color =
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                TextButton(
                                    onClick = { onMainRouteClick(route) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            route.icon,
                                            contentDescription = stringResource(route.titleRes),
                                            tint = color,
                                        )
                                        Text(
                                            text = stringResource(route.titleRes),
                                            color = color,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (showBottomBar) {
                    NavigationBar {
                        mainRoutes.forEach { route ->
                            NavigationBarItem(
                                selected = selectedRoute == route.route,
                                onClick = { onMainRouteClick(route) },
                                icon = { Icon(route.icon, contentDescription = stringResource(route.titleRes)) },
                                label = { Text(stringResource(route.titleRes)) },
                            )
                        }
                    }
                }
            },
    ) { padding ->
        val currentApi = api
        val currentStart = startDestination
        when {
            currentApi == null -> LoadingState(Modifier.fillMaxSize(), stringResource(R.string.loading))
            currentStart == null -> LoadingState(Modifier.fillMaxSize(), stringResource(R.string.loading))
            else ->
                NavHost(
                    navController = navController,
                    startDestination = currentStart,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    enterTransition = navEnterTransition(reducedMotion),
                    exitTransition = navExitTransition(reducedMotion),
                    popEnterTransition = navPopEnterTransition(reducedMotion),
                    popExitTransition = navPopExitTransition(reducedMotion),
                ) {
                    composable(MainRoute.Checklists.route) {
                        val instanceId = currentInstance?.id
                        if (instanceId != null) {
                            OfflineChecklistsScreen(
                                api = currentApi,
                                settingsRepository = settingsRepository,
                                instanceId = instanceId,
                                authFingerprint = "${serverUrl.orEmpty()}|${apiKey.orEmpty()}",
                                swipeToDeleteEnabled = swipeToDeleteEnabled,
                                tabReselectToken = checklistsTabReselectToken,
                            )
                        } else {
                            LoadingState(Modifier.fillMaxSize(), stringResource(R.string.loading))
                        }
                    }
                    composable(MainRoute.Notes.route) {
                        val instanceId = currentInstance?.id
                        if (instanceId != null) {
                            OfflineNotesScreen(
                                api = currentApi,
                                settingsRepository = settingsRepository,
                                instanceId = instanceId,
                                authFingerprint = "${serverUrl.orEmpty()}|${apiKey.orEmpty()}",
                                initialNoteId = deepLinkNoteId?.value,
                                onDeepLinkConsumed = { deepLinkNoteId?.value = null },
                                sharedText = sharedNoteText?.value,
                                onSharedTextConsumed = { sharedNoteText?.value = null },
                                swipeToDeleteEnabled = swipeToDeleteEnabled,
                                imageLoader = imageLoader,
                                tabReselectToken = notesTabReselectToken,
                            )
                        } else {
                            LoadingState(Modifier.fillMaxSize(), stringResource(R.string.loading))
                        }
                    }
                    composable(MainRoute.Settings.route) {
                        SettingsScreen(
                            api = currentApi,
                            settingsRepository = settingsRepository,
                            onDisconnect = onDisconnect,
                            onManageInstances = { navController.navigate(ROUTE_MANAGE_INSTANCES) },
                            onAppearance = { navController.navigate(ROUTE_APPEARANCE) },
                            onDashboard = { navController.navigate(ROUTE_DASHBOARD) },
                            onBehavior = { navController.navigate(ROUTE_BEHAVIOR) },
                        )
                    }
                    composable(ROUTE_APPEARANCE) {
                        AppearanceSettingsScreen(settingsRepository = settingsRepository)
                    }
                    composable(ROUTE_BEHAVIOR) {
                        BehaviorSettingsScreen(settingsRepository = settingsRepository)
                    }
                    composable(ROUTE_DASHBOARD) {
                        DashboardOverviewScreen(
                            api = currentApi,
                            settingsRepository = settingsRepository,
                        )
                    }
                    composable(ROUTE_MANAGE_INSTANCES) {
                        SetupScreen(
                            settingsRepository = settingsRepository,
                            onConfigured = { /* no-op; we stay in manage mode */ },
                            standaloneMode = true,
                            showStandaloneHeader = true,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
        }
        }
    }
}

/**
 * Header control showing the current instance's accent dot; tapping opens a menu to switch between
 * configured instances or jump to "Manage instances". Hidden when only one instance is configured.
 */
@Composable
private fun InstanceSwitcher(
    instances: List<JottyInstance>,
    currentInstance: JottyInstance?,
    onSelectInstance: (String) -> Unit,
    onManageInstances: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val switchDescription = stringResource(R.string.switch_instance)
    Box {
        IconButton(onClick = { expanded = true }) {
            val accent = currentInstance?.accentColor()
            if (accent != null) {
                Box(
                    modifier =
                        Modifier
                            .size(16.dp)
                            .background(accent, CircleShape)
                            .semantics { contentDescription = switchDescription },
                )
            } else {
                Icon(Icons.Default.Dns, contentDescription = switchDescription)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            instances.forEach { instance ->
                DropdownMenuItem(
                    text = { Text(instance.name) },
                    onClick = {
                        expanded = false
                        if (instance.id != currentInstance?.id) onSelectInstance(instance.id)
                    },
                    leadingIcon = {
                        val accent = instance.accentColor()
                        if (accent != null) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(14.dp)
                                        .background(accent, CircleShape),
                            )
                        } else {
                            Icon(Icons.Default.Dns, contentDescription = null)
                        }
                    },
                    trailingIcon = {
                        if (instance.id == currentInstance?.id) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.manage_instances)) },
                onClick = {
                    expanded = false
                    onManageInstances()
                },
                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
            )
        }
    }
}
