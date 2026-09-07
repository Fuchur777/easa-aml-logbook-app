package nl.part66l.logbook.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import nl.part66l.logbook.ui.aircraft.AircraftFormScreen
import nl.part66l.logbook.ui.aircraft.AircraftListScreen
import nl.part66l.logbook.ui.navigation.Destination
import nl.part66l.logbook.ui.profile.ProfileFormScreen

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
    val noBottomBarRoutes = setOf(Destination.ProfileSetup.route, Destination.AircraftForm.route)
    val showBottomBar = currentRoute?.hierarchy?.none { it.route in noBottomBarRoutes } ?: false

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
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
            val onProfileClick = { navController.navigate(Destination.Profile.route) }
            composable(Destination.WorkEntries.route) { PlaceholderScreen("Work entries", onProfileClick) }
            composable(Destination.Aircraft.route) {
                AircraftListScreen(
                    onProfileClick = onProfileClick,
                    onAddAircraft = { navController.navigate(Destination.AircraftForm.route) },
                )
            }
            composable(Destination.AircraftForm.route) {
                AircraftFormScreen(onSaved = { navController.popBackStack() })
            }
            composable(Destination.Recency.route) { PlaceholderScreen("Recency", onProfileClick) }
            composable(Destination.Profile.route) {
                ProfileFormScreen(onSaved = { navController.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateSingleTopTo(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
