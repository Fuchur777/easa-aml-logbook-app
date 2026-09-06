package nl.part66l.logbook.domain

/**
 * Decides which certification bases are available for a given entry, and why.
 *
 * The app defaults to the user's preferred basis but **never silently switches**.
 * If the preferred basis is unavailable it says so with a reason — the situation
 * where auto-selection would be most convenient is exactly the situation where it
 * would produce an invalid release.
 */
class BasisAvailability {

    data class Option(
        val basis: CertificationBasis,
        val available: Boolean,
        val reason: String? = null,
    )

    data class Input(
        val holdsAnyLicence: Boolean,
        val licenceValid: Boolean,
        val recencyCurrentForSubcategory: Boolean,
        val ownershipRelation: OwnershipRelation,
        val isAnnualInspection: Boolean,
        val concurrentWithArc: Boolean,
        val withinPilotOwnerTaskList: Boolean?,   // null while the Part-ML catalogue is absent
        val hasHelpers: Boolean,
    )

    fun evaluate(input: Input): List<Option> = listOf(
        aml(input),
        pilotOwner(input),
    )

    private fun aml(i: Input) = when {
        !i.holdsAnyLicence ->
            Option(CertificationBasis.ML_A_801_B2_INDEPENDENT, false, "No licence details recorded.")
        !i.licenceValid ->
            Option(CertificationBasis.ML_A_801_B2_INDEPENDENT, false, "Licence has expired.")
        !i.recencyCurrentForSubcategory ->
            Option(
                CertificationBasis.ML_A_801_B2_INDEPENDENT, false,
                "Recency not currently met for this aircraft's subcategory under 66.A.20(b)(2).",
            )
        else -> Option(CertificationBasis.ML_A_801_B2_INDEPENDENT, true)
    }

    private fun pilotOwner(i: Input) = when {
        i.ownershipRelation == OwnershipRelation.NONE ->
            Option(
                CertificationBasis.ML_A_803_PILOT_OWNER, false,
                "Pilot-owner maintenance requires ownership of the aircraft (ML.A.803(a)(2)).",
            )
        /**
         * A yearly signed off on the same day as the airworthiness review cannot be
         * released under ML.A.803.
         */
        i.isAnnualInspection && i.concurrentWithArc ->
            Option(
                CertificationBasis.ML_A_803_PILOT_OWNER, false,
                "An annual inspection released on the same day as the ARC cannot be signed as pilot-owner.",
            )
        i.hasHelpers ->
            Option(
                CertificationBasis.ML_A_803_PILOT_OWNER, false,
                "AMC1 ML.A.803 limits the pilot-owner CRS to maintenance personally performed; " +
                    "remove the named helpers or certify under the licence.",
            )
        i.withinPilotOwnerTaskList == false ->
            Option(
                CertificationBasis.ML_A_803_PILOT_OWNER, false,
                "Work falls outside limited pilot-owner maintenance (Appendix II to Part-ML).",
            )
        else -> Option(CertificationBasis.ML_A_803_PILOT_OWNER, true)
    }
}
