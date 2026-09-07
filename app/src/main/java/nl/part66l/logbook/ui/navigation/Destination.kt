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

    data object Aircraft : Destination {
        override val route = "aircraft"
    }

    data object Recency : Destination {
        override val route = "recency"
    }

    /** Reached from the top bar on any bottom-nav screen — edits the existing profile. */
    data object Profile : Destination {
        override val route = "profile"
    }
}
