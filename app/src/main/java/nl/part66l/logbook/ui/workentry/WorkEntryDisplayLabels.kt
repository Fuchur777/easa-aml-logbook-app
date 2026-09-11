package nl.part66l.logbook.ui.workentry

import nl.part66l.logbook.data.PartUsedInput
import nl.part66l.logbook.domain.ActivityType

/**
 * One added part as a single line: `2x Wheel bearing (B-77412 / 55120)` — quantity, what it is,
 * then the two references that identify the individual item fitted.
 *
 * Everything is optional except a label: the description is used where there is one, falling back
 * to the part number, since materials (sealant, cable ties) often have a description and no part
 * number while a stocked component is the other way round. A purely numeric quantity gets the
 * "2x" form; anything else ("1 tube") is printed as written.
 */
val PartUsedInput.rowLabel: String
    get() {
        val label = description?.takeIf { it.isNotBlank() } ?: partNumber.takeIf { it.isNotBlank() } ?: "Part"
        val prefix = quantity?.trim()?.takeIf { it.isNotBlank() }?.let { if (it.all(Char::isDigit)) "${it}x " else "$it " }.orEmpty()
        val references = listOfNotNull(
            batchOrSerial?.takeIf { it.isNotBlank() },
            formOneRef?.takeIf { it.isNotBlank() },
        ).joinToString(" / ")
        return prefix + label + if (references.isNotEmpty()) " ($references)" else ""
    }

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
