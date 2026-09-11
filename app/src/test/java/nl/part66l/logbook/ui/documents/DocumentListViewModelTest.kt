package nl.part66l.logbook.ui.documents

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import nl.part66l.logbook.domain.DocumentCategory
import nl.part66l.logbook.fakes.FakeAircraftRepository
import nl.part66l.logbook.fakes.FakeDocumentRepository
import nl.part66l.logbook.fakes.FakeSettingsRepository
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

    private fun viewModel(repository: FakeDocumentRepository) =
        DocumentListViewModel(repository, FakeAircraftRepository(), FakeSettingsRepository())

    @Test
    fun `archived documents are hidden until the toggle asks for them`() = runBlocking {
        val repository = FakeDocumentRepository()
        val activeId = repository.create(name = "AMM", category = DocumentCategory.MANUAL, revision = null, link = null)
        val archivedId = repository.create(name = "Old AD", category = DocumentCategory.AD, revision = null, link = null)
        repository.setArchived(archivedId, true)

        val viewModel = viewModel(repository)

        assertEquals(listOf(activeId), viewModel.documents.value.map { it.id })

        viewModel.onShowArchivedChange(true)

        assertEquals(setOf(activeId, archivedId), viewModel.documents.value.map { it.id }.toSet())
    }

    @Test
    fun `the category filter narrows to one category and back`() = runBlocking {
        val repository = FakeDocumentRepository()
        val manualId = repository.create(name = "AMM", category = DocumentCategory.MANUAL, revision = null, link = null)
        val adId = repository.create(name = "AD 2026-01", category = DocumentCategory.AD, revision = null, link = null)

        val viewModel = viewModel(repository)

        viewModel.onCategoryChange(DocumentCategory.AD)
        assertEquals(listOf(adId), viewModel.documents.value.map { it.id })

        viewModel.onCategoryChange(null)
        assertEquals(setOf(manualId, adId), viewModel.documents.value.map { it.id }.toSet())
    }

    @Test
    fun `the aircraft filter shows only documents used on that aircraft`() = runBlocking {
        val repository = FakeDocumentRepository()
        val usedId = repository.create(name = "ASK 21 AMM", category = DocumentCategory.MANUAL, revision = null, link = null)
        repository.create(name = "Unrelated TCDS", category = DocumentCategory.TCDS, revision = null, link = null)
        repository.usageByAircraft["aircraft-1"] = setOf(usedId)

        val viewModel = viewModel(repository)

        viewModel.onAircraftChange("aircraft-1")

        assertEquals(listOf(usedId), viewModel.documents.value.map { it.id })
    }
}
