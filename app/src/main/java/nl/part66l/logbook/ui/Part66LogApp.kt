package nl.part66l.logbook.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import nl.part66l.logbook.ui.aircraft.AircraftFormScreen
import nl.part66l.logbook.ui.aircraft.AircraftListScreen
import nl.part66l.logbook.ui.navigation.Destination
import nl.part66l.logbook.ui.profile.ProfileFormScreen
import nl.part66l.logbook.ui.settings.SettingsScreen
import nl.part66l.logbook.ui.workentry.WorkEntryFormScreen
import nl.part66l.logbook.ui.workentry.WorkEntryListScreen

@Composable
fun Part66LogApp() {
    val appViewModel: AppViewModel = hiltViewModel()
    val state by appViewModel.state.collectAsStateWithLifecycle()

    when (state) {
        AppStartState.Loading -> LoadingScreen()
        AppStartState.NeedsProfile -> AppNavHost(startDestination = Destination.ProfileSetup.route, onProfileSaved = appViewModel::markProfileReady)
        AppStartState.Ready -> AppNavHost(startDestination = Destination.WorkEntries.route, onProfileSaved = appViewModel::markProfileReady)
    }
}

private val bottomBarDestinations = listOf(
    Destination.WorkEntries to "Work",
    Destination.Aircraft to "Aircraft",
    Destination.Recency to "Recency",
)

@Composable
private fun AppNavHost(startDestination: String, onProfileSaved: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination
    val noBottomBarRoutes = setOf(
        Destination.ProfileSetup.route,
        Destination.AircraftForm.route,
        Destination.AircraftEdit.ROUTE_PATTERN,
        Destination.Profile.route,
        Destination.Settings.route,
        Destination.WorkEntryForm.route,
    )
    val showBottomBar = currentRoute?.hierarchy?.none { it.route in noBottomBarRoutes } ?: false

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                var menuExpanded by remember { mutableStateOf(false) }
                NavigationBar {
                    bottomBarDestinations.forEach { (destination, label) ->
                        val selected = currentRoute?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateSingleTopTo(destination.route) },
                            icon = { Text(label.take(1)) },
                            label = { Text(label) },
                        )
                    }
                    NavigationBarItem(
                        selected = false,
                        onClick = { menuExpanded = true },
                        icon = {
                            Text("☰")
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("Profile") },
                                    onClick = { menuExpanded = false; navController.navigate(Destination.Profile.route) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    onClick = { menuExpanded = false; navController.navigate(Destination.Settings.route) },
                                )
                            }
                        },
                        label = { Text("Menu") },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.ProfileSetup.route) {
                ProfileFormScreen(onSaved = {
                    onProfileSaved()
                    navController.navigate(Destination.WorkEntries.route) {
                        popUpTo(Destination.ProfileSetup.route) { inclusive = true }
                    }
                })
            }
            composable(Destination.WorkEntries.route) {
                WorkEntryListScreen(onAddEntry = { navController.navigate(Destination.WorkEntryForm.route) })
            }
            composable(Destination.WorkEntryForm.route) {
                WorkEntryFormScreen(
                    onSaved = { navController.popBackStack() },
                    onClose = { navController.popBackStack() },
                )
            }
            composable(Destination.Aircraft.route) {
                AircraftListScreen(
                    onAddAircraft = { navController.navigate(Destination.AircraftForm.route) },
                    onEditAircraft = { id -> navController.navigate(Destination.AircraftEdit(id).route) },
                )
            }
            composable(Destination.AircraftForm.route) {
                AircraftFormScreen(
                    onSaved = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() },
                    onClose = { navController.popBackStack() },
                )
            }
            composable(
                route = Destination.AircraftEdit.ROUTE_PATTERN,
                arguments = listOf(navArgument(Destination.AircraftEdit.ARG_AIRCRAFT_ID) { type = NavType.StringType }),
            ) {
                AircraftFormScreen(
                    onSaved = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() },
                    onClose = { navController.popBackStack() },
                )
            }
            composable(Destination.Recency.route) { PlaceholderScreen("Recency") }
            composable(Destination.Profile.route) {
                ProfileFormScreen(onSaved = { navController.popBackStack() }, onClose = { navController.popBackStack() })
            }
            composable(Destination.Settings.route) { SettingsScreen(onClose = { navController.popBackStack() }) }
        }
    }
}

private fun NavHostController.navigateSingleTopTo(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
