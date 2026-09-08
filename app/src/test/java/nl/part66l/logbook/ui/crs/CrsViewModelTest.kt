package nl.part66l.logbook.ui.crs

import androidx.lifecycle.SavedStateHandle
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import nl.part66l.logbook.data.CrsEntity
import nl.part66l.logbook.domain.CertificationBasis
import nl.part66l.logbook.domain.SignatureState
import nl.part66l.logbook.fakes.FakeCrsRepository
import nl.part66l.logbook.fakes.GenerateCrsCall
import nl.part66l.logbook.ui.navigation.Destination
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CrsViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newState(entryId: String) = SavedStateHandle(mapOf(Destination.Crs.ARG_ENTRY_ID to entryId))

    private fun crs(entryId: String, number: String) = CrsEntity(
        id = UUID.randomUUID().toString(), entryId = entryId, number = number, numberNormalised = number,
        sequence = 1, year = 2026, basis = CertificationBasis.ML_A_801_B2_INDEPENDENT, statementVersion = "test",
        completionDate = LocalDate.of(2026, 1, 15), signatureState = SignatureState.ISSUED_UNSIGNED_PRINT, snapshotJson = "{}",
    )

    @Test
    fun `issued reflects the repository, scoped to this entry only`() {
        val repository = FakeCrsRepository(
            initial = listOf(crs(entryId = "e1", number = "CRS-1"), crs(entryId = "e2", number = "CRS-2")),
        )

        val viewModel = CrsViewModel(newState("e1"), repository)

        assertEquals(listOf("CRS-1"), viewModel.issued.value.map { it.number })
    }

    @Test
    fun `generate calls the repository with the entered limitations and flag, then resets the form`() {
        val repository = FakeCrsRepository()
        val viewModel = CrsViewModel(newState("e1"), repository)
        viewModel.onLimitationsChange("Engine run-up not performed")
        viewModel.onMaintenanceIncompleteChange(true)

        viewModel.generate()

        assertEquals(listOf(GenerateCrsCall("e1", "Engine run-up not performed", true)), repository.generateCalls)
        assertEquals(CrsFormState(), viewModel.state.value)
        assertEquals(1, viewModel.issued.value.size)
    }

    @Test
    fun `blank limitations are passed through as null`() {
        val repository = FakeCrsRepository()
        val viewModel = CrsViewModel(newState("e1"), repository)
        viewModel.onLimitationsChange("   ")

        viewModel.generate()

        assertEquals(null, repository.generateCalls.first().limitations)
    }
}
