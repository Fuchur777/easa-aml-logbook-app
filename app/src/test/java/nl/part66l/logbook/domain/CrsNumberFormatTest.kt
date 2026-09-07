package nl.part66l.logbook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CrsNumberFormatTest {

    private fun standard(annualReset: Boolean, startAt: Int = 1) = CrsNumberFormat(
        template = "{PREFIX}-{YYYY}-{SEQ:4}",
        prefix = "CRS",
        annualReset = annualReset,
        startAt = startAt,
    )

    // -----------------------------------------------------------------
    // format
    // -----------------------------------------------------------------

    @Test
    fun `format renders prefix, year and zero-padded sequence in template order`() {
        assertEquals("CRS-2026-0007", standard(annualReset = true).format(sequence = 7, year = 2026))
    }

    @Test
    fun `format requires a year when the template contains YYYY`() {
        assertThrows(IllegalArgumentException::class.java) {
            standard(annualReset = true).format(sequence = 1, year = null)
        }
    }

    @Test
    fun `format rejects a sequence that overflows the declared width`() {
        assertThrows(IllegalArgumentException::class.java) {
            standard(annualReset = true).format(sequence = 10_000, year = 2026)
        }
    }

    @Test
    fun `a template without YYYY needs no year, at all`() {
        val format = CrsNumberFormat(template = "{PREFIX}-{SEQ:6}", prefix = "LOG", annualReset = false)

        assertEquals("LOG-000042", format.format(sequence = 42))
    }

    // -----------------------------------------------------------------
    // nextNumber
    // -----------------------------------------------------------------

    @Test
    fun `nextNumber starts at startAt when nothing has been issued yet`() {
        assertEquals("CRS-2026-0001", standard(annualReset = true).nextNumber(emptyList(), year = 2026))
    }

    @Test
    fun `nextNumber respects a startAt above 1 when nothing has been issued yet`() {
        val format = CrsNumberFormat(template = "{PREFIX}-{SEQ:3}", prefix = "X", annualReset = false, startAt = 500)

        assertEquals("X-500", format.nextNumber(emptyList()))
    }

    @Test
    fun `nextNumber increments past the highest issued number under this format`() {
        val existing = listOf("CRS-2026-0007", "CRS-2026-0003")

        assertEquals("CRS-2026-0008", standard(annualReset = true).nextNumber(existing, year = 2026))
    }

    @Test
    fun `nextNumber ignores numbers that don't match this format at all`() {
        // An imported historical number (§11: "never enter the live sequence") and
        // a number from an entirely different prefix must not perturb the count.
        val existing = listOf("ILT-LOGBOOK-042", "CRS-2026-0007")

        assertEquals("CRS-2026-0008", standard(annualReset = true).nextNumber(existing, year = 2026))
    }

    @Test
    fun `with annual reset, a new year starts back at startAt regardless of last year's numbers`() {
        val existing = listOf("CRS-2025-0099")

        assertEquals("CRS-2026-0001", standard(annualReset = true, startAt = 1).nextNumber(existing, year = 2026))
    }

    @Test
    fun `without annual reset, the sequence keeps climbing across a year boundary`() {
        val existing = listOf("CRS-2025-0099")

        assertEquals("CRS-2026-0100", standard(annualReset = false).nextNumber(existing, year = 2026))
    }

    @Test
    fun `without annual reset, the highest number across several prior years is found correctly`() {
        val existing = listOf("CRS-2024-0050", "CRS-2025-0099", "CRS-2023-0010")

        assertEquals("CRS-2026-0100", standard(annualReset = false).nextNumber(existing, year = 2026))
    }

    // -----------------------------------------------------------------
    // collision check (§9.2: required on a format change)
    // -----------------------------------------------------------------

    @Test
    fun `collidesWith detects an exact match against already-issued numbers`() {
        val format = standard(annualReset = true)
        val existing = listOf("CRS-2026-0007", "CRS-2026-0008")

        assertTrue(format.collidesWith("CRS-2026-0008", existing))
        assertFalse(format.collidesWith("CRS-2026-0009", existing))
    }

    // -----------------------------------------------------------------
    // template validation
    // -----------------------------------------------------------------

    @Test
    fun `a template with no SEQ placeholder is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            CrsNumberFormat(template = "{PREFIX}-{YYYY}", prefix = "CRS", annualReset = true)
        }
    }

    @Test
    fun `a template with two SEQ placeholders is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            CrsNumberFormat(template = "{SEQ:4}-{SEQ:4}", prefix = "CRS", annualReset = false)
        }
    }

    @Test
    fun `SEQ must be the last placeholder in the template`() {
        assertThrows(IllegalArgumentException::class.java) {
            CrsNumberFormat(template = "{SEQ:4}-{YYYY}", prefix = "CRS", annualReset = true)
        }
    }

    @Test
    fun `literal text is allowed after SEQ, so long as no other placeholder is`() {
        val format = CrsNumberFormat(template = "{PREFIX}-{SEQ:4}-FINAL", prefix = "CRS", annualReset = false)

        assertEquals("CRS-0001-FINAL", format.nextNumber(emptyList()))
    }

    @Test
    fun `startAt below 1 is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            CrsNumberFormat(template = "{PREFIX}-{SEQ:4}", prefix = "CRS", annualReset = false, startAt = 0)
        }
    }
}
