package nl.part66l.logbook.domain

/**
 * The prescribed statement text (§9.1), keyed by certification basis — versioned,
 * non-translatable catalogue data per the spec's invariant, not a string baked
 * into the PDF renderer. [statement] is the clause alone; the issuer's name and
 * licence number are inlined by the caller, matching [CrsRenderData.statement]'s
 * contract ("issuer's name NOT yet inlined").
 *
 * Only [CertificationBasis.ML_A_801_B2_INDEPENDENT] has a real path to a CRS today —
 * pilot-owner mode (ML.A.803) is deferred per spec §16, with no supporting profile/entry
 * flow built yet. The pilot-owner text is included for completeness (it's already
 * published catalogue data) but nothing in the UI can reach it yet.
 */
object CertificationStatements {

    const val VERSION = "2026.1"

    fun statement(basis: CertificationBasis): String = when (basis) {
        CertificationBasis.ML_A_801_B2_INDEPENDENT ->
            "certifies that the work specified, except as otherwise specified, was carried out " +
                "in accordance with Part-ML, and in respect to that work, the aircraft is considered " +
                "ready for release to service."
        CertificationBasis.ML_A_803_PILOT_OWNER ->
            "certifies that the limited pilot-owner maintenance specified, except as otherwise " +
                "specified, was carried out in accordance with Part-ML, and in respect to that work, " +
                "the aircraft is considered ready for release to service."
    }

    fun basisLabel(basis: CertificationBasis): String = when (basis) {
        CertificationBasis.ML_A_801_B2_INDEPENDENT -> "Independent certifying staff — ML.A.801(b)(2)"
        CertificationBasis.ML_A_803_PILOT_OWNER -> "Pilot-owner — ML.A.803"
    }

    fun regulationFooter(basis: CertificationBasis): String = when (basis) {
        CertificationBasis.ML_A_801_B2_INDEPENDENT ->
            "Issued under ML.A.801(b)(2) of Regulation (EU) No 1321/2014, Annex Vb (Part-ML)."
        CertificationBasis.ML_A_803_PILOT_OWNER ->
            "Issued under ML.A.803 of Regulation (EU) No 1321/2014, Annex Vb (Part-ML)."
    }
}
