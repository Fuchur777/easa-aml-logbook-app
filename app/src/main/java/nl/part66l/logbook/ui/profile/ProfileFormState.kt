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
    val licenceNumber: String = "",
    val issuingAuthority: String = "",
    val licenceExpiry: LocalDate? = null,
    val holdsL1: Boolean = false,
    val holdsL1C: Boolean = false,
    val holdsL2: Boolean = false,
    val holdsL2C: Boolean = false,
    val recencyReductionGranted: Boolean = false,
    val recencyReductionAuthority: String = "",
    val recencyReductionReference: String = "",
    val recencyReductionDate: LocalDate? = null,
    val saving: Boolean = false,
) {
    val nameError: String? get() = if (name.isBlank()) "Name is required" else null

    val subcategoryError: String?
        get() = if (!(holdsL1 || holdsL1C || holdsL2 || holdsL2C)) "Select at least one subcategory" else null

    val canSave: Boolean get() = !saving && nameError == null && subcategoryError == null
}

fun ProfileEntity.toFormState() = ProfileFormState(
    name = name,
    licenceNumber = licenceNumber.orEmpty(),
    issuingAuthority = issuingAuthority.orEmpty(),
    licenceExpiry = licenceExpiry,
    holdsL1 = holdsL1,
    holdsL1C = holdsL1C,
    holdsL2 = holdsL2,
    holdsL2C = holdsL2C,
    recencyReductionGranted = recencyReductionGranted,
    recencyReductionAuthority = recencyReductionAuthority.orEmpty(),
    recencyReductionReference = recencyReductionReference.orEmpty(),
    recencyReductionDate = recencyReductionDate,
)

fun ProfileFormState.toEntity() = ProfileEntity(
    name = name.trim(),
    licenceNumber = licenceNumber.trim().ifBlank { null },
    issuingAuthority = issuingAuthority.trim().ifBlank { null },
    licenceExpiry = licenceExpiry,
    holdsL1 = holdsL1,
    holdsL1C = holdsL1C,
    holdsL2 = holdsL2,
    holdsL2C = holdsL2C,
    recencyReductionGranted = recencyReductionGranted,
    recencyReductionAuthority = recencyReductionAuthority.trim().ifBlank { null },
    recencyReductionReference = recencyReductionReference.trim().ifBlank { null },
    recencyReductionDate = recencyReductionDate,
)
