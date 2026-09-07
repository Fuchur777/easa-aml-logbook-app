package nl.part66l.logbook.domain

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenceValidityTest {

    private val today = LocalDate.of(2026, 6, 15)

    @Test
    fun `valid when both bounds unset`() {
        assertTrue(LicenceValidity.isValid(today, validFrom = null, expiry = null))
    }

    @Test
    fun `valid when today is within both bounds`() {
        assertTrue(
            LicenceValidity.isValid(
                today,
                validFrom = LocalDate.of(2026, 1, 1),
                expiry = LocalDate.of(2026, 12, 31),
            ),
        )
    }

    @Test
    fun `invalid before validFrom`() {
        assertFalse(LicenceValidity.isValid(today, validFrom = LocalDate.of(2026, 7, 1), expiry = null))
    }

    @Test
    fun `invalid after expiry`() {
        assertFalse(LicenceValidity.isValid(today, validFrom = null, expiry = LocalDate.of(2026, 6, 1)))
    }

    @Test
    fun `valid on the boundary dates themselves`() {
        assertTrue(LicenceValidity.isValid(today, validFrom = today, expiry = today))
    }
}
