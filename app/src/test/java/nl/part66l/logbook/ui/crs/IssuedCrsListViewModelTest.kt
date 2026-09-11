package nl.part66l.logbook.ui.crs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import nl.part66l.logbook.data.CrsEntity
import nl.part66l.logbook.data.Identifiers
import nl.part66l.logbook.data.IssuedCrsRow
import nl.part66l.logbook.domain.CertificationBasis
import nl.part66l.logbook.domain.Propulsion
import nl.part66l.logbook.domain.SignatureState
import nl.part66l.logbook.domain.Structure
import nl.part66l.logbook.fakes.FakeAircraftRepository
import nl.part66l.logbook.fakes.FakeCrsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric, not a plain JVM test, only because [IssuedCrsListViewModel.exportSelected]
 * needs a real `context.filesDir` to write into — everything else here could run without it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IssuedCrsListViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        crsRepository: FakeCrsRepository = FakeCrsRepository(),
        aircraftRepository: FakeAircraftRepository = FakeAircraftRepository(),
    ) = IssuedCrsListViewModel(context, crsRepository, aircraftRepository)

    private fun row(
        number: String,
        completionDate: LocalDate,
        aircraftId: String? = null,
        aircraftRegistration: String? = null,
        helperNames: List<String> = emptyList(),
    ) = IssuedCrsRow(
        crs = CrsEntity(
            id = number,
            entryId = "entry-$number",
            number = number,
            numberNormalised = Identifiers.normalise(number),
            sequence = 1,
            year = completionDate.year,
            basis = CertificationBasis.ML_A_801_B2_INDEPENDENT,
            statementVersion = "v1",
            completionDate = completionDate,
            signatureState = SignatureState.SIGNED_LOCAL,
            snapshotJson = "{}",
            pdfLocalPath = "/fake/$number.pdf",
        ),
        aircraftId = aircraftId,
        aircraftRegistration = aircraftRegistration,
        helperNamesCsv = helperNames.joinToString(","),
    )

    @Test
    fun `rows reflects the repository's latest-issued flow for the current filter`() {
        val crsRepository = FakeCrsRepository()
        crsRepository.seedIssued(
            listOf(
                row("CRS-0001", LocalDate.of(2026, 1, 1), aircraftId = "ac1", helperNames = listOf("Jan de Vries")),
                row("CRS-0002", LocalDate.of(2026, 6, 1), aircraftId = "ac2"),
            ),
        )
        val viewModel = viewModel(crsRepository)

        assertEquals(listOf("CRS-0002", "CRS-0001"), viewModel.rows.value.map { it.crs.number }) // newest first, the fake's default
    }

    @Test
    fun `changing the aircraft filter narrows rows`() {
        val crsRepository = FakeCrsRepository()
        crsRepository.seedIssued(
            listOf(
                row("CRS-0001", LocalDate.of(2026, 1, 1), aircraftId = "ac1"),
                row("CRS-0002", LocalDate.of(2026, 6, 1), aircraftId = "ac2"),
            ),
        )
        val viewModel = viewModel(crsRepository)

        viewModel.onAircraftFilterChange("ac1")

        assertEquals(listOf("CRS-0001"), viewModel.rows.value.map { it.crs.number })
    }

    @Test
    fun `changing the CRS name query narrows rows to a case-insensitive substring match`() {
        val crsRepository = FakeCrsRepository()
        crsRepository.seedIssued(
            listOf(
                row("CRS-2026-0001", LocalDate.of(2026, 1, 1)),
                row("CRS-2026-0002", LocalDate.of(2026, 6, 1)),
            ),
        )
        val viewModel = viewModel(crsRepository)

        viewModel.onNumberQueryChange("0002")

        assertEquals(listOf("CRS-2026-0002"), viewModel.rows.value.map { it.crs.number })
    }

    @Test
    fun `changing the helper query narrows rows to a case-insensitive substring match`() {
        val crsRepository = FakeCrsRepository()
        crsRepository.seedIssued(
            listOf(
                row("CRS-0001", LocalDate.of(2026, 1, 1), helperNames = listOf("Jan de Vries")),
                row("CRS-0002", LocalDate.of(2026, 6, 1), helperNames = listOf("Piet Bakker")),
            ),
        )
        val viewModel = viewModel(crsRepository)

        viewModel.onHelperQueryChange("DE VRIES")

        assertEquals(listOf("CRS-0001"), viewModel.rows.value.map { it.crs.number })
    }

    @Test
    fun `toggling sort flips oldest-first versus newest-first`() {
        val crsRepository = FakeCrsRepository()
        crsRepository.seedIssued(
            listOf(
                row("CRS-0001", LocalDate.of(2026, 1, 1)),
                row("CRS-0002", LocalDate.of(2026, 6, 1)),
            ),
        )
        val viewModel = viewModel(crsRepository)
        assertEquals(listOf("CRS-0002", "CRS-0001"), viewModel.rows.value.map { it.crs.number })

        viewModel.onSortToggle()

        assertEquals(listOf("CRS-0001", "CRS-0002"), viewModel.rows.value.map { it.crs.number })
    }

    @Test
    fun `aircraftOptions reflects the aircraft repository`() = runBlocking {
        val aircraftRepository = FakeAircraftRepository()
        aircraftRepository.create(
            manufacturer = "Schleicher", type = "ASK 21", serialNumber = "21123",
            propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
            subcategoryOverride = null, registration = "PH-1234", validFrom = LocalDate.of(2020, 1, 1),
        )
        val viewModel = viewModel(aircraftRepository = aircraftRepository)

        assertEquals(listOf("PH-1234"), viewModel.aircraftOptions.value.map { it.registration })
    }

    @Test
    fun `long-pressing a row selects it, and tapping another row while selected toggles it too`() {
        val crsRepository = FakeCrsRepository()
        crsRepository.seedIssued(
            listOf(row("CRS-0001", LocalDate.of(2026, 1, 1)), row("CRS-0002", LocalDate.of(2026, 6, 1))),
        )
        val viewModel = viewModel(crsRepository)

        viewModel.onRowLongPress("CRS-0001")
        assertEquals(setOf("CRS-0001"), viewModel.selectedIds.value)

        viewModel.onRowToggleSelected("CRS-0002")
        assertEquals(setOf("CRS-0001", "CRS-0002"), viewModel.selectedIds.value)

        viewModel.onRowToggleSelected("CRS-0001") // toggling again deselects it
        assertEquals(setOf("CRS-0002"), viewModel.selectedIds.value)
    }

    @Test
    fun `select all selects every row the current filter shows, not the whole unfiltered set`() {
        val crsRepository = FakeCrsRepository()
        crsRepository.seedIssued(
            listOf(
                row("CRS-0001", LocalDate.of(2026, 1, 1), aircraftId = "ac1"),
                row("CRS-0002", LocalDate.of(2026, 6, 1), aircraftId = "ac2"),
            ),
        )
        val viewModel = viewModel(crsRepository)
        viewModel.onAircraftFilterChange("ac1")

        viewModel.onSelectAll()

        assertEquals(setOf("CRS-0001"), viewModel.selectedIds.value)
    }

    @Test
    fun `clear selection empties it`() {
        val crsRepository = FakeCrsRepository()
        crsRepository.seedIssued(listOf(row("CRS-0001", LocalDate.of(2026, 1, 1))))
        val viewModel = viewModel(crsRepository)
        viewModel.onRowLongPress("CRS-0001")

        viewModel.onClearSelection()

        assertTrue(viewModel.selectedIds.value.isEmpty())
    }

    @Test
    fun `changing the aircraft, name or helper filter clears the selection, but re-sorting does not`() {
        val crsRepository = FakeCrsRepository()
        crsRepository.seedIssued(
            listOf(row("CRS-0001", LocalDate.of(2026, 1, 1), aircraftId = "ac1")),
        )
        val viewModel = viewModel(crsRepository)

        viewModel.onRowLongPress("CRS-0001")
        viewModel.onSortToggle()
        assertEquals(setOf("CRS-0001"), viewModel.selectedIds.value) // re-sorting the same set kept it

        viewModel.onAircraftFilterChange("ac1")
        assertTrue(viewModel.selectedIds.value.isEmpty())

        viewModel.onRowLongPress("CRS-0001")
        viewModel.onNumberQueryChange("CRS")
        assertTrue(viewModel.selectedIds.value.isEmpty())

        viewModel.onRowLongPress("CRS-0001")
        viewModel.onHelperQueryChange("Jan")
        assertTrue(viewModel.selectedIds.value.isEmpty())
    }

    // exportSelected's actual zip-building is covered end-to-end, deterministically, by
    // CrsExportTest — it runs on Dispatchers.IO internally, which Dispatchers.setMain doesn't
    // swap, so asserting on it right after a fire-and-forget viewModelScope.launch here would
    // race the real background dispatch instead of reliably waiting for it.

    @Test
    fun `exportSelected does nothing with an empty selection`() {
        val crsRepository = FakeCrsRepository()
        crsRepository.seedIssued(listOf(row("CRS-0001", LocalDate.of(2026, 1, 1))))
        val viewModel = viewModel(crsRepository)

        var called = false
        viewModel.exportSelected { called = true }

        assertFalse(called)
    }
}
