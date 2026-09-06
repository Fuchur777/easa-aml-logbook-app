package nl.part66l.logbook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Never-silently-switches semantics: each rejection carries a reason, and the
 * `when` branches bind in a fixed priority order (tested at the bottom).
 */
class BasisAvailabilityTest {

    private val checker = BasisAvailability()

    /** Every condition passing, so both bases come out available. */
    private val validInput = BasisAvailability.Input(
        holdsAnyLicence = true,
        licenceValid = true,
        recencyCurrentForSubcategory = true,
        ownershipRelation = OwnershipRelation.OWNER,
        isAnnualInspection = false,
        concurrentWithArc = false,
        withinPilotOwnerTaskList = true,
        hasHelpers = false,
    )

    private fun BasisAvailability.evaluateOne(input: BasisAvailability.Input, basis: CertificationBasis) =
        evaluate(input).first { it.basis == basis }

    // -----------------------------------------------------------------
    // ML_A_801_B2_INDEPENDENT
    // -----------------------------------------------------------------

    @Test
    fun `AML available when licence, validity and recency all hold`() {
        val option = checker.evaluateOne(validInput, CertificationBasis.ML_A_801_B2_INDEPENDENT)
        assertTrue(option.available)
        assertNull(option.reason)
    }

    @Test
    fun `AML unavailable with no licence details recorded`() {
        val option = checker.evaluateOne(
            validInput.copy(holdsAnyLicence = false),
            CertificationBasis.ML_A_801_B2_INDEPENDENT,
        )
        assertFalse(option.available)
        assertEquals("No licence details recorded.", option.reason)
    }

    @Test
    fun `AML unavailable when the licence has expired`() {
        val option = checker.evaluateOne(
            validInput.copy(licenceValid = false),
            CertificationBasis.ML_A_801_B2_INDEPENDENT,
        )
        assertFalse(option.available)
        assertEquals("Licence has expired.", option.reason)
    }

    @Test
    fun `AML unavailable when recency is not currently met`() {
        val option = checker.evaluateOne(
            validInput.copy(recencyCurrentForSubcategory = false),
            CertificationBasis.ML_A_801_B2_INDEPENDENT,
        )
        assertFalse(option.available)
        assertEquals(
            "Recency not currently met for this aircraft's subcategory under 66.A.20(b)(2).",
            option.reason,
        )
    }

    // -----------------------------------------------------------------
    // ML_A_803_PILOT_OWNER
    // -----------------------------------------------------------------

    @Test
    fun `pilot-owner available when ownership, ARC timing, helpers and task list all clear`() {
        val option = checker.evaluateOne(validInput, CertificationBasis.ML_A_803_PILOT_OWNER)
        assertTrue(option.available)
        assertNull(option.reason)
    }

    @Test
    fun `pilot-owner unavailable without ownership of the aircraft`() {
        val option = checker.evaluateOne(
            validInput.copy(ownershipRelation = OwnershipRelation.NONE),
            CertificationBasis.ML_A_803_PILOT_OWNER,
        )
        assertFalse(option.available)
        assertEquals(
            "Pilot-owner maintenance requires ownership of the aircraft (ML.A.803(a)(2)).",
            option.reason,
        )
    }

    @Test
    fun `pilot-owner unavailable for an annual inspection concurrent with the ARC`() {
        val option = checker.evaluateOne(
            validInput.copy(isAnnualInspection = true, concurrentWithArc = true),
            CertificationBasis.ML_A_803_PILOT_OWNER,
        )
        assertFalse(option.available)
        assertEquals(
            "An annual inspection released on the same day as the ARC cannot be signed as pilot-owner.",
            option.reason,
        )
    }

    @Test
    fun `an annual inspection alone, without a concurrent ARC, does not block pilot-owner`() {
        val option = checker.evaluateOne(
            validInput.copy(isAnnualInspection = true, concurrentWithArc = false),
            CertificationBasis.ML_A_803_PILOT_OWNER,
        )
        assertTrue(option.available)
    }

    @Test
    fun `pilot-owner unavailable with named helpers`() {
        val option = checker.evaluateOne(
            validInput.copy(hasHelpers = true),
            CertificationBasis.ML_A_803_PILOT_OWNER,
        )
        assertFalse(option.available)
        assertEquals(
            "AMC1 ML.A.803 limits the pilot-owner CRS to maintenance personally performed; " +
                "remove the named helpers or certify under the licence.",
            option.reason,
        )
    }

    @Test
    fun `pilot-owner unavailable when work falls outside the pilot-owner task list`() {
        val option = checker.evaluateOne(
            validInput.copy(withinPilotOwnerTaskList = false),
            CertificationBasis.ML_A_803_PILOT_OWNER,
        )
        assertFalse(option.available)
        assertEquals(
            "Work falls outside limited pilot-owner maintenance (Appendix II to Part-ML).",
            option.reason,
        )
    }

    @Test
    fun `an unknown task list, absent the Part-ML catalogue, does not block pilot-owner`() {
        // null means "we don't know yet", not "no" — only an explicit false blocks.
        val option = checker.evaluateOne(
            validInput.copy(withinPilotOwnerTaskList = null),
            CertificationBasis.ML_A_803_PILOT_OWNER,
        )
        assertTrue(option.available)
    }

    // -----------------------------------------------------------------
    // Priority order between simultaneous pilot-owner disqualifiers
    // -----------------------------------------------------------------

    @Test
    fun `ownership is checked before helpers when both conditions fail`() {
        val option = checker.evaluateOne(
            validInput.copy(ownershipRelation = OwnershipRelation.NONE, hasHelpers = true),
            CertificationBasis.ML_A_803_PILOT_OWNER,
        )
        assertEquals(
            "Pilot-owner maintenance requires ownership of the aircraft (ML.A.803(a)(2)).",
            option.reason,
        )
    }

    @Test
    fun `helpers are checked before the task list when both conditions fail`() {
        val option = checker.evaluateOne(
            validInput.copy(hasHelpers = true, withinPilotOwnerTaskList = false),
            CertificationBasis.ML_A_803_PILOT_OWNER,
        )
        assertEquals(
            "AMC1 ML.A.803 limits the pilot-owner CRS to maintenance personally performed; " +
                "remove the named helpers or certify under the licence.",
            option.reason,
        )
    }
}
