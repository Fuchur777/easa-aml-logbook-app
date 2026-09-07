package nl.part66l.logbook.ui.workentry

import java.time.LocalDate
import nl.part66l.logbook.domain.ActivityType
import nl.part66l.logbook.domain.EntryRole

/**
 * A distinct "nothing chosen yet" state, separate from [Bench] — bench/component work
 * is a real, deliberate choice (not every entry has an aircraft), not a default you
 * fall into by not picking anything.
 */
sealed interface AircraftSelection {
    data object Unselected : AircraftSelection
    data object Bench : AircraftSelection
    data class Specific(val aircraftId: String) : AircraftSelection
}

/** Same validation pattern as the other forms: raw fields, computed errors, canSave gates Save. */
data class WorkEntryFormState(
    val aircraftSelection: AircraftSelection = AircraftSelection.Unselected,
    val description: String = "",
    val activityTypes: Set<ActivityType> = emptySet(),
    /** Deliberately the most conservative default — never presumes a certification happened. */
    val role: EntryRole = EntryRole.NO_RELEASE,
    /** Independent of [role] — set via its own checkbox, not the role dropdown. */
    val supervisedAnother: Boolean = false,
    val sessionDate: LocalDate = LocalDate.now(),
    val helperNames: List<String> = emptyList(),
    /** Personal time-tracking only, kept out of the regulatory [ActivityType] set — see there for why. */
    val researchAndPaperwork: Boolean = false,
    val saving: Boolean = false,
) {
    val descriptionError: String? get() = if (description.isBlank()) "Description of work done is required" else null
    val activityTypesError: String? get() = if (activityTypes.isEmpty()) "Select at least one activity" else null
    val aircraftSelectionError: String?
        get() = if (aircraftSelection is AircraftSelection.Unselected) "Select an aircraft or bench / component work" else null

    val canSave: Boolean
        get() = !saving && descriptionError == null && activityTypesError == null && aircraftSelectionError == null
}
