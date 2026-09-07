package nl.part66l.logbook.ui.aircraft

import java.time.LocalDate
import nl.part66l.logbook.domain.Propulsion
import nl.part66l.logbook.domain.Structure
import nl.part66l.logbook.domain.Subcategory

/** Same validation pattern as ProfileFormState (see there for why): raw fields, computed errors, canSave gates Save. */
data class AircraftFormState(
    val manufacturer: String = "",
    val type: String = "",
    val serialNumber: String = "",
    val propulsion: Propulsion = Propulsion.UNPOWERED,
    val structure: Structure = Structure.WOOD_AND_FABRIC,
    val subcategoryOverride: Subcategory? = null,
    val registration: String = "",
    val validFrom: LocalDate = LocalDate.now(),
    val saving: Boolean = false,
) {
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
        get() = !saving &&
            manufacturerError == null &&
            typeError == null &&
            serialNumberError == null &&
            registrationError == null &&
            subcategoryOverrideError == null
}
