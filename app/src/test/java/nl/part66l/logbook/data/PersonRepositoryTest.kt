package nl.part66l.logbook.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersonRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: PersonRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PersonRepositoryImpl(db.people())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `create then byId round-trips the contact`() = runBlocking {
        val id = repository.create(name = "Jan de Vries", licenceNumber = "NL.66.11111", email = "jan@example.com")

        val contact = repository.byId(id)
        assertEquals("Jan de Vries", contact!!.name)
        assertEquals("NL.66.11111", contact.licenceNumber)
        assertEquals("jan@example.com", contact.email)
    }

    @Test
    fun `update replaces the contact's fields in place`() = runBlocking {
        val id = repository.create(name = "Jan de Vries", licenceNumber = null, email = null)

        repository.update(id, name = "Jan de Vries Jr.", licenceNumber = "NL.66.22222", email = "jan.jr@example.com")

        val contact = repository.byId(id)
        assertEquals("Jan de Vries Jr.", contact!!.name)
        assertEquals("NL.66.22222", contact.licenceNumber)
        assertEquals("jan.jr@example.com", contact.email)
    }

    @Test
    fun `observeAll excludes archived contacts unless includeArchived is set`() = runBlocking {
        val activeId = repository.create("Jan de Vries", null, null)
        val archivedId = repository.create("Piet Bakker", null, null)
        repository.setArchived(archivedId, true)

        val visible = repository.observeAll(includeArchived = false).first()
        assertEquals(listOf(activeId), visible.map { it.id })

        val all = repository.observeAll(includeArchived = true).first()
        assertEquals(setOf(activeId, archivedId), all.map { it.id }.toSet())
    }

    @Test
    fun `delete removes the contact`() = runBlocking {
        val id = repository.create("Jan de Vries", null, null)

        repository.delete(id)

        assertNull(repository.byId(id))
    }

    @Test
    fun `findOrCreate returns the existing id for a known name, without touching an unrelated licence`() = runBlocking {
        val id = repository.create("Jan de Vries", "NL.66.11111", null)

        val foundId = repository.findOrCreate("Jan de Vries")

        assertEquals(id, foundId)
        assertEquals("NL.66.11111", repository.byId(id)!!.licenceNumber)
    }

    @Test
    fun `findOrCreate persists a licence number given for an existing person`() = runBlocking {
        val id = repository.create("Jan de Vries", null, null)

        repository.findOrCreate("Jan de Vries", "NL.66.11111")

        assertEquals("NL.66.11111", repository.byId(id)!!.licenceNumber)
    }

    @Test
    fun `findOrCreate creates a new person with the given licence number when the name isn't on file`() = runBlocking {
        val id = repository.findOrCreate("Piet Bakker", "NL.66.22222")

        val person = repository.byId(id)
        assertEquals("Piet Bakker", person!!.name)
        assertEquals("NL.66.22222", person.licenceNumber)
        assertTrue(!person.archived)
    }
}
