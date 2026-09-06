package nl.part66l.logbook.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import nl.part66l.logbook.domain.ActivityType
import nl.part66l.logbook.domain.EntryRole
import nl.part66l.logbook.domain.Propulsion
import nl.part66l.logbook.domain.Structure
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the FTS4 index and identifier search against real SQLite — the FTS
 * tokenizer configuration and rowid handling are exactly the kind of thing that
 * compiles fine and misbehaves at runtime.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entry(id: String, description: String, aircraftId: String? = null) = WorkEntryEntity(
        id = id,
        aircraftId = aircraftId,
        description = description,
        activityType = ActivityType.SERVICING,
        role = EntryRole.CERTIFIED_BY_ME_IN_APP,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `byText finds an entry by a description token, via the repository`() = runBlocking {
        db.workEntries().insert(entry("e1", "Replace rudder cable"))
        db.search().upsertIndex(
            WorkEntryFts(rowId = 1, entryId = "e1", description = "Replace rudder cable", workorder = "", people = ""),
        )

        val results = SearchRepository(db.search()).search("rudder")

        assertEquals(listOf("e1"), results.map { it.id })
    }

    @Test
    fun `byText still finds an earlier entry once a second entry is indexed, with explicit rowids`() = runBlocking {
        db.workEntries().insert(entry("e1", "Replace rudder cable"))
        db.workEntries().insert(entry("e2", "Inspect elevator hinge"))
        db.search().upsertIndex(
            WorkEntryFts(rowId = 1, entryId = "e1", description = "Replace rudder cable", workorder = "", people = ""),
        )
        db.search().upsertIndex(
            WorkEntryFts(rowId = 2, entryId = "e2", description = "Inspect elevator hinge", workorder = "", people = ""),
        )

        assertEquals(listOf("e1"), SearchRepository(db.search()).search("rudder").map { it.id })
        assertEquals(listOf("e2"), SearchRepository(db.search()).search("elevator").map { it.id })
    }

    @Test
    fun `indexing two entries without an explicit rowid does not silently collide`() = runBlocking {
        // WorkEntryFts.rowId defaults to 0. upsertIndex is REPLACE-on-conflict, so if
        // nothing assigns a real rowid, the second index write can silently overwrite
        // the first rather than adding a second FTS row.
        db.workEntries().insert(entry("e1", "Replace rudder cable"))
        db.workEntries().insert(entry("e2", "Inspect elevator hinge"))
        db.search().upsertIndex(WorkEntryFts(entryId = "e1", description = "Replace rudder cable", workorder = "", people = ""))
        db.search().upsertIndex(WorkEntryFts(entryId = "e2", description = "Inspect elevator hinge", workorder = "", people = ""))

        assertEquals(listOf("e1"), SearchRepository(db.search()).search("rudder").map { it.id })
        assertEquals(listOf("e2"), SearchRepository(db.search()).search("elevator").map { it.id })
    }

    @Test
    fun `byIdentifier finds an entry by a registration prefix`() = runBlocking {
        db.aircraft().insert(
            AircraftEntity(
                id = "a1", manufacturer = "Schleicher", type = "ASK 21", serialNumber = "21123",
                propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
            ),
        )
        db.aircraft().insertRegistration(
            AircraftRegistrationEntity(
                id = "r1", aircraftId = "a1", registration = "PH-1234",
                registrationNormalised = Identifiers.normalise("PH-1234"),
                validFrom = LocalDate.of(2020, 1, 1), validTo = null,
            ),
        )
        db.workEntries().insert(entry("e1", "Annual inspection", aircraftId = "a1"))

        val results = SearchRepository(db.search()).search("PH-12")

        assertEquals(listOf("e1"), results.map { it.id })
    }
}
