package nl.part66l.logbook.ui.profile

import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import nl.part66l.logbook.data.ProfileEntity
import nl.part66l.logbook.domain.Subcategory
import nl.part66l.logbook.fakes.FakeProfileRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * UnconfinedTestDispatcher on Main so viewModelScope.launch runs eagerly —
 * FakeProfileRepository never genuinely suspends, so every launched coroutine
 * completes synchronously within the call that triggered it.
 */
class ProfileViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `canSave is false until a name and at least one subcategory are set`() {
        val viewModel = ProfileViewModel(FakeProfileRepository())

        assertFalse(viewModel.state.value.canSave)

        viewModel.onNameChange("F. Example")
        assertFalse(viewModel.state.value.canSave) // no subcategory yet

        viewModel.onSubcategoryToggle(Subcategory.L1, true)
        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun `save persists the entered profile`() {
        val repository = FakeProfileRepository()
        val viewModel = ProfileViewModel(repository)

        viewModel.onNameChange("F. Example")
        viewModel.onSubcategoryToggle(Subcategory.L1, true)
        viewModel.onLicenceNumberChange("NL.66.00000")
        viewModel.save()

        val stored = runBlocking { repository.get() }
        assertEquals("F. Example", stored?.name)
        assertEquals("NL.66.00000", stored?.licenceNumber)
        assertTrue(stored?.holdsL1 == true)
    }

    @Test
    fun `save is a no-op when the form is invalid`() {
        val repository = FakeProfileRepository()
        val viewModel = ProfileViewModel(repository)

        viewModel.save() // no name, no subcategory

        assertNull(runBlocking { repository.get() })
    }

    @Test
    fun `an existing profile is loaded into the form on start`() {
        val existing = ProfileEntity(
            name = "Existing Pilot", licenceNumber = "NL.66.99999",
            issuingAuthority = "ILT", licenceExpiry = null, holdsL2 = true,
        )

        val viewModel = ProfileViewModel(FakeProfileRepository(existing))

        assertEquals("Existing Pilot", viewModel.state.value.name)
        assertTrue(viewModel.state.value.holdsL2)
    }

    @Test
    fun `saved reduction authority always mirrors the licence issuing authority, never asked separately`() {
        val repository = FakeProfileRepository()
        val viewModel = ProfileViewModel(repository)
        viewModel.onNameChange("F. Example")
        viewModel.onSubcategoryToggle(Subcategory.L1, true)
        viewModel.onIssuingAuthorityChange("ILT")
        viewModel.onRecencyReductionGrantedChange(true)
        viewModel.onRecencyReductionReferenceChange("REF-123")

        viewModel.save()

        val stored = runBlocking { repository.get() }
        assertEquals("ILT", stored?.recencyReductionAuthority)
    }

    @Test
    fun `isDirty is false until a field changes, and false again after saving`() {
        val viewModel = ProfileViewModel(FakeProfileRepository())
        assertFalse(viewModel.isDirty())

        viewModel.onNameChange("F. Example")
        assertTrue(viewModel.isDirty())

        viewModel.onSubcategoryToggle(Subcategory.L1, true)
        viewModel.save()
        assertFalse(viewModel.isDirty())
    }

    @Test
    fun `isDirty compares against the loaded profile, not a blank form`() {
        val existing = ProfileEntity(
            name = "Existing Pilot", licenceNumber = "NL.66.99999",
            issuingAuthority = "ILT", licenceExpiry = null, holdsL2 = true,
        )
        val viewModel = ProfileViewModel(FakeProfileRepository(existing))

        assertFalse(viewModel.isDirty())

        viewModel.onNameChange("Existing Pilot") // re-typing the same value
        assertFalse(viewModel.isDirty())

        viewModel.onNameChange("Changed Name")
        assertTrue(viewModel.isDirty())
    }

    @Test
    fun `canSave is false when valid-from is after valid-till`() {
        val viewModel = ProfileViewModel(FakeProfileRepository())
        viewModel.onNameChange("F. Example")
        viewModel.onSubcategoryToggle(Subcategory.L1, true)

        viewModel.onLicenceValidFromChange(LocalDate.of(2026, 12, 31))
        viewModel.onLicenceExpiryChange(LocalDate.of(2026, 1, 1))

        assertFalse(viewModel.state.value.canSave)
        assertEquals("Valid from must not be after valid till", viewModel.state.value.licenceDatesError)
    }

    @Test
    fun `save persists phone, email and licence dates`() {
        val repository = FakeProfileRepository()
        val viewModel = ProfileViewModel(repository)
        viewModel.onNameChange("F. Example")
        viewModel.onSubcategoryToggle(Subcategory.L1, true)
        viewModel.onPhoneNumberChange("+31 6 12345678")
        viewModel.onEmailChange("frank@example.com")
        viewModel.onLicenceValidFromChange(LocalDate.of(2026, 1, 1))
        viewModel.onLicenceExpiryChange(LocalDate.of(2031, 1, 1))

        viewModel.save()

        val stored = runBlocking { repository.get() }
        assertEquals("+31 6 12345678", stored?.phoneNumber)
        assertEquals("frank@example.com", stored?.email)
        assertEquals(LocalDate.of(2026, 1, 1), stored?.licenceValidFrom)
        assertEquals(LocalDate.of(2031, 1, 1), stored?.licenceExpiry)
    }
}
