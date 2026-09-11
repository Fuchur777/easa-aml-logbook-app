package nl.part66l.logbook.ui.contacts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import nl.part66l.logbook.fakes.FakePersonRepository
import nl.part66l.logbook.fakes.FakeSettingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ContactListViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `archived contacts are hidden until the toggle asks for them, same as the other directories`() {
        val repository = FakePersonRepository()
        runBlocking {
            repository.create("Jan de Vries", null, null)
            val archivedId = repository.create("Piet Bakker", null, null)
            repository.setArchived(archivedId, true)
        }

        val viewModel = ContactListViewModel(repository, FakeSettingsRepository())

        assertEquals(1, viewModel.contacts.value.size)

        viewModel.onShowArchivedChange(true)

        assertEquals(2, viewModel.contacts.value.size)
    }
}
