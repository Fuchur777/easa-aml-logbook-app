package nl.part66l.logbook.ui.crs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.LocalDate
import java.util.Collections
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import nl.part66l.logbook.data.CrsEntity
import nl.part66l.logbook.data.Identifiers
import nl.part66l.logbook.data.IssuedCrsRow
import nl.part66l.logbook.domain.CertificationBasis
import nl.part66l.logbook.domain.SignatureState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrsExportTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun testFile(name: String, content: String): String {
        val file = File(context.cacheDir, name)
        file.writeText(content)
        return file.absolutePath
    }

    private fun row(number: String, pdfPath: String?, signedPhotoPath: String? = null) = IssuedCrsRow(
        crs = CrsEntity(
            id = number,
            entryId = "entry-$number",
            number = number,
            numberNormalised = Identifiers.normalise(number),
            sequence = 1,
            year = 2026,
            basis = CertificationBasis.ML_A_801_B2_INDEPENDENT,
            statementVersion = "v1",
            completionDate = LocalDate.of(2026, 3, 14),
            signatureState = SignatureState.SIGNED_LOCAL,
            snapshotJson = "{}",
            pdfLocalPath = pdfPath,
            signedPhotoLocalPath = signedPhotoPath,
        ),
        aircraftId = null,
        aircraftRegistration = null,
        helperNamesCsv = null,
    )

    private fun entryNames(zip: File): List<String> =
        ZipFile(zip).use { Collections.list(it.entries()).map { entry -> entry.name } }

    @Test
    fun `zips each row's PDF, named after its certificate number`() = runBlocking {
        val rows = listOf(
            row("CRS-2026-0001", pdfPath = testFile("c1.pdf", "pdf one")),
            row("CRS-2026-0002", pdfPath = testFile("c2.pdf", "pdf two")),
        )

        val zip = exportCrsZip(context, rows)

        assertEquals(setOf("CRS-2026-0001.pdf", "CRS-2026-0002.pdf"), entryNames(zip).toSet())
        ZipFile(zip).use { zf ->
            assertEquals("pdf one", zf.getInputStream(zf.getEntry("CRS-2026-0001.pdf")).bufferedReader().readText())
        }
    }

    @Test
    fun `also includes a signed-copy photo, when one was attached`() = runBlocking {
        val rows = listOf(
            row(
                "CRS-2026-0001",
                pdfPath = testFile("c1.pdf", "pdf"),
                signedPhotoPath = testFile("c1.jpg", "photo bytes"),
            ),
        )

        val zip = exportCrsZip(context, rows)

        assertEquals(setOf("CRS-2026-0001.pdf", "CRS-2026-0001-signed-copy.jpg"), entryNames(zip).toSet())
    }

    @Test
    fun `skips a row whose file no longer exists on disk, rather than failing the whole export`() = runBlocking {
        val rows = listOf(
            row("CRS-2026-0001", pdfPath = "/no/such/file.pdf"),
            row("CRS-2026-0002", pdfPath = testFile("c2.pdf", "pdf two")),
        )

        val zip = exportCrsZip(context, rows)

        assertEquals(listOf("CRS-2026-0002.pdf"), entryNames(zip))
    }

    @Test
    fun `de-duplicates entry names when two rows would otherwise collide`() = runBlocking {
        // Same certificate number on two rows shouldn't happen in practice (latestIssued is one
        // row per base number), but the zip must still come out valid rather than silently
        // dropping the second file under an overwritten entry name.
        val rows = listOf(
            row("CRS-2026-0001", pdfPath = testFile("a.pdf", "first")),
            row("CRS-2026-0001", pdfPath = testFile("b.pdf", "second")),
        )

        val zip = exportCrsZip(context, rows)

        assertEquals(setOf("CRS-2026-0001.pdf", "CRS-2026-0001-1.pdf"), entryNames(zip).toSet())
    }

    @Test
    fun `re-exporting clears out the previous export rather than accumulating files`() = runBlocking {
        val first = exportCrsZip(context, listOf(row("CRS-2026-0001", pdfPath = testFile("c1.pdf", "pdf"))))
        assertTrue(first.exists())

        exportCrsZip(context, listOf(row("CRS-2026-0002", pdfPath = testFile("c2.pdf", "pdf"))))

        assertTrue("the previous export's own file should have been cleared away", !first.exists())
    }
}
