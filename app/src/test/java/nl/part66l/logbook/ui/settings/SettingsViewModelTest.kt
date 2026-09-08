package nl.part66l.logbook.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

    @Test
    fun `CRS numbering loads its stored values`() {
        val settingsRepository = FakeSettingsRepository(
            initialCrsNumberTemplate = "NL-{YYYY}-{SEQ:5}",
            initialCrsNumberPrefix = "NL",
            initialCrsAnnualReset = false,
            initialCrsStartAt = 100,
        )
        val viewModel = SettingsViewModel(settingsRepository, FakeProfileRepository())

        assertEquals("NL-{YYYY}-{SEQ:5}", viewModel.crsNumberTemplate.value)
        assertEquals("NL", viewModel.crsNumberPrefix.value)
        assertFalse(viewModel.crsAnnualReset.value)
        assertEquals("100", viewModel.crsStartAt.value)
    }

    @Test
    fun `a valid CRS numbering change is persisted with no error`() {
        val settingsRepository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(settingsRepository, FakeProfileRepository())

        viewModel.onCrsNumberTemplateChange("{PREFIX}-{SEQ:6}")
        viewModel.onCrsNumberPrefixChange("WO")
        viewModel.onCrsStartAtChange("50")

        assertEquals(null, viewModel.crsNumberingError.value)
        assertEquals("{PREFIX}-{SEQ:6}", runBlocking { settingsRepository.crsNumberTemplate.first() })
        assertEquals("WO", runBlocking { settingsRepository.crsNumberPrefix.first() })
        assertEquals(50, runBlocking { settingsRepository.crsStartAt.first() })
    }

    @Test
    fun `an invalid CRS numbering template shows an error and is not persisted`() {
        val settingsRepository = FakeSettingsRepository(initialCrsNumberTemplate = "{PREFIX}-{SEQ:4}")
        val viewModel = SettingsViewModel(settingsRepository, FakeProfileRepository())

        viewModel.onCrsNumberTemplateChange("{PREFIX}-no-sequence-placeholder")

        assertEquals("{PREFIX}-no-sequence-placeholder", viewModel.crsNumberTemplate.value) // still reflected, not snapped back
        assertTrue(viewModel.crsNumberingError.value != null)
        assertEquals("{PREFIX}-{SEQ:4}", runBlocking { settingsRepository.crsNumberTemplate.first() }) // unchanged
    }

    @Test
    fun `a non-numeric start-at shows an error and is not persisted`() {
        val settingsRepository = FakeSettingsRepository(initialCrsStartAt = 1)
        val viewModel = SettingsViewModel(settingsRepository, FakeProfileRepository())

        viewModel.onCrsStartAtChange("not a number")

        assertEquals("Start-at must be a whole number", viewModel.crsNumberingError.value)
        assertEquals(1, runBlocking { settingsRepository.crsStartAt.first() })
    }
}
