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

    /** Edits an existing work entry. [entryId] builds the concrete route; [ROUTE_PATTERN] registers it with NavHost. */
    data class WorkEntryEdit(val entryId: String) : Destination {
        override val route = "work-entries/edit/$entryId"

        companion object {
            const val ARG_ENTRY_ID = "entryId"
            const val ROUTE_PATTERN = "work-entries/edit/{$ARG_ENTRY_ID}"
        }
    }

    /** Certificates issued against a work entry (§9), plus generating a new one. [entryId] builds the concrete route; [ROUTE_PATTERN] registers it with NavHost. */
    data class Crs(val entryId: String) : Destination {
        override val route = "work-entries/$entryId/crs"

        companion object {
            const val ARG_ENTRY_ID = "entryId"
            const val ROUTE_PATTERN = "work-entries/{$ARG_ENTRY_ID}/crs"
        }
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

    /** Reached from the top bar on any bottom-nav screen — manages the document directory (§5.3). */
    data object Documents : Destination {
        override val route = "documents"
    }

    data object DocumentForm : Destination {
        override val route = "documents/new"
    }

    /** Edits an existing document. [documentId] builds the concrete route; [ROUTE_PATTERN] registers it with NavHost. */
    data class DocumentEdit(val documentId: String) : Destination {
        override val route = "documents/edit/$documentId"

        companion object {
            const val ARG_DOCUMENT_ID = "documentId"
            const val ROUTE_PATTERN = "documents/edit/{$ARG_DOCUMENT_ID}"
        }
    }

    /** Reached from the top bar on any bottom-nav screen — edits the existing profile. */
    data object Profile : Destination {
        override val route = "profile"
    }

    /** Reached from the top bar on any bottom-nav screen. */
    data object Settings : Destination {
        override val route = "settings"
    }

    /** Reached from Settings — manages the contact directory (helpers, workorder issuers). */
    data object Contacts : Destination {
        override val route = "contacts"
    }

    data object ContactForm : Destination {
        override val route = "contacts/new"
    }

    /** Edits an existing contact. [contactId] builds the concrete route; [ROUTE_PATTERN] registers it with NavHost. */
    data class ContactEdit(val contactId: String) : Destination {
        override val route = "contacts/edit/$contactId"

        companion object {
            const val ARG_CONTACT_ID = "contactId"
            const val ROUTE_PATTERN = "contacts/edit/{$ARG_CONTACT_ID}"
        }
    }
}
