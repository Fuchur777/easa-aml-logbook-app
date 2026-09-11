package nl.part66l.logbook.ui.workentry

import java.time.LocalDate
import nl.part66l.logbook.data.DocumentationRefInput
import nl.part66l.logbook.data.PartUsedInput
import nl.part66l.logbook.data.PhotoInput
import nl.part66l.logbook.domain.ActivityType

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
    /** Short line for headers, list rows and the CRS's own header line. */
    val description: String = "",
    /** Optional multiline "explanation of work done" — printed under [description] on the CRS. */
    val explanation: String = "",
    val activityTypes: Set<ActivityType> = emptySet(),
    /** One job can span several days — always at least one date, never empty. */
    val sessionDates: List<LocalDate> = listOf(LocalDate.now()),
    /** Overrides the distinct-date count below when set — raw text so an empty field isn't 0. */
    val daysWorkedOverride: String = "",
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
    /** Already fully processed (§8) by the time they land here — see [nl.part66l.logbook.ui.workentry.processAndStorePhoto]. */
    val photos: List<PhotoInput> = emptyList(),

    /** Optional — closes notes in the engineer's own record (§5.7), never a statement about the aircraft. Always starts empty, even when editing. */
    val closesDeferredItemIds: Set<String> = emptySet(),

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
    val daysWorkedOverrideError: String?
        get() = if (daysWorkedOverride.isNotBlank() && daysWorkedOverride.toIntOrNull() == null) "Enter a whole number" else null

    val canSave: Boolean
        get() = !saving && !loading && descriptionError == null && activityTypesError == null && aircraftSelectionError == null &&
            airframeHoursError == null && launchesError == null && daysWorkedOverrideError == null && sessionDates.isNotEmpty()

    /** No `hasWorkHistory`-style guard here — a signed CRS blocks the delete via FK RESTRICT instead, and there's no UI path to a signed CRS yet. */
    val canDelete: Boolean get() = isEditing && !saving && !deleting
}
