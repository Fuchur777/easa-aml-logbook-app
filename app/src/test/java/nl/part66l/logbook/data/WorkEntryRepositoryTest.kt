package nl.part66l.logbook.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.testing.asSnapshot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import nl.part66l.logbook.domain.ActivityType
import nl.part66l.logbook.domain.EntryRole
import nl.part66l.logbook.domain.Propulsion
import nl.part66l.logbook.domain.Structure
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `pagedAll`/`filtered` aren't exercised here — they're thin passthroughs to
 * WorkEntryDao's already-tested queries. `pagedAllWithDetails` is, via
 * paging-testing's `asSnapshot`, since it has its own join/ordering logic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkEntryRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: WorkEntryRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WorkEntryRepositoryImpl(
            db.workEntries(), db.workSessions(), db.entryHelpers(), PersonRepositoryImpl(db.people()),
            db.catalogue(), db.taskCompletions(),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `create inserts the entry, its activity types and its first session together`() = runBlocking {
        val id = repository.create(
            aircraftId = null,
            description = "Bench work on a spare altimeter",
            activityTypes = setOf(ActivityType.TROUBLESHOOTING, ActivityType.REPAIRING),
            role = EntryRole.CERTIFIED_BY_ME_IN_APP,
            supervisedAnother = false,
            sessionDate = LocalDate.of(2026, 1, 15),
        )

        val entry = db.workEntries().byId(id)
        assertNotNull(entry)
        assertEquals("Bench work on a spare altimeter", entry!!.description)

        val sessions = db.workSessions().forEntry(id)
        assertEquals(1, sessions.size)
        assertEquals(LocalDate.of(2026, 1, 15), sessions.first().date)

        val row = Pager(PagingConfig(pageSize = 20)) { repository.pagedAllWithDetails() }.flow.asSnapshot().first()
        assertEquals(setOf(ActivityType.TROUBLESHOOTING, ActivityType.REPAIRING), row.activityTypes.toSet())
    }

    @Test
    fun `create resolves helper names to the person directory, creating new ones as needed`() = runBlocking {
        val existingPersonId = PersonRepositoryImpl(db.people()).findOrCreate("Jan de Vries")

        val id = repository.create(
            aircraftId = null,
            description = "Two-person inspection",
            activityTypes = setOf(ActivityType.INSPECTION),
            role = EntryRole.CERTIFIED_BY_ME_IN_APP,
            supervisedAnother = true,
            sessionDate = LocalDate.of(2026, 1, 15),
            helperNames = listOf("Jan de Vries", "Piet Bakker"),
        )

        val helpers = db.entryHelpers().forEntry(id)
        assertEquals(2, helpers.size)
        assertTrue(helpers.any { it.personId == existingPersonId })
        val allPeople = db.people().all().first()
        assertEquals(2, allPeople.size)
        assertTrue(allPeople.any { it.name == "Piet Bakker" })
    }

    @Test
    fun `create resolves completed task ids into snapshotted TaskCompletionEntity rows`() = runBlocking {
        db.catalogue().upsertAll(listOf(
            CatalogueTaskEntity(
                id = "T1", catalogueVersion = "2026.1", table = "B", section = "General activities", sectionCode = "GEN",
                text = "Task text as seeded", reference = "ref", appliesToL1 = true, appliesToL1C = false, appliesToL2 = false, appliesToL2C = false,
            ),
        ))

        val id = repository.create(
            aircraftId = null,
            description = "Practical task work",
            activityTypes = setOf(ActivityType.SERVICING),
            role = EntryRole.CERTIFIED_BY_ME_IN_APP,
            supervisedAnother = false,
            sessionDate = LocalDate.of(2026, 1, 15),
            completedTaskIds = setOf("T1"),
        )

        val completions = db.taskCompletions().forEntry(id)
        assertEquals(1, completions.size)
        assertEquals("T1", completions.first().taskId)
        assertEquals("2026.1", completions.first().catalogueVersion)
        assertEquals("Task text as seeded", completions.first().taskTextSnapshot)
    }

    @Test
    fun `pagedAllWithDetails joins the work date, current aircraft registration and activity types, newest first`() = runBlocking {
        val aircraftRepository = AircraftRepositoryImpl(db.aircraft())
        val aircraftId = aircraftRepository.create(
            manufacturer = "Schleicher", type = "ASK 21", serialNumber = "21123",
            propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
            subcategoryOverride = null, registration = "PH-1234", validFrom = LocalDate.of(2020, 1, 1),
        )
        repository.create(
            aircraftId = aircraftId, description = "Older entry", activityTypes = setOf(ActivityType.INSPECTION),
            role = EntryRole.NO_RELEASE, supervisedAnother = false, sessionDate = LocalDate.of(2026, 1, 1),
        )
        repository.create(
            aircraftId = null, description = "Newer bench entry", activityTypes = setOf(ActivityType.SERVICING),
            role = EntryRole.NO_RELEASE, supervisedAnother = false, sessionDate = LocalDate.of(2026, 6, 1),
        )

        val snapshot = Pager(PagingConfig(pageSize = 20)) { repository.pagedAllWithDetails() }.flow.asSnapshot()

        assertEquals(2, snapshot.size)
        assertEquals("Newer bench entry", snapshot[0].entry.description)
        assertEquals(LocalDate.of(2026, 6, 1), snapshot[0].workDate)
        assertEquals(null, snapshot[0].aircraftRegistration)
        assertEquals(listOf(ActivityType.SERVICING), snapshot[0].activityTypes)
        assertEquals("Older entry", snapshot[1].entry.description)
        assertEquals("PH-1234", snapshot[1].aircraftRegistration)
    }
}
