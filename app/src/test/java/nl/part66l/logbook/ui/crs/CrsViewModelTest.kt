package nl.part66l.logbook.ui.crs

import androidx.lifecycle.SavedStateHandle
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import nl.part66l.logbook.data.CrsEntity
import nl.part66l.logbook.domain.CertificationBasis
import nl.part66l.logbook.domain.SignatureState
import nl.part66l.logbook.fakes.FakeCrsRepository
import nl.part66l.logbook.fakes.FakeDeferredItemRepository
import nl.part66l.logbook.fakes.FakeLocalKeystoreSigner
import nl.part66l.logbook.fakes.GenerateCrsCall
import nl.part66l.logbook.ui.navigation.Destination
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private fun viewModel(
        entryId: String,
        crsRepository: FakeCrsRepository = FakeCrsRepository(),
        deferredItemRepository: FakeDeferredItemRepository = FakeDeferredItemRepository(),
    ) = CrsViewModel(newState(entryId), crsRepository, deferredItemRepository)

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

        val viewModel = viewModel("e1", repository)

        assertEquals(listOf("CRS-1"), viewModel.issued.value.map { it.number })
    }

    @Test
    fun `generate calls the repository with the entered limitations and flag, then resets the form`() {
        val repository = FakeCrsRepository()
        val viewModel = viewModel("e1", repository)
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
        val viewModel = viewModel("e1", repository)
        viewModel.onLimitationsChange("   ")

        viewModel.generate()

        assertEquals(null, repository.generateCalls.first().limitations)
    }

    @Test
    fun `onPhotoAttached persists the path to the repository, picked up reactively by issued`() {
        val existing = crs(entryId = "e1", number = "CRS-1")
        val repository = FakeCrsRepository(initial = listOf(existing))
        val viewModel = viewModel("e1", repository)

        viewModel.onPhotoAttached(existing.id, "/fake/signed-copy.jpg")

        assertEquals("/fake/signed-copy.jpg", viewModel.issued.value.first().signedPhotoLocalPath)
    }

    @Test
    fun `onPhotoRemoved clears a wrongly attached photo`() {
        val existing = crs(entryId = "e1", number = "CRS-1")
        val repository = FakeCrsRepository(initial = listOf(existing))
        val viewModel = viewModel("e1", repository)
        viewModel.onPhotoAttached(existing.id, "/fake/signed-copy.jpg")

        viewModel.onPhotoRemoved(existing.id)

        assertEquals(null, viewModel.issued.value.first().signedPhotoLocalPath)
    }

    @Test
    fun `unchecking maintenance-incomplete clears any queued deferred items`() {
        val viewModel = viewModel("e1")
        viewModel.onMaintenanceIncompleteChange(true)
        viewModel.onDeferredItemAdd("Transponder recal outstanding")
        assertEquals(listOf("Transponder recal outstanding"), viewModel.state.value.deferredItemDescriptions)

        viewModel.onMaintenanceIncompleteChange(false)

        assertEquals(emptyList<String>(), viewModel.state.value.deferredItemDescriptions)
    }

    @Test
    fun `generate raises each queued deferred item against the newly issued certificate`() = runBlocking {
        val crsRepository = FakeCrsRepository()
        val deferredItemRepository = FakeDeferredItemRepository()
        val viewModel = viewModel("e1", crsRepository, deferredItemRepository)
        viewModel.onMaintenanceIncompleteChange(true)
        viewModel.onDeferredItemAdd("Transponder recal outstanding")
        viewModel.onDeferredItemAdd("Wing tip wheel bearing worn")

        viewModel.generate()

        assertEquals(1, crsRepository.generateCalls.size)
        // Passed through to generateUnsigned so the descriptions can be woven into the printed limitations text too.
        assertEquals(
            listOf("Transponder recal outstanding", "Wing tip wheel bearing worn"),
            crsRepository.generateCalls.first().deferredItemDescriptions,
        )
        val raised = deferredItemRepository.open().first()
        assertEquals(2, raised.size)
        assertEquals(setOf("Transponder recal outstanding", "Wing tip wheel bearing worn"), raised.map { it.description }.toSet())
        assertTrue(raised.all { it.raisedByCrsId == viewModel.issued.value.first().id })
    }

    @Test
    fun `signNow signs via the repository and resets the form on success`() {
        val repository = FakeCrsRepository()
        val viewModel = viewModel("e1", repository)
        viewModel.onLimitationsChange("None.")

        viewModel.signNow(FakeLocalKeystoreSigner())

        assertEquals(1, repository.signLocalCalls.size)
        assertEquals("None.", repository.signLocalCalls.first().limitations)
        assertEquals(CrsFormState(), viewModel.state.value)
        assertEquals(1, viewModel.issued.value.size)
        assertEquals(SignatureState.SIGNED_LOCAL, viewModel.issued.value.first().signatureState)
    }

    @Test
    fun `signNow surfaces a repository failure as an error and clears the signing flag`() {
        val repository = FakeCrsRepository()
        repository.signLocalFailure = IllegalStateException("StrongBox unavailable")
        val viewModel = viewModel("e1", repository)

        viewModel.signNow(FakeLocalKeystoreSigner())

        assertEquals(false, viewModel.state.value.signing)
        assertEquals(CrsGenerationError.Other("StrongBox unavailable"), viewModel.state.value.error)
        assertEquals(0, viewModel.issued.value.size)
    }

    @Test
    fun `signNow raises queued deferred items against the newly signed certificate`() = runBlocking {
        val crsRepository = FakeCrsRepository()
        val deferredItemRepository = FakeDeferredItemRepository()
        val viewModel = viewModel("e1", crsRepository, deferredItemRepository)
        viewModel.onMaintenanceIncompleteChange(true)
        viewModel.onDeferredItemAdd("Transponder recal outstanding")

        viewModel.signNow(FakeLocalKeystoreSigner())

        val raised = deferredItemRepository.open().first()
        assertEquals(1, raised.size)
        assertEquals(viewModel.issued.value.first().id, raised.first().raisedByCrsId)
    }

    @Test
    fun `onSignAuthorizationFailed sets the error without touching the repository`() {
        val repository = FakeCrsRepository()
        val viewModel = viewModel("e1", repository)

        viewModel.onSignAuthorizationFailed(RuntimeException("cancelled"))

        assertEquals(false, viewModel.state.value.signing)
        assertEquals(CrsGenerationError.Other("cancelled"), viewModel.state.value.error)
        assertEquals(0, repository.signLocalCalls.size)
    }

    @Test
    fun `dismissError clears a surfaced error`() {
        val viewModel = viewModel("e1")
        viewModel.onSignAuthorizationFailed(RuntimeException("cancelled"))

        viewModel.dismissError()

        assertEquals(null, viewModel.state.value.error)
    }
}
