package nl.part66l.logbook.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpiryWarningTest {

    private val today = LocalDate.of(2026, 3, 1)
    private val thresholds = WarningThresholds()

    @Test
    fun `a licence with no expiry date warns about nothing`() {
        assertEquals(WarningLevel.NONE, ExpiryWarning.forLicence(null, today, thresholds))
    }

    @Test
    fun `licence expiry crosses amber then red as it approaches, and stays red once past`() {
        assertEquals(WarningLevel.NONE, ExpiryWarning.forLicence(today.plusDays(91), today, thresholds))
        assertEquals(WarningLevel.AMBER, ExpiryWarning.forLicence(today.plusDays(90), today, thresholds))
        assertEquals(WarningLevel.AMBER, ExpiryWarning.forLicence(today.plusDays(31), today, thresholds))
        assertEquals(WarningLevel.RED, ExpiryWarning.forLicence(today.plusDays(30), today, thresholds))
        assertEquals(WarningLevel.RED, ExpiryWarning.forLicence(today.minusDays(1), today, thresholds))
    }

    @Test
    fun `recency already lapsed is red whatever the dates say`() {
        assertEquals(WarningLevel.RED, ExpiryWarning.forRecency(current = false, lapseDate = null, today = today, thresholds = thresholds))
        assertEquals(WarningLevel.RED, ExpiryWarning.forRecency(current = false, lapseDate = today.plusYears(1), today = today, thresholds = thresholds))
    }

    @Test
    fun `a current subcategory with no lapse date warns about nothing`() {
        assertEquals(WarningLevel.NONE, ExpiryWarning.forRecency(current = true, lapseDate = null, today = today, thresholds = thresholds))
    }

    @Test
    fun `recency lapse uses its own shorter lead time`() {
        assertEquals(WarningLevel.NONE, ExpiryWarning.forRecency(true, today.plusDays(61), today, thresholds))
        assertEquals(WarningLevel.AMBER, ExpiryWarning.forRecency(true, today.plusDays(60), today, thresholds))
        assertEquals(WarningLevel.RED, ExpiryWarning.forRecency(true, today.plusDays(30), today, thresholds))
    }

    @Test
    fun `custom thresholds are honoured`() {
        val wide = WarningThresholds(licenceAmberDays = 365, licenceRedDays = 180)
        assertEquals(WarningLevel.AMBER, ExpiryWarning.forLicence(today.plusDays(200), today, wide))
        assertEquals(WarningLevel.RED, ExpiryWarning.forLicence(today.plusDays(179), today, wide))
    }

    @Test
    fun `highest reports the most severe level present`() {
        assertEquals(WarningLevel.NONE, ExpiryWarning.highest(emptyList()))
        assertEquals(WarningLevel.NONE, ExpiryWarning.highest(listOf(WarningLevel.NONE, WarningLevel.NONE)))
        assertEquals(WarningLevel.AMBER, ExpiryWarning.highest(listOf(WarningLevel.NONE, WarningLevel.AMBER)))
        assertEquals(WarningLevel.RED, ExpiryWarning.highest(listOf(WarningLevel.AMBER, WarningLevel.RED, WarningLevel.NONE)))
    }
}
