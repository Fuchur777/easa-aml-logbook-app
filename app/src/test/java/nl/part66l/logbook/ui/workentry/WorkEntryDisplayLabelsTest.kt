package nl.part66l.logbook.ui.workentry

import nl.part66l.logbook.data.PartUsedInput
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkEntryDisplayLabelsTest {

    @Test
    fun `a fully filled part reads as quantity, description and both references`() {
        val part = PartUsedInput(
            partNumber = "6204-2RS",
            description = "Wheel bearing",
            batchOrSerial = "B-77412",
            formOneRef = "55120",
            quantity = "2",
        )

        assertEquals("2x Wheel bearing (B-77412 / 55120)", part.rowLabel)
    }

    @Test
    fun `a single reference doesn't leave a dangling separator`() {
        val serialOnly = PartUsedInput(partNumber = "", description = "Wheel bearing", batchOrSerial = "B-77412", quantity = "1")
        val formOnly = PartUsedInput(partNumber = "", description = "Wheel bearing", formOneRef = "55120", quantity = "1")

        assertEquals("1x Wheel bearing (B-77412)", serialOnly.rowLabel)
        assertEquals("1x Wheel bearing (55120)", formOnly.rowLabel)
    }

    @Test
    fun `no references at all drops the brackets`() {
        val part = PartUsedInput(partNumber = "", description = "Cable ties", quantity = "10")

        assertEquals("10x Cable ties", part.rowLabel)
    }

    @Test
    fun `a part with no description falls back to its part number`() {
        val part = PartUsedInput(partNumber = "6204-2RS", quantity = "2")

        assertEquals("2x 6204-2RS", part.rowLabel)
    }

    @Test
    fun `a non-numeric quantity is printed as written rather than suffixed with x`() {
        val part = PartUsedInput(partNumber = "", description = "Loctite 243", quantity = "1 tube")

        assertEquals("1 tube Loctite 243", part.rowLabel)
    }

    @Test
    fun `a missing quantity just omits the prefix`() {
        val part = PartUsedInput(partNumber = "", description = "Safety wire")

        assertEquals("Safety wire", part.rowLabel)
    }
}
