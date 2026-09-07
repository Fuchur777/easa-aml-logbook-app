package nl.part66l.logbook.ui.navigation

/** Every screen the app can navigate to. A route is just its string key into NavHost's graph. */
sealed interface Destination {
    val route: String

    /** First-run gate (§4: single profile) — no bottom bar, not reachable once a profile exists. */
    data object ProfileSetup : Destination {
        override val route = "profile/setup"
    }

    data object WorkEntries : Destination {
        override val route = "work-entries"
    }

    data object WorkEntryForm : Destination {
        override val route = "work-entries/new"
    }

    data object Aircraft : Destination {
        override val route = "aircraft"
    }

    data object AircraftForm : Destination {
        override val route = "aircraft/new"
    }

    /** Edits an existing aircraft. [aircraftId] builds the concrete route; [ROUTE_PATTERN] registers it with NavHost. */
    data class AircraftEdit(val aircraftId: String) : Destination {
        override val route = "aircraft/edit/$aircraftId"

        companion object {
            const val ARG_AIRCRAFT_ID = "aircraftId"
            const val ROUTE_PATTERN = "aircraft/edit/{$ARG_AIRCRAFT_ID}"
        }
    }

    data object Recency : Destination {
        override val route = "recency"
    }

    /** Reached from the top bar on any bottom-nav screen — edits the existing profile. */
    data object Profile : Destination {
        override val route = "profile"
    }

    /** Reached from the top bar on any bottom-nav screen. */
    data object Settings : Destination {
        override val route = "settings"
    }
}
