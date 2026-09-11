package nl.part66l.logbook.ui.recency

import nl.part66l.logbook.domain.RecencyRoute

val RecencyRoute.displayLabel: String
    get() = when (this) {
        RecencyRoute.DAYS -> "Days"
        RecencyRoute.TASKS -> "Tasks"
        RecencyRoute.ANNUAL_INSPECTIONS -> "Annual inspections"
        RecencyRoute.INITIAL_CERTIFICATION_GRACE_PERIOD -> "Initial certification grace period"
    }
