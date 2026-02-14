package com.jotty.android.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.first
import com.jotty.android.R
import com.jotty.android.data.api.ApiClient
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.preferences.SettingsRepository
import com.jotty.android.ui.checklists.ChecklistsScreen
import com.jotty.android.ui.notes.OfflineNotesScreen
import com.jotty.android.ui.setup.SetupScreen
import com.jotty.android.ui.settings.SettingsScreen
import com.jotty.android.util.createNoteImageLoader
import coil.ImageLoader

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
    var startDestination by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        startDestination = settingsRepository.startTab.first() ?: MainRoute.Checklists.route
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
    val currentInstance = settingsRepository.currentInstance.collectAsState(initial = null).value
    val serverUrl = settingsRepository.serverUrl.collectAsState(initial = null).value
    val apiKey = settingsRepository.apiKey.collectAsState(initial = null).value
    val swipeToDeleteEnabled = settingsRepository.swipeToDeleteEnabled.collectAsState(initial = false).value

    val imageLoader = remember(context, serverUrl, apiKey) {
        createNoteImageLoader(context, serverUrl, apiKey)
    }

    val api = remember(serverUrl, apiKey) {
        if (!serverUrl.isNullOrBlank() && !apiKey.isNullOrBlank()) {
            ApiClient.create(serverUrl, apiKey)
        } else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                listOf(MainRoute.Checklists, MainRoute.Notes, MainRoute.Settings).forEach { route ->
                    NavigationBarItem(
                        selected = currentRoute == route.route,
                        onClick = {
                            if (currentRoute != route.route) {
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
            currentApi == null -> Box(Modifier.fillMaxSize()) {
                Text(stringResource(R.string.loading))
            }
            currentStart == null -> Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            else -> NavHost(
                navController = navController,
                startDestination = currentStart,
                modifier = Modifier.padding(padding),
            ) {
                composable(MainRoute.Checklists.route) {
                    ChecklistsScreen(
                        api = currentApi,
                        settingsRepository = settingsRepository,
                        swipeToDeleteEnabled = swipeToDeleteEnabled,
                    )
                }
                composable(MainRoute.Notes.route) {
                    val instanceId = currentInstance?.id
                    if (instanceId != null) {
                        OfflineNotesScreen(
                            api = currentApi,
                            settingsRepository = settingsRepository,
                            instanceId = instanceId,
                            initialNoteId = deepLinkNoteId?.value,
                            onDeepLinkConsumed = { deepLinkNoteId?.value = null },
                            swipeToDeleteEnabled = swipeToDeleteEnabled,
                            imageLoader = imageLoader,
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(R.string.loading))
                        }
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
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
