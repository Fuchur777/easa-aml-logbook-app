package nl.part66l.logbook.ui.documents

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import nl.part66l.logbook.domain.DocumentCategory
import nl.part66l.logbook.fakes.FakeDocumentRepository
import nl.part66l.logbook.ui.navigation.Destination
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DocumentFormViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newState(documentId: String? = null) = SavedStateHandle(
        documentId?.let { mapOf(Destination.DocumentEdit.ARG_DOCUMENT_ID to it) } ?: emptyMap(),
    )

    @Test
    fun `canSave requires a name`() {
        val viewModel = DocumentFormViewModel(newState(), FakeDocumentRepository())

        assertFalse(viewModel.state.value.canSave)

        viewModel.onNameChange("AMM")
        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun `save creates a new document with the entered fields`() {
        val repository = FakeDocumentRepository()
        val viewModel = DocumentFormViewModel(newState(), repository)
        viewModel.onNameChange("AMM")
        viewModel.onCategoryChange(DocumentCategory.MANUAL)
        viewModel.onRevisionChange("Rev 3")
        viewModel.onLinkChange("https://example.com")

        viewModel.save()

        val created = runBlocking { repository.observeAll(includeArchived = true).first() }.first()
        assertEquals("AMM", created.name)
        assertEquals(DocumentCategory.MANUAL, created.category)
        assertEquals("Rev 3", created.revision)
        assertEquals("https://example.com", created.link)
    }

    @Test
    fun `loading an existing document populates the form and archive toggle flips it`() {
        val repository = FakeDocumentRepository()
        val id = runBlocking {
            repository.create(
                name = "AMM", category = DocumentCategory.MANUAL, revision = "Rev 3", link = null,
                pdfPath = "/data/documents/abc.pdf", pdfFileName = "AMM.pdf",
            )
        }

        val viewModel = DocumentFormViewModel(newState(id), repository)
        assertEquals("AMM", viewModel.state.value.name)
        assertEquals("/data/documents/abc.pdf", viewModel.state.value.pdfPath)
        assertEquals("AMM.pdf", viewModel.state.value.pdfFileName)
        assertFalse(viewModel.state.value.archived)

        viewModel.onArchiveToggle()
        assertTrue(viewModel.state.value.archived)
        assertTrue(runBlocking { repository.byId(id) }!!.archived)
    }

    @Test
    fun `isDirty is false until a field changes, and false again after saving`() {
        val viewModel = DocumentFormViewModel(newState(), FakeDocumentRepository())
        assertFalse(viewModel.isDirty())

        viewModel.onNameChange("AMM")
        assertTrue(viewModel.isDirty())

        viewModel.save()
        assertFalse(viewModel.isDirty())
    }

    @Test
    fun `attaching and removing a PDF updates state, and the attachment is saved`() {
        val repository = FakeDocumentRepository()
        val viewModel = DocumentFormViewModel(newState(), repository)
        viewModel.onNameChange("AMM")

        viewModel.onPdfAttached("/data/documents/abc.pdf", "Aircraft Maintenance Manual.pdf")
        assertEquals("/data/documents/abc.pdf", viewModel.state.value.pdfPath)
        assertEquals("Aircraft Maintenance Manual.pdf", viewModel.state.value.pdfFileName)

        viewModel.save()

        val created = runBlocking { repository.observeAll(includeArchived = true).first() }.first()
        assertEquals("/data/documents/abc.pdf", created.pdfPath)
        assertEquals("Aircraft Maintenance Manual.pdf", created.pdfFileName)

        viewModel.onPdfRemoved()
        assertEquals("", viewModel.state.value.pdfPath)
        assertEquals("", viewModel.state.value.pdfFileName)
    }

    @Test
    fun `delete removes the document`() = runBlocking {
        val repository = FakeDocumentRepository()
        val id = repository.create(name = "AMM", category = DocumentCategory.MANUAL, revision = null, link = null)

        val viewModel = DocumentFormViewModel(newState(id), repository)
        viewModel.delete()

        assertNull(repository.byId(id))
    }
}
