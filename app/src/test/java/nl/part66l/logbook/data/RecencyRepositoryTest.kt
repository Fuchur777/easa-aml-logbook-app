package nl.part66l.logbook.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import nl.part66l.logbook.domain.ActivityType
import nl.part66l.logbook.domain.EntryRole
import nl.part66l.logbook.domain.Propulsion
import nl.part66l.logbook.domain.RecencyRoute
import nl.part66l.logbook.domain.Structure
import nl.part66l.logbook.domain.Subcategory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the wiring between stored rows and RecencyEvaluator — not the
 * evaluator's own math (covered by RecencyEvaluatorTest), but whether the
 * repository maps entities to the right evaluator inputs: aircraft-specific
 * subcategory attribution for Route A/C, and catalogue-driven (not
 * aircraft-driven) subcategory crediting for Route B.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecencyRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RecencyRepository
    private val today: LocalDate = LocalDate.of(2026, 9, 4)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RecencyRepository(db.profile(), db.aircraft(), db.recency(), db.catalogue())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun profile(holdsL1: Boolean = false, holdsL2: Boolean = false) = ProfileEntity(
        name = "Test Pilot",
        licenceNumber = "L-123",
        issuingAuthority = "ILT",
        licenceExpiry = null,
        holdsL1 = holdsL1,
        holdsL2 = holdsL2,
    )

    private fun aircraft(id: String, propulsion: Propulsion, structure: Structure) = AircraftEntity(
        id = id, manufacturer = "Schleicher", type = "ASK 21", serialNumber = id,
        propulsion = propulsion, structure = structure,
    )

    private fun entry(id: String, aircraftId: String?) = WorkEntryEntity(
        id = id, aircraftId = aircraftId, description = "test entry",
        activityType = ActivityType.SERVICING, role = EntryRole.CERTIFIED_BY_ME_IN_APP,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    private fun session(id: String, entryId: String, date: LocalDate) =
        WorkSessionEntity(id = id, entryId = entryId, date = date)

    @Test
    fun `days on an aircraft credit only its resolved subcategory, bench work credits every held subcategory`() = runBlocking {
        db.profile().upsert(profile(holdsL1 = true, holdsL2 = true))
        // a1: unpowered, wood/fabric -> L1. a2: powered, wood/fabric -> L2.
        db.aircraft().insert(aircraft("a1", Propulsion.UNPOWERED, Structure.WOOD_AND_FABRIC))
        db.aircraft().insert(aircraft("a2", Propulsion.POWERED_SAILPLANE, Structure.WOOD_AND_FABRIC))
        db.workEntries().insert(entry("e1", "a1"))
        db.workEntries().insert(entry("e2", "a2"))
        db.workEntries().insert(entry("e3", aircraftId = null)) // bench work

        db.workSessions().insert(session("s1", "e1", LocalDate.of(2025, 1, 1)))
        db.workSessions().insert(session("s2", "e2", LocalDate.of(2025, 2, 1)))
        db.workSessions().insert(session("s3", "e3", LocalDate.of(2025, 3, 1)))

        val results = repository.evaluate(today, catalogueVersion = "none")

        val l1Days = results.first { it.subcategory == Subcategory.L1 }.routes.first { it.route == RecencyRoute.DAYS }
        val l2Days = results.first { it.subcategory == Subcategory.L2 }.routes.first { it.route == RecencyRoute.DAYS }

        // Each aircraft's day plus the bench day: 2 per subcategory, not 3.
        assertEquals(2, l1Days.have)
        assertEquals(2, l2Days.have)
    }

    @Test
    fun `task completions credit every subcategory the catalogue task applies to, not just the work aircraft's`() = runBlocking {
        db.profile().upsert(profile(holdsL1 = true, holdsL2 = true))
        db.workEntries().insert(entry("e1", aircraftId = null))
        db.workSessions().insert(session("s1", "e1", LocalDate.of(2025, 1, 1)))

        db.catalogue().upsertAll(listOf(
            CatalogueTaskEntity(
                id = "L1_ONLY", catalogueVersion = "2026.1", table = "B",
                section = "General activities", sectionCode = "GEN", text = "L1-only task",
                reference = "ref", appliesToL1 = true, appliesToL1C = false, appliesToL2 = false, appliesToL2C = false,
            ),
            CatalogueTaskEntity(
                id = "L1_AND_L2", catalogueVersion = "2026.1", table = "B",
                section = "Metal structures", sectionCode = "METAL", text = "Applies to both",
                reference = "ref", appliesToL1 = true, appliesToL1C = false, appliesToL2 = true, appliesToL2C = false,
            ),
        ))
        db.taskCompletions().insert(
            TaskCompletionEntity(id = "c1", entryId = "e1", taskId = "L1_ONLY", catalogueVersion = "2026.1", taskTextSnapshot = "L1-only task"),
        )
        db.taskCompletions().insert(
            TaskCompletionEntity(id = "c2", entryId = "e1", taskId = "L1_AND_L2", catalogueVersion = "2026.1", taskTextSnapshot = "Applies to both"),
        )

        val results = repository.evaluate(today, catalogueVersion = "2026.1")

        val l1Tasks = results.first { it.subcategory == Subcategory.L1 }.routes.first { it.route == RecencyRoute.TASKS }
        val l2Tasks = results.first { it.subcategory == Subcategory.L2 }.routes.first { it.route == RecencyRoute.TASKS }

        assertEquals(2, l1Tasks.have) // both tasks apply to L1
        assertEquals(1, l2Tasks.have) // only the shared task applies to L2
    }

    @Test
    fun `evaluate returns nothing when no profile has been recorded`() = runBlocking {
        assertTrue(repository.evaluate(today, catalogueVersion = "2026.1").isEmpty())
    }
}
