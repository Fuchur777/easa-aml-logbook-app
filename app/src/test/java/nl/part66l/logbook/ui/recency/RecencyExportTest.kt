package nl.part66l.logbook.ui.recency

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import nl.part66l.logbook.data.RecencyEvidenceRow
import nl.part66l.logbook.domain.RecencyRoute
import nl.part66l.logbook.domain.Subcategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecencyExportTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `writes a header row plus one row per evidence record`() = runBlocking {
        val rows = listOf(
            RecencyEvidenceRow(
                subcategory = Subcategory.L1, route = RecencyRoute.DAYS, date = LocalDate.of(2026, 3, 14),
                aircraftRegistration = "PH-1234", detail = "Annual inspection",
            ),
            RecencyEvidenceRow(
                subcategory = Subcategory.L1, route = RecencyRoute.TASKS, date = LocalDate.of(2026, 3, 10),
                aircraftRegistration = null, detail = "Weighing, weight & balance sheet",
            ),
        )

        val csv = exportRecencyCsv(context, rows)
        val lines = csv.readLines()

        assertEquals("Subcategory,Route,Date,Aircraft,Detail", lines[0])
        assertEquals("L1,Days,2026-03-14,PH-1234,Annual inspection", lines[1])
        assertEquals("L1,Tasks,2026-03-10,Bench / component work,\"Weighing, weight & balance sheet\"", lines[2])
        assertEquals(3, lines.size)
    }

    @Test
    fun `quotes a detail field that contains a comma, and escapes an embedded quote`() = runBlocking {
        val rows = listOf(
            RecencyEvidenceRow(
                subcategory = Subcategory.L2, route = RecencyRoute.DAYS, date = LocalDate.of(2026, 1, 1),
                aircraftRegistration = null, detail = "Replaced the \"main\" bearing, greased and re-torqued",
            ),
        )

        val csv = exportRecencyCsv(context, rows)
        val line = csv.readLines()[1]

        assertTrue(line.contains("\"Replaced the \"\"main\"\" bearing, greased and re-torqued\""))
    }

    @Test
    fun `an empty evidence list still produces a header-only file`() = runBlocking {
        val csv = exportRecencyCsv(context, emptyList())

        assertEquals(listOf("Subcategory,Route,Date,Aircraft,Detail"), csv.readLines())
    }

    @Test
    fun `re-exporting clears out the previous export rather than accumulating files`() = runBlocking {
        val first = exportRecencyCsv(context, emptyList())
        assertTrue(first.exists())

        exportRecencyCsv(context, emptyList())

        assertTrue("the previous export's own file should have been cleared away", !first.exists())
    }
}
