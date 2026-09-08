package nl.part66l.logbook.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Not a substitute for actually looking at a rendered PDF — layout correctness is
 * a visual matter. This is a regression net: does it render at all, does the
 * two-pass "Page n of m" total match reality, does pagination actually carry every
 * row across a page break rather than silently dropping any, and is the
 * kept-together certification block still exactly one block once pagination
 * happens around it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrsPdfRendererTest {

    private fun sampleData(
        documentation: List<DocRow> = defaultDocumentation,
        parts: List<PartRow> = defaultParts,
        workOrders: List<WorkOrderRow> = defaultWorkOrders,
        activities: List<String> = defaultActivities,
        completedTasks: List<String> = defaultCompletedTasks,
    ) = CrsRenderData(
        number = "NL66-2026-0007",
        basisLabel = "Independent certifying staff — ML.A.801(b)(2)",
        aircraft = listOf(
            "Registration" to "PH-1234",
            "Manufacturer and type" to "Schleicher ASK 21",
            "Serial number" to "21123",
            "Hours / launches" to "3412 h / 8907",
        ),
        description = "Annual inspection in accordance with the approved maintenance programme.",
        period = listOf(
            "Start" to "11 March 2026",
            "End" to "14 March 2026",
            "Days worked" to "3",
        ),
        documentation = documentation,
        parts = parts,
        workOrders = workOrders,
        limitations = "None.",
        statement = "certifies that the work specified, except as otherwise specified, was carried " +
            "out in accordance with Part-ML, and in respect to that work, the aircraft is considered " +
            "ready for release to service.",
        issuer = "F. Example",
        licenceNumber = "NL.66.00000",
        issuedDate = "14 March 2026",
        regulationFooter = "Issued under ML.A.801(b)(2) of Regulation (EU) No 1321/2014, Annex Vb (Part-ML).",
        personnel = listOf(
            PersonnelRow("F. Example", "NL.66.00000", "Certifying staff"),
            PersonnelRow("J. de Vries", "NL.66.11111", "Assisted / On the job training"),
        ),
        activities = activities,
        completedTasks = completedTasks,
        photos = listOf(PhotoRow("a3f1c0e2-…-9b41.jpg", "9f2a41c7de08b533", "14 March 2026 10:12")),
    )

    private fun textOf(document: PDDocument): String = PDFTextStripper().getText(document)

    @Test
    fun `renders a single page for the ordinary case, with a correct page total`() {
        PDDocument().use { document ->
            val pages = CrsPdfRenderer().render(document, sampleData(workOrders = emptyList()))

            assertEquals(1, pages)
            assertEquals(document.numberOfPages, pages)
            val text = textOf(document)
            assertTrue(text.contains("NL66-2026-0007"))
            assertTrue(text.contains("CERTIFICATION"))
            assertTrue(text.contains("None.")) // limitations, always printed
            assertTrue(text.contains("Page 1 of 1"))
        }
    }

    @Test
    fun `an empty personnel or photos section is omitted rather than printed empty`() {
        PDDocument().use { document ->
            CrsPdfRenderer().render(document, sampleData().copy(personnel = emptyList(), photos = emptyList()))

            val text = textOf(document)
            assertTrue(!text.contains("PERSONNEL WHO CARRIED OUT THE WORK"))
            assertTrue(!text.contains("PHOTOGRAPHIC RECORD"))
        }
    }

    @Test
    fun `personnel lists the certifying staff alongside anyone who assisted`() {
        PDDocument().use { document ->
            CrsPdfRenderer().render(document, sampleData())

            val text = textOf(document)
            assertTrue(text.contains("PERSONNEL WHO CARRIED OUT THE WORK"))
            assertTrue(text.contains("F. Example"))
            assertTrue(text.contains("Certifying staff"))
            assertTrue(text.contains("J. de Vries"))
            assertTrue(text.contains("Assisted / On the job training"))
        }
    }

    @Test
    fun `activities and completed tasks are listed, and omitted entirely when both are empty`() {
        PDDocument().use { document ->
            CrsPdfRenderer().render(document, sampleData())

            val text = textOf(document)
            assertTrue(text.contains("ACTIVITIES AND TASKS"))
            assertTrue(text.contains("Inspection"))
            assertTrue(text.contains("Weighing, weight & balance sheet"))
        }
        PDDocument().use { document ->
            CrsPdfRenderer().render(document, sampleData(activities = emptyList(), completedTasks = emptyList()))

            assertTrue(!textOf(document).contains("ACTIVITIES AND TASKS"))
        }
    }

    @Test
    fun `an empty documentation or parts section prints None rather than an empty table`() {
        PDDocument().use { document ->
            val data = sampleData(documentation = emptyList(), parts = emptyList()).copy(limitations = "No limitations apply.")
            CrsPdfRenderer().render(document, data)

            val text = textOf(document)
            assertTrue(text.contains("MAINTENANCE DATA USED"))
            assertTrue(text.contains("PARTS AND MATERIALS INSTALLED"))
            assertEquals(2, Regex("None\\.").findAll(text).count()) // one for each empty section
        }
    }

    @Test
    fun `a work order, when present, is rendered on its own page titled Work Order`() {
        PDDocument().use { document ->
            CrsPdfRenderer().render(document, sampleData())

            val text = textOf(document)
            assertTrue(text.contains("WORK ORDER"))
            assertTrue(text.contains("WO-2026-014"))
        }
    }

    @Test
    fun `an empty work order list omits the Work Order section entirely`() {
        PDDocument().use { document ->
            CrsPdfRenderer().render(document, sampleData(workOrders = emptyList()))

            assertTrue(!textOf(document).contains("WORK ORDER"))
        }
    }

    @Test
    fun `a long documentation table paginates, repeats its header, and drops nothing`() {
        val longDocs = (1..80).map { DocRow("Doc reference $it", "Manual", "Rev. $it", "1 January 2026") }

        PDDocument().use { document ->
            val pages = CrsPdfRenderer().render(document, sampleData(documentation = longDocs))

            assertTrue("expected pagination past a single page, got $pages", pages > 1)
            assertEquals(document.numberOfPages, pages)

            val text = textOf(document)
            // Every row survived the page break, not just the ones that fit on page 1 —
            // checked by count rather than an exact string match, since PDFTextStripper's
            // exact whitespace between independently-positioned cells isn't guaranteed.
            assertEquals(80, Regex("Doc reference").findAll(text).count())
            assertTrue(text.contains("Doc reference 80"))
            // The continuation header appears, and the certification block was not
            // duplicated by being kept together across the break.
            assertTrue(text.contains("— continued"))
            assertEquals(1, Regex("CERTIFICATION").findAll(text).count())
            // The printed total matches the actual page count from both passes.
            assertTrue(text.contains("Page 1 of $pages"))
        }
    }
}

private val defaultDocumentation = listOf(
    DocRow("ASK 21 Maintenance Manual, chapter 4", "Manual", "Rev. 7", "12 June 2024"),
    DocRow("TN 826-11", "SD", "Issue 2", "3 February 2021"),
)

private val defaultParts = listOf(PartRow("6204-2RS", "Wheel bearing", "B-77412", "EASA Form 1 ref. 55120"))

private val defaultWorkOrders = listOf(
    WorkOrderRow("Club maintenance officer", "10 March 2026", "Annual inspection", "WO-2026-014"),
)

private val defaultActivities = listOf("Inspection", "Repairing")

private val defaultCompletedTasks = listOf("Weighing, weight & balance sheet")
