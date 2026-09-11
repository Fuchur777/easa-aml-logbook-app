package nl.part66l.logbook.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
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
 * Exercises RecencyDao against a real in-memory SQLite database, rather than
 * trusting that a Room query which compiles also runs correctly — in particular
 * `distinctDays`, which binds the same nullable parameter both as a scalar
 * (`:aircraftIds IS NULL`) and as an `IN (:aircraftIds)` multi-bind.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecencyDaoTest {

    private lateinit var db: AppDatabase
    private val windowStart: LocalDate = LocalDate.of(2024, 1, 1)

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

    private fun aircraft(id: String) = AircraftEntity(
        id = id,
        manufacturer = "Schleicher",
        type = "ASK 21",
        serialNumber = id,
        propulsion = Propulsion.UNPOWERED,
        structure = Structure.WOOD_AND_FABRIC,
    )

    private fun entry(id: String, aircraftId: String?, annualInspection: Boolean = false) = WorkEntryEntity(
        id = id,
        aircraftId = aircraftId,
        description = "test entry",
        annualInspection = annualInspection,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun session(id: String, entryId: String, date: LocalDate) =
        WorkSessionEntity(id = id, entryId = entryId, date = date)

    /** Seeds two aircraft with distinct in-window days, plus one day outside the window. */
    private fun seedDistinctDaysFixture() = runBlocking {
        db.aircraft().insert(aircraft("a1"))
        db.aircraft().insert(aircraft("a2"))
        db.workEntries().insert(entry("e1", "a1"))
        db.workEntries().insert(entry("e2", "a2"))

        db.workSessions().insert(session("s1", "e1", LocalDate.of(2024, 2, 1)))
        db.workSessions().insert(session("s2", "e1", LocalDate.of(2024, 2, 2)))
        db.workSessions().insert(session("s3", "e2", LocalDate.of(2024, 3, 1)))
        // Before the window: must never be counted.
        db.workSessions().insert(session("s4", "e1", LocalDate.of(2023, 6, 1)))
    }

    @Test
    fun `distinctDays with no aircraft filter counts every aircraft's in-window days`() = runBlocking {
        seedDistinctDaysFixture()

        assertEquals(3, db.recency().distinctDays(windowStart))
    }

    @Test
    fun `distinctDaysForAircraft restricted to one aircraft counts only that aircraft's days`() = runBlocking {
        seedDistinctDaysFixture()

        assertEquals(2, db.recency().distinctDaysForAircraft(windowStart, aircraftIds = listOf("a1")))
        assertEquals(1, db.recency().distinctDaysForAircraft(windowStart, aircraftIds = listOf("a2")))
    }

    @Test
    fun `distinctDaysForAircraft with every aircraft named explicitly matches the unfiltered case`() = runBlocking {
        seedDistinctDaysFixture()

        assertEquals(3, db.recency().distinctDaysForAircraft(windowStart, aircraftIds = listOf("a1", "a2")))
    }

    @Test
    fun `daysInWindow returns distinct dates ascending, excluding dates before the window`() = runBlocking {
        seedDistinctDaysFixture()

        assertEquals(
            listOf(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 2), LocalDate.of(2024, 3, 1)),
            db.recency().daysInWindow(windowStart),
        )
    }

    @Test
    fun `completedTasksBySection groups completions by their catalogue section code`() = runBlocking {
        db.workEntries().insert(entry("e1", aircraftId = null))
        db.workEntries().insert(entry("e2", aircraftId = null))
        db.workSessions().insert(session("s1", "e1", LocalDate.of(2024, 2, 1)))
        db.workSessions().insert(session("s2", "e2", LocalDate.of(2024, 3, 1)))

        db.catalogue().upsertAll(listOf(
            CatalogueTaskEntity(
                id = "B.GEN.01", catalogueVersion = "2026.1", table = "B",
                section = "General activities", sectionCode = "GEN",
                text = "Placards check or replace",
                reference = "Appendix II to AMC to Annex III (Part-66), Table B — General activities",
                appliesToL1 = true, appliesToL1C = true, appliesToL2 = true, appliesToL2C = true,
            ),
            CatalogueTaskEntity(
                id = "B.WFAB.02", catalogueVersion = "2026.1", table = "B",
                section = "Wood and fabric structures", sectionCode = "WFAB",
                text = "Repair local skin damage",
                reference = "Appendix II to AMC to Annex III (Part-66), Table B — Wood and fabric structures",
                appliesToL1 = true, appliesToL1C = false, appliesToL2 = true, appliesToL2C = false,
            ),
        ))
        db.taskCompletions().insert(
            TaskCompletionEntity(id = "c1", entryId = "e1", taskId = "B.GEN.01", catalogueVersion = "2026.1", taskTextSnapshot = "Placards check or replace"),
        )
        db.taskCompletions().insert(
            TaskCompletionEntity(id = "c2", entryId = "e2", taskId = "B.WFAB.02", catalogueVersion = "2026.1", taskTextSnapshot = "Repair local skin damage"),
        )

        val bySection = db.recency().completedTasksBySection(windowStart).associate { it.sectionCode to it.completed }

        assertEquals(mapOf("GEN" to 1, "WFAB" to 1), bySection)
    }

    @Test
    fun `annualInspections counts distinct annual-flagged entries with an in-window session`() = runBlocking {
        db.workEntries().insert(entry("e1", aircraftId = null, annualInspection = true))
        db.workEntries().insert(entry("e2", aircraftId = null, annualInspection = true))
        db.workSessions().insert(session("s1", "e1", LocalDate.of(2024, 4, 1)))
        // Outside the window: must not be counted.
        db.workSessions().insert(session("s2", "e2", LocalDate.of(2023, 1, 1)))

        assertEquals(1, db.recency().annualInspections(windowStart))
    }
}
