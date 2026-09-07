package nl.part66l.logbook.ui.recency

import nl.part66l.logbook.domain.RecencyRoute

val RecencyRoute.displayLabel: String
    get() = when (this) {
        RecencyRoute.DAYS -> "Days (Route A)"
        RecencyRoute.TASKS -> "Tasks (Route B)"
        RecencyRoute.ANNUAL_INSPECTIONS -> "Annual inspections (Route C)"
        RecencyRoute.INITIAL_CERTIFICATION_GRACE_PERIOD -> "Initial certification grace period"
    }
