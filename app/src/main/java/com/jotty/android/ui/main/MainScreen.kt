package com.jotty.android.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.first
import com.jotty.android.data.api.ApiClient
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.preferences.SettingsRepository
import com.jotty.android.ui.checklists.ChecklistsScreen
import com.jotty.android.ui.notes.NotesScreen
import com.jotty.android.ui.settings.SettingsScreen

sealed class MainRoute(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Checklists : MainRoute("checklists", "Checklists", Icons.Default.Checklist)
    data object Notes : MainRoute("notes", "Notes", Icons.Default.Note)
    data object Settings : MainRoute("settings", "Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    settingsRepository: SettingsRepository,
    onDisconnect: () -> Unit = {},
    deepLinkNoteId: androidx.compose.runtime.MutableState<String?>? = null,
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
    val serverUrl = settingsRepository.serverUrl.collectAsState(initial = null).value
    val apiKey = settingsRepository.apiKey.collectAsState(initial = null).value

    val api = remember(serverUrl, apiKey) {
        if (!serverUrl.isNullOrBlank() && !apiKey.isNullOrBlank()) {
            ApiClient.create(serverUrl, apiKey)
        } else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jotty") },
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
                        icon = { Icon(route.icon, contentDescription = route.title) },
                        label = { Text(route.title) },
                    )
                }
            }
        },
    ) { padding ->
        when {
            api == null -> Box(Modifier.fillMaxSize()) { Text("Loading…") }
            startDestination == null -> Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            else -> NavHost(
                navController = navController,
                startDestination = startDestination!!,
                modifier = Modifier.padding(padding),
            ) {
                composable(MainRoute.Checklists.route) {
                    ChecklistsScreen(api = api!!)
                }
                composable(MainRoute.Notes.route) {
                    NotesScreen(
                        api = api!!,
                        initialNoteId = deepLinkNoteId?.value,
                        onDeepLinkConsumed = { deepLinkNoteId?.value = null },
                    )
                }
                composable(MainRoute.Settings.route) {
                    SettingsScreen(
                        api = api,
                        settingsRepository = settingsRepository,
                        onDisconnect = onDisconnect,
                    )
                }
            }
        }
    }
}
