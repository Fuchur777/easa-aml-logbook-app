package nl.part66l.logbook.ui.workentry

import nl.part66l.logbook.domain.ActivityType

val ActivityType.displayLabel: String
    get() = when (this) {
        ActivityType.SERVICING -> "Servicing"
        ActivityType.INSPECTION -> "Inspection"
        ActivityType.OPERATIONAL_AND_FUNCTIONAL_TESTING -> "Operational & functional testing"
        ActivityType.TROUBLESHOOTING -> "Troubleshooting"
        ActivityType.REPAIRING -> "Repairing"
        ActivityType.MODIFYING -> "Modifying"
        ActivityType.CHANGING_COMPONENT -> "Changing a component"
        ActivityType.SUPERVISING -> "Supervising"
        ActivityType.RELEASING_TO_SERVICE -> "Releasing to service"
        ActivityType.RESEARCH_AND_PAPERWORK -> "Research & paperwork"
    }
