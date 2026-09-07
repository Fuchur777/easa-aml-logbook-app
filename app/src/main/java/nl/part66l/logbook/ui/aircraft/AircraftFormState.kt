package nl.part66l.logbook.ui.aircraft

import java.time.LocalDate
import nl.part66l.logbook.domain.Propulsion
import nl.part66l.logbook.domain.Structure
import nl.part66l.logbook.domain.Subcategory

/** Same validation pattern as ProfileFormState (see there for why): raw fields, computed errors, canSave gates Save. */
data class AircraftFormState(
    /** Null while adding a new aircraft; set once an existing one has been loaded for editing. */
    val aircraftId: String? = null,
    val manufacturer: String = "",
    val type: String = "",
    val serialNumber: String = "",
    val propulsion: Propulsion = Propulsion.UNPOWERED,
    val structure: Structure = Structure.WOOD_AND_FABRIC,
    val subcategoryOverride: Subcategory? = null,
    val registration: String = "",
    /** Effective date of a NEW registration — only used if [registration] is actually changed from what's on file. */
    val validFrom: LocalDate = LocalDate.now(),
    val archived: Boolean = false,
    /** Whether any work entry already references this aircraft — gates [canDelete]. */
    val hasWorkHistory: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val deleting: Boolean = false,
) {
    val isEditing: Boolean get() = aircraftId != null

    val manufacturerError: String? get() = if (manufacturer.isBlank()) "Manufacturer is required" else null
    val typeError: String? get() = if (type.isBlank()) "Type is required" else null
    val serialNumberError: String? get() = if (serialNumber.isBlank()) "Serial number is required" else null
    val registrationError: String? get() = if (registration.isBlank()) "Registration is required" else null

    /** §5.2: mixed construction "cannot be derived" — the app must not guess it. */
    val subcategoryOverrideError: String?
        get() = if (structure == Structure.MIXED && subcategoryOverride == null) {
            "Mixed construction needs a subcategory chosen manually"
        } else {
            null
        }

    val canSave: Boolean
        get() = !saving && !loading &&
            manufacturerError == null &&
            typeError == null &&
            serialNumberError == null &&
            registrationError == null &&
            subcategoryOverrideError == null

    /** The database itself also refuses this (FK RESTRICT on work_entry) — this just explains why upfront instead of failing silently. */
    val canDelete: Boolean get() = isEditing && !hasWorkHistory && !saving && !deleting
}
