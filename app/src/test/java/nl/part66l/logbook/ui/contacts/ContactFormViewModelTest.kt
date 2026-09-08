package nl.part66l.logbook.ui.contacts

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import nl.part66l.logbook.fakes.FakePersonRepository
import nl.part66l.logbook.ui.navigation.Destination
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContactFormViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newState(contactId: String? = null) = SavedStateHandle(
        contactId?.let { mapOf(Destination.ContactEdit.ARG_CONTACT_ID to it) } ?: emptyMap(),
    )

    @Test
    fun `canSave requires a name`() {
        val viewModel = ContactFormViewModel(newState(), FakePersonRepository())

        assertFalse(viewModel.state.value.canSave)

        viewModel.onNameChange("Jan de Vries")
        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun `save creates a new contact with the entered fields`() {
        val repository = FakePersonRepository()
        val viewModel = ContactFormViewModel(newState(), repository)
        viewModel.onNameChange("Jan de Vries")
        viewModel.onLicenceNumberChange("NL.66.11111")
        viewModel.onEmailChange("jan@example.com")

        viewModel.save()

        val created = runBlocking { repository.observeAll(includeArchived = true).first() }.first()
        assertEquals("Jan de Vries", created.name)
        assertEquals("NL.66.11111", created.licenceNumber)
        assertEquals("jan@example.com", created.email)
    }

    @Test
    fun `loading an existing contact populates the form and archive toggle flips it`() {
        val repository = FakePersonRepository()
        val id = runBlocking { repository.create("Jan de Vries", "NL.66.11111", null) }

        val viewModel = ContactFormViewModel(newState(id), repository)
        assertEquals("Jan de Vries", viewModel.state.value.name)
        assertEquals("NL.66.11111", viewModel.state.value.licenceNumber)
        assertFalse(viewModel.state.value.archived)

        viewModel.onArchiveToggle()
        assertTrue(viewModel.state.value.archived)
        assertTrue(runBlocking { repository.byId(id) }!!.archived)
    }

    @Test
    fun `isDirty is false until a field changes, and false again after saving`() {
        val viewModel = ContactFormViewModel(newState(), FakePersonRepository())
        assertFalse(viewModel.isDirty())

        viewModel.onNameChange("Jan de Vries")
        assertTrue(viewModel.isDirty())

        viewModel.save()
        assertFalse(viewModel.isDirty())
    }

    @Test
    fun `delete removes the contact`() = runBlocking {
        val repository = FakePersonRepository()
        val id = repository.create("Jan de Vries", null, null)

        val viewModel = ContactFormViewModel(newState(id), repository)
        viewModel.delete()

        assertNull(repository.byId(id))
    }
}
