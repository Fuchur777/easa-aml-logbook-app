package nl.part66l.logbook.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import nl.part66l.logbook.data.ProfileEntity
import nl.part66l.logbook.fakes.FakeProfileRepository
import nl.part66l.logbook.fakes.FakeSettingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * UnconfinedTestDispatcher on Main so viewModelScope.launch runs eagerly —
 * the fakes never genuinely suspend, so every launched coroutine completes
 * synchronously within the call that triggered it.
 */
class SettingsViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun profile(
        issuingAuthority: String? = "ILT",
        recencyReductionGranted: Boolean = false,
        recencyReductionReference: String? = null,
        researchCountsTowardRecency: Boolean = false,
    ) = ProfileEntity(
        name = "Test Pilot", licenceNumber = "L-123", issuingAuthority = issuingAuthority, licenceExpiry = null,
        holdsL1 = true, recencyReductionGranted = recencyReductionGranted,
        recencyReductionReference = recencyReductionReference,
        researchCountsTowardRecency = researchCountsTowardRecency,
    )

    @Test
    fun `showArchivedAircraft reflects and updates the settings repository`() {
        val settingsRepository = FakeSettingsRepository(initialShowArchivedAircraft = false)
        val viewModel = SettingsViewModel(settingsRepository, FakeProfileRepository())

        assertFalse(viewModel.showArchivedAircraft.value)

        viewModel.onShowArchivedAircraftChange(true)

        assertTrue(viewModel.showArchivedAircraft.value)
    }

    @Test
    fun `recency reduction and research-counts-toward-recency load from the existing profile`() {
        val profileRepository = FakeProfileRepository(
            profile(recencyReductionGranted = true, recencyReductionReference = "REF-123", researchCountsTowardRecency = true),
        )
        val viewModel = SettingsViewModel(FakeSettingsRepository(), profileRepository)

        assertTrue(viewModel.recencyReductionGranted.value)
        assertEquals("REF-123", viewModel.recencyReductionReference.value)
        assertTrue(viewModel.researchCountsTowardRecency.value)
    }

    @Test
    fun `toggling recency reduction persists it and mirrors the licence issuing authority`() {
        val profileRepository = FakeProfileRepository(profile(issuingAuthority = "ILT"))
        val viewModel = SettingsViewModel(FakeSettingsRepository(), profileRepository)

        viewModel.onRecencyReductionGrantedChange(true)
        viewModel.onRecencyReductionReferenceChange("REF-456")

        assertTrue(viewModel.recencyReductionGranted.value)
        assertEquals("REF-456", viewModel.recencyReductionReference.value)

        val stored = runBlocking { profileRepository.get() }
        assertTrue(stored?.recencyReductionGranted == true)
        assertEquals("ILT", stored?.recencyReductionAuthority)
        assertEquals("REF-456", stored?.recencyReductionReference)
    }

    @Test
    fun `toggling research-counts-toward-recency persists it independently`() {
        val profileRepository = FakeProfileRepository(profile())
        val viewModel = SettingsViewModel(FakeSettingsRepository(), profileRepository)

        viewModel.onResearchCountsTowardRecencyChange(true)

        assertTrue(viewModel.researchCountsTowardRecency.value)
        assertTrue(runBlocking { profileRepository.get() }?.researchCountsTowardRecency == true)
    }
}
