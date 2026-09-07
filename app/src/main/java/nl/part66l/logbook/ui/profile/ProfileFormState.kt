package nl.part66l.logbook.ui.profile

import java.time.LocalDate
import nl.part66l.logbook.data.ProfileEntity

/**
 * The validation pattern used across every form in this app: raw field values
 * plus computed error properties derived from them, never stored/updated
 * imperatively alongside a value — so an error can never drift out of sync
 * with the field it describes. [canSave] gates the Save button directly;
 * there is no separate "submit and see what's wrong" path.
 */
data class ProfileFormState(
    val name: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val licenceNumber: String = "",
    val issuingAuthority: String = "",
    val licenceValidFrom: LocalDate? = null,
    val licenceExpiry: LocalDate? = null,
    val holdsL1: Boolean = false,
    val holdsL1C: Boolean = false,
    val holdsL2: Boolean = false,
    val holdsL2C: Boolean = false,
    val recencyReductionGranted: Boolean = false,
    val recencyReductionReference: String = "",
    val recencyReductionDate: LocalDate? = null,
    val saving: Boolean = false,
) {
    val nameError: String? get() = if (name.isBlank()) "Name is required" else null

    val subcategoryError: String?
        get() = if (!(holdsL1 || holdsL1C || holdsL2 || holdsL2C)) "Select at least one subcategory" else null

    val licenceDatesError: String?
        get() = if (licenceValidFrom != null && licenceExpiry != null && licenceValidFrom.isAfter(licenceExpiry)) {
            "Valid from must not be after valid till"
        } else {
            null
        }

    val canSave: Boolean get() = !saving && nameError == null && subcategoryError == null && licenceDatesError == null
}

fun ProfileEntity.toFormState() = ProfileFormState(
    name = name,
    phoneNumber = phoneNumber.orEmpty(),
    email = email.orEmpty(),
    licenceNumber = licenceNumber.orEmpty(),
    issuingAuthority = issuingAuthority.orEmpty(),
    licenceValidFrom = licenceValidFrom,
    licenceExpiry = licenceExpiry,
    holdsL1 = holdsL1,
    holdsL1C = holdsL1C,
    holdsL2 = holdsL2,
    holdsL2C = holdsL2C,
    recencyReductionGranted = recencyReductionGranted,
    recencyReductionReference = recencyReductionReference.orEmpty(),
    recencyReductionDate = recencyReductionDate,
)

/**
 * The reduction is granted by the same competent authority that issued the licence —
 * asking for it a second time was pure duplicate data entry, so [ProfileEntity.recencyReductionAuthority]
 * always mirrors [issuingAuthority] rather than being its own form field.
 */
fun ProfileFormState.toEntity() = ProfileEntity(
    name = name.trim(),
    phoneNumber = phoneNumber.trim().ifBlank { null },
    email = email.trim().ifBlank { null },
    licenceNumber = licenceNumber.trim().ifBlank { null },
    issuingAuthority = issuingAuthority.trim().ifBlank { null },
    licenceValidFrom = licenceValidFrom,
    licenceExpiry = licenceExpiry,
    holdsL1 = holdsL1,
    holdsL1C = holdsL1C,
    holdsL2 = holdsL2,
    holdsL2C = holdsL2C,
    recencyReductionGranted = recencyReductionGranted,
    recencyReductionAuthority = issuingAuthority.trim().ifBlank { null },
    recencyReductionReference = recencyReductionReference.trim().ifBlank { null },
    recencyReductionDate = recencyReductionDate,
)
