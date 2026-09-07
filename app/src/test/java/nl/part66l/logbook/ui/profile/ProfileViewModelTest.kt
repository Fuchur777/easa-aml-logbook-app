package nl.part66l.logbook.ui.profile

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
}
