package nl.part66l.logbook.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import nl.part66l.logbook.domain.DocumentCategory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocumentRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: DocumentRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DocumentRepositoryImpl(db.documents())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `create then byId round-trips the document`() = runBlocking {
        val id = repository.create(name = "AMM", category = DocumentCategory.MANUAL, revision = "Rev 3", link = "https://example.com/amm")

        val document = repository.byId(id)
        assertEquals("AMM", document!!.name)
        assertEquals(DocumentCategory.MANUAL, document.category)
        assertEquals("Rev 3", document.revision)
        assertEquals("https://example.com/amm", document.link)
    }

    @Test
    fun `update replaces the document's fields in place`() = runBlocking {
        val id = repository.create(name = "AMM", category = DocumentCategory.MANUAL, revision = "Rev 3", link = null)

        repository.update(id, name = "AMM v2", category = DocumentCategory.TCDS, revision = "Rev 4", link = "https://example.com")

        val document = repository.byId(id)
        assertEquals("AMM v2", document!!.name)
        assertEquals(DocumentCategory.TCDS, document.category)
        assertEquals("Rev 4", document.revision)
        assertEquals("https://example.com", document.link)
    }

    @Test
    fun `observeAll excludes archived documents unless includeArchived is set`() = runBlocking {
        val activeId = repository.create(name = "AD 2026-01", category = DocumentCategory.AD, revision = null, link = null)
        val archivedId = repository.create(name = "Old regulation", category = DocumentCategory.REGULATION, revision = null, link = null)
        repository.setArchived(archivedId, true)

        val visible = repository.observeAll(includeArchived = false).first()
        assertEquals(listOf(activeId), visible.map { it.id })

        val all = repository.observeAll(includeArchived = true).first()
        assertEquals(setOf(activeId, archivedId), all.map { it.id }.toSet())
    }

    @Test
    fun `delete removes the document`() = runBlocking {
        val id = repository.create(name = "SD 12", category = DocumentCategory.SD, revision = null, link = null)

        repository.delete(id)

        assertNull(repository.byId(id))
    }

    @Test
    fun `byId returns null for an unknown id`() = runBlocking {
        assertNull(repository.byId("does-not-exist"))
    }

    @Test
    fun `create and update round-trip the PDF path and filename`() = runBlocking {
        val id = repository.create(
            name = "AMM", category = DocumentCategory.MANUAL, revision = null, link = null,
            pdfPath = "/data/documents/abc.pdf", pdfFileName = "Aircraft Maintenance Manual.pdf",
        )

        val created = repository.byId(id)
        assertEquals("/data/documents/abc.pdf", created!!.pdfPath)
        assertEquals("Aircraft Maintenance Manual.pdf", created.pdfFileName)

        repository.update(
            id, name = "AMM", category = DocumentCategory.MANUAL, revision = null, link = null,
            pdfPath = "/data/documents/def.pdf", pdfFileName = "AMM v2.pdf",
        )

        val updated = repository.byId(id)
        assertEquals("/data/documents/def.pdf", updated!!.pdfPath)
        assertEquals("AMM v2.pdf", updated.pdfFileName)
    }

    @Test
    fun `observeAll orders by category then name`() = runBlocking {
        repository.create(name = "Zeta manual", category = DocumentCategory.MANUAL, revision = null, link = null)
        repository.create(name = "Alpha AD", category = DocumentCategory.AD, revision = null, link = null)
        repository.create(name = "Beta manual", category = DocumentCategory.MANUAL, revision = null, link = null)

        val names = repository.observeAll(includeArchived = false).first().map { it.name }

        assertEquals(listOf("Alpha AD", "Beta manual", "Zeta manual"), names)
    }
}
