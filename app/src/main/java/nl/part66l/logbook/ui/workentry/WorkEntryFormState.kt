package nl.part66l.logbook.ui.workentry

import java.time.LocalDate
import nl.part66l.logbook.data.DocumentationRefInput
import nl.part66l.logbook.data.PartUsedInput
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
    /** Null while adding a new entry; set once an existing one has been loaded for editing. */
    val entryId: String? = null,
    val aircraftSelection: AircraftSelection = AircraftSelection.Unselected,
    val description: String = "",
    val activityTypes: Set<ActivityType> = emptySet(),
    /** Deliberately the most conservative default — never presumes a certification happened. */
    val role: EntryRole = EntryRole.NO_RELEASE,
    /** Independent of [role] — set via its own checkbox, not the role dropdown. */
    val supervisedAnother: Boolean = false,
    val sessionDate: LocalDate = LocalDate.now(),
    val helperNames: List<String> = emptyList(),
    /** Appendix II catalogue task ids evidenced by this entry — feeds Route B. Entirely optional. */
    val completedTaskIds: Set<String> = emptySet(),

    // Workorder value object (§5.4) — all optional free text; the scanned-attachment
    // part needs the document-scanner flow and isn't captured here yet.
    val workorderIssuerName: String = "",
    val workorderDate: LocalDate? = null,
    val workorderRequestedWork: String = "",
    val workorderReference: String = "",

    // Readings at the time of work, not counters — raw text so an empty field isn't 0.
    val airframeHours: String = "",
    val launches: String = "",

    /** Entry metadata, never printed on the CRS — see WorkEntryEntity.annualInspection. */
    val annualInspection: Boolean = false,
    /** Only meaningful while [annualInspection] is set — cleared when it's unchecked. */
    val concurrentWithArc: Boolean = false,

    val documentationRefs: List<DocumentationRefInput> = emptyList(),
    val partsUsed: List<PartUsedInput> = emptyList(),

    val loading: Boolean = false,
    val saving: Boolean = false,
    val deleting: Boolean = false,
) {
    val isEditing: Boolean get() = entryId != null

    val descriptionError: String? get() = if (description.isBlank()) "Description of work done is required" else null
    val activityTypesError: String? get() = if (activityTypes.isEmpty()) "Select at least one activity" else null
    val aircraftSelectionError: String?
        get() = if (aircraftSelection is AircraftSelection.Unselected) "Select an aircraft or bench / component work" else null
    val airframeHoursError: String? get() = if (airframeHours.isNotBlank() && airframeHours.toDoubleOrNull() == null) "Enter a number" else null
    val launchesError: String? get() = if (launches.isNotBlank() && launches.toIntOrNull() == null) "Enter a whole number" else null

    val canSave: Boolean
        get() = !saving && !loading && descriptionError == null && activityTypesError == null && aircraftSelectionError == null &&
            airframeHoursError == null && launchesError == null

    /** No `hasWorkHistory`-style guard here — a signed CRS blocks the delete via FK RESTRICT instead, and there's no UI path to a signed CRS yet. */
    val canDelete: Boolean get() = isEditing && !saving && !deleting
}
