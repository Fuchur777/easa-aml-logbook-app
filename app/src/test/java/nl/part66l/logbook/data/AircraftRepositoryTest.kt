package nl.part66l.logbook.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import nl.part66l.logbook.domain.EntryRole
import nl.part66l.logbook.domain.Propulsion
import nl.part66l.logbook.domain.Structure
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AircraftRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: AircraftRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AircraftRepositoryImpl(db.aircraft())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `create inserts the aircraft and its first registration together`() = runBlocking {
        val id = repository.create(
            manufacturer = "Schleicher",
            type = "ASK 21",
            serialNumber = "21123",
            propulsion = Propulsion.UNPOWERED,
            structure = Structure.WOOD_AND_FABRIC,
            subcategoryOverride = null,
            registration = "PH-1234",
            validFrom = LocalDate.of(2020, 1, 1),
        )

        val all = repository.all().first()
        assertEquals(1, all.size)
        assertEquals(id, all.first().id)
        assertEquals("PH-1234", repository.currentRegistration(id, on = LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `currentRegistration is null before that date`() = runBlocking {
        val id = repository.create(
            manufacturer = "Schleicher", type = "ASK 21", serialNumber = "21123",
            propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
            subcategoryOverride = null, registration = "PH-1234", validFrom = LocalDate.of(2020, 1, 1),
        )

        assertNull(repository.currentRegistration(id, on = LocalDate.of(2019, 1, 1)))
    }

    @Test
    fun `update with an unchanged registration leaves registration history untouched`() = runBlocking {
        val id = repository.create(
            manufacturer = "Schleicher", type = "ASK 21", serialNumber = "21123",
            propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
            subcategoryOverride = null, registration = "PH-1234", validFrom = LocalDate.of(2020, 1, 1),
        )

        repository.update(
            id = id, manufacturer = "Schleicher", type = "ASK 21 (updated)", serialNumber = "21123",
            propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
            subcategoryOverride = null, registration = "ph-1234", registrationValidFrom = LocalDate.of(2026, 1, 1),
        )

        assertEquals("ASK 21 (updated)", repository.byId(id)?.type)
        assertEquals("PH-1234", repository.currentRegistration(id, on = LocalDate.of(2020, 6, 1)))
    }

    @Test
    fun `update with a changed registration preserves the old one for dates before the change`() = runBlocking {
        val id = repository.create(
            manufacturer = "Schleicher", type = "ASK 21", serialNumber = "21123",
            propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
            subcategoryOverride = null, registration = "PH-1234", validFrom = LocalDate.of(2020, 1, 1),
        )

        repository.update(
            id = id, manufacturer = "Schleicher", type = "ASK 21", serialNumber = "21123",
            propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
            subcategoryOverride = null, registration = "PH-9999", registrationValidFrom = LocalDate.of(2026, 6, 1),
        )

        assertEquals("PH-1234", repository.currentRegistration(id, on = LocalDate.of(2026, 5, 31)))
        assertEquals("PH-9999", repository.currentRegistration(id, on = LocalDate.of(2026, 6, 1)))
    }

    @Test
    fun `setArchived flips the flag and observeAll respects includeArchived`() = runBlocking {
        val id = repository.create(
            manufacturer = "Schleicher", type = "ASK 21", serialNumber = "21123",
            propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
            subcategoryOverride = null, registration = "PH-1234", validFrom = LocalDate.of(2020, 1, 1),
        )

        repository.setArchived(id, true)

        assertTrue(repository.byId(id)?.archived == true)
        assertEquals(0, repository.observeAll(includeArchived = false).first().size)
        assertEquals(1, repository.observeAll(includeArchived = true).first().size)
    }

    @Test
    fun `an aircraft with no work history can be deleted`() = runBlocking {
        val id = repository.create(
            manufacturer = "Schleicher", type = "ASK 21", serialNumber = "21123",
            propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
            subcategoryOverride = null, registration = "PH-1234", validFrom = LocalDate.of(2020, 1, 1),
        )

        assertFalse(repository.hasWorkHistory(id))
        repository.delete(id)

        assertNull(repository.byId(id))
    }

    @Test
    fun `the database itself refuses to delete an aircraft with work history`() = runBlocking {
        val id = repository.create(
            manufacturer = "Schleicher", type = "ASK 21", serialNumber = "21123",
            propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
            subcategoryOverride = null, registration = "PH-1234", validFrom = LocalDate.of(2020, 1, 1),
        )
        val now = java.time.Instant.now()
        db.workEntries().insert(
            WorkEntryEntity(
                id = "entry-1", aircraftId = id, description = "Test", role = EntryRole.NO_RELEASE,
                createdAt = now, updatedAt = now,
            ),
        )

        assertTrue(repository.hasWorkHistory(id))
        assertThrows(SQLiteConstraintException::class.java) { runBlocking { repository.delete(id) } }
        Unit
    }

    @Test
    fun `reorder persists the new sortOrder`() = runBlocking {
        val first = repository.create(
            manufacturer = "First", type = "Type", serialNumber = "1",
            propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
            subcategoryOverride = null, registration = "PH-0001", validFrom = LocalDate.of(2020, 1, 1),
        )
        val second = repository.create(
            manufacturer = "Second", type = "Type", serialNumber = "2",
            propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
            subcategoryOverride = null, registration = "PH-0002", validFrom = LocalDate.of(2020, 1, 1),
        )

        repository.reorder(listOf(second, first))

        val ordered = repository.observeAll(includeArchived = true).first()
        assertEquals(listOf(second, first), ordered.map { it.id })
    }

    @Test
    fun `observeAllWithRegistration joins each aircraft with its current registration`() = runBlocking {
        val id = repository.create(
            manufacturer = "Schleicher", type = "ASK 21", serialNumber = "21123",
            propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
            subcategoryOverride = null, registration = "PH-1234", validFrom = LocalDate.of(2020, 1, 1),
        )

        val rows = repository.observeAllWithRegistration(includeArchived = false).first()

        assertEquals(1, rows.size)
        assertEquals(id, rows.first().aircraft.id)
        assertEquals("PH-1234", rows.first().registration)
    }
}
