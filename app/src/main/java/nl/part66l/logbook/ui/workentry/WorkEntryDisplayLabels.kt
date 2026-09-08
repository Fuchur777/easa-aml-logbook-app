package nl.part66l.logbook.ui.workentry

import nl.part66l.logbook.domain.ActivityType
import nl.part66l.logbook.domain.EntryRole

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

val EntryRole.displayLabel: String
    get() = when (this) {
        EntryRole.CERTIFIED_BY_ME_IN_APP -> "Certified by me (in app)"
        EntryRole.CERTIFIED_BY_ME_ON_PAPER -> "Certified by me (on paper)"
        EntryRole.PERFORMED_BY_ME_RELEASED_BY_OTHER -> "Performed by me, released by other"
        EntryRole.PERFORMED_UNDER_SUPERVISION -> "Performed under supervision"
        EntryRole.SUPERVISED_ANOTHER -> "Supervised another"
        EntryRole.ASSISTED_ON_ARC -> "Assisted on ARC"
        EntryRole.NO_RELEASE -> "No release"
    }
