package com.jotty.android.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jotty.android.R
import com.jotty.android.data.api.ApiClient
import com.jotty.android.data.preferences.SettingsRepository
import com.jotty.android.ui.checklists.OfflineChecklistsScreen
import com.jotty.android.ui.common.LoadingState
import com.jotty.android.ui.notes.OfflineNotesScreen
import com.jotty.android.ui.settings.SettingsScreen
import com.jotty.android.ui.setup.SetupScreen
import com.jotty.android.util.createNoteImageLoader
import kotlinx.coroutines.flow.first

private const val ROUTE_MANAGE_INSTANCES = "manage_instances"

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
) {
    val navController = rememberNavController()
    var startDestination by rememberSaveable { mutableStateOf<String?>(null) }
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
    val context = LocalContext.current
    val currentInstance by settingsRepository.currentInstance.collectAsStateWithLifecycle(initialValue = null)
    val serverUrl by settingsRepository.serverUrl.collectAsStateWithLifecycle(initialValue = null)
    val apiKey by settingsRepository.apiKey.collectAsStateWithLifecycle(initialValue = null)
    val swipeToDeleteEnabled by settingsRepository.swipeToDeleteEnabled.collectAsStateWithLifecycle(initialValue = false)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val selectedRoute =
        when (currentRoute) {
            ROUTE_MANAGE_INSTANCES -> MainRoute.Settings.route
            else -> currentRoute
        }
    val titleRes =
        when (currentRoute) {
            MainRoute.Checklists.route -> MainRoute.Checklists.titleRes
            MainRoute.Notes.route -> MainRoute.Notes.titleRes
            MainRoute.Settings.route -> MainRoute.Settings.titleRes
            ROUTE_MANAGE_INSTANCES -> R.string.manage_instances
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                navigationIcon = {
                    if (currentRoute == ROUTE_MANAGE_INSTANCES) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            )
        },
        bottomBar = {
            NavigationBar {
                listOf(MainRoute.Checklists, MainRoute.Notes, MainRoute.Settings).forEach { route ->
                    NavigationBarItem(
                        selected = selectedRoute == route.route,
                        onClick = {
                            if (selectedRoute != route.route) {
                                navController.navigate(route.route) {
                                    popUpTo(MainRoute.Checklists.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(route.icon, contentDescription = stringResource(route.titleRes)) },
                        label = { Text(stringResource(route.titleRes)) },
                    )
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
                                swipeToDeleteEnabled = swipeToDeleteEnabled,
                                imageLoader = imageLoader,
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
                        )
                    }
                    composable(ROUTE_MANAGE_INSTANCES) {
                        SetupScreen(
                            settingsRepository = settingsRepository,
                            onConfigured = { /* no-op; we stay in manage mode */ },
                            standaloneMode = true,
                            showStandaloneHeader = false,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
        }
    }
}
