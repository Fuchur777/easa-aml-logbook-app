package nl.part66l.logbook.ui.documents

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import nl.part66l.logbook.domain.DocumentCategory
import nl.part66l.logbook.fakes.FakeDocumentRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DocumentListViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `documents includes archived ones, unlike the entry-form picker`() = runBlocking {
        val repository = FakeDocumentRepository()
        val activeId = repository.create(name = "AMM", category = DocumentCategory.MANUAL, revision = null, link = null)
        val archivedId = repository.create(name = "Old AD", category = DocumentCategory.AD, revision = null, link = null)
        repository.setArchived(archivedId, true)

        val viewModel = DocumentListViewModel(repository)

        assertEquals(setOf(activeId, archivedId), viewModel.documents.value.map { it.id }.toSet())
    }
}
