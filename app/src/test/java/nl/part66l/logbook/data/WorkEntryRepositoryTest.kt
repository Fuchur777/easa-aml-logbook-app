package nl.part66l.logbook.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.testing.asSnapshot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
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
            db.workEntries(), db.workSessions(), db.entryHelpers(), PersonRepositoryImpl(db.people()), db.people(),
            db.catalogue(), db.taskCompletions(), db.documentationRefs(), db.partsUsed(), db.attachments(),
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
            sessionDates = listOf(LocalDate.of(2026, 1, 15)),
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
    fun `create inserts one session per date, and daysWorkedOverride round-trips through forEdit`() = runBlocking {
        val id = repository.create(
            aircraftId = null,
            description = "Multi-day annual",
            activityTypes = setOf(ActivityType.INSPECTION),
            role = EntryRole.CERTIFIED_BY_ME_IN_APP,
            supervisedAnother = false,
            sessionDates = listOf(LocalDate.of(2026, 3, 4), LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 4)), // duplicate, deliberately
            daysWorkedOverride = 3,
        )

        val sessions = db.workSessions().forEntry(id)
        assertEquals(2, sessions.size) // the duplicate date collapsed to one row
        assertEquals(setOf(LocalDate.of(2026, 3, 4), LocalDate.of(2026, 3, 5)), sessions.map { it.date }.toSet())
        assertEquals(3, db.workEntries().byId(id)!!.daysWorkedOverride)

        val edit = repository.forEdit(id)!!
        assertEquals(setOf(LocalDate.of(2026, 3, 4), LocalDate.of(2026, 3, 5)), edit.sessionDates.toSet())
        assertEquals(3, edit.daysWorkedOverride)
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
            sessionDates = listOf(LocalDate.of(2026, 1, 15)),
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
            sessionDates = listOf(LocalDate.of(2026, 1, 15)),
            completedTaskIds = setOf("T1"),
        )

        val completions = db.taskCompletions().forEntry(id)
        assertEquals(1, completions.size)
        assertEquals("T1", completions.first().taskId)
        assertEquals("2026.1", completions.first().catalogueVersion)
        assertEquals("Task text as seeded", completions.first().taskTextSnapshot)
    }

    @Test
    fun `create stores workorder, airframe reading, annual inspection flag, documentation and parts`() = runBlocking {
        val id = repository.create(
            aircraftId = null,
            description = "Annual inspection",
            activityTypes = setOf(ActivityType.INSPECTION),
            role = EntryRole.CERTIFIED_BY_ME_IN_APP,
            supervisedAnother = false,
            sessionDates = listOf(LocalDate.of(2026, 1, 15)),
            airframeHoursAtWork = 1234.5,
            launchesAtWork = 6789,
            workorderIssuerName = "Piet Bakker",
            workorderDate = LocalDate.of(2026, 1, 10),
            workorderRequestedWork = "Annual inspection per maintenance programme",
            workorderReference = "WO-2026-001",
            annualInspection = true,
            concurrentWithArc = true,
            documentationRefs = listOf(DocumentationRefInput("AMM 12-34", "Rev 5")),
            partsUsed = listOf(
                PartUsedInput("PN-001", description = "Altimeter", batchOrSerial = "SN-9", formOneRef = "F1-77", quantity = "2"),
            ),
        )

        val entry = db.workEntries().byId(id)
        assertNotNull(entry)
        assertEquals(1234.5, entry!!.airframeHoursAtWork)
        assertEquals(6789, entry.launchesAtWork)
        assertEquals("Piet Bakker", entry.workorderIssuerName)
        assertEquals(LocalDate.of(2026, 1, 10), entry.workorderDate)
        assertEquals("Annual inspection per maintenance programme", entry.workorderRequestedWork)
        assertEquals("WO-2026-001", entry.workorderReference)
        assertEquals("WO2026001", entry.workorderReferenceNormalised)
        assertTrue(entry.annualInspection)
        assertTrue(entry.concurrentWithArc)

        val docs = db.documentationRefs().forEntry(id)
        assertEquals(1, docs.size)
        assertEquals("AMM 12-34", docs.first().reference)
        assertEquals("Rev 5", docs.first().revision)

        val parts = db.partsUsed().forEntry(id)
        assertEquals(1, parts.size)
        assertEquals("PN-001", parts.first().partNumber)
        assertEquals("Altimeter", parts.first().description)
        assertEquals("SN-9", parts.first().batchOrSerial)
        assertEquals("F1-77", parts.first().formOneRef)
        assertEquals("2", parts.first().quantity)
    }

    @Test
    fun `create stores photos, and forEdit reassembles them`() = runBlocking {
        val photo = PhotoInput(
            id = "photo-1", localPath = "/data/attachments/photo-1.jpg", sha256 = "abc123",
            capturedAt = Instant.parse("2026-03-14T10:12:00Z"), caption = "Wing spar corrosion", bytes = 12345,
        )

        val id = repository.create(
            aircraftId = null,
            description = "Annual inspection",
            activityTypes = setOf(ActivityType.INSPECTION),
            role = EntryRole.CERTIFIED_BY_ME_IN_APP,
            supervisedAnother = false,
            sessionDates = listOf(LocalDate.of(2026, 3, 14)),
            photos = listOf(photo),
        )

        val stored = db.attachments().forEntry(id)
        assertEquals(1, stored.size)
        assertEquals("PHOTO", stored.first().kind)
        assertEquals("Wing spar corrosion", stored.first().caption)

        val edit = repository.forEdit(id)!!
        assertEquals(listOf(photo), edit.photos)
    }

    @Test
    fun `update replaces photos, same as documentation and parts`() = runBlocking {
        val original = PhotoInput(id = "p1", localPath = "/data/p1.jpg", sha256 = "hash1", capturedAt = Instant.now(), bytes = 100)
        val id = repository.create(
            aircraftId = null, description = "Bench work", activityTypes = setOf(ActivityType.SERVICING),
            role = EntryRole.NO_RELEASE, supervisedAnother = false, sessionDates = listOf(LocalDate.of(2026, 1, 15)),
            photos = listOf(original),
        )

        val replacement = PhotoInput(id = "p2", localPath = "/data/p2.jpg", sha256 = "hash2", capturedAt = Instant.now(), bytes = 200)
        repository.update(
            id = id, aircraftId = null, description = "Bench work", activityTypes = setOf(ActivityType.SERVICING),
            role = EntryRole.NO_RELEASE, supervisedAnother = false, sessionDates = listOf(LocalDate.of(2026, 1, 15)),
            photos = listOf(replacement),
        )

        val stored = db.attachments().forEntry(id)
        assertEquals(1, stored.size)
        assertEquals("p2", stored.first().id)
    }

    @Test
    fun `create resolves the workorder issuer name into the person directory, same as helpers`() = runBlocking {
        val id = repository.create(
            aircraftId = null,
            description = "Bench work",
            activityTypes = setOf(ActivityType.SERVICING),
            role = EntryRole.CERTIFIED_BY_ME_IN_APP,
            supervisedAnother = false,
            sessionDates = listOf(LocalDate.of(2026, 1, 15)),
            workorderIssuerName = "Piet Bakker",
        )

        val entry = db.workEntries().byId(id)
        assertEquals("Piet Bakker", entry!!.workorderIssuerName) // stored as plain text, not a person id

        val people = db.people().all().first()
        assertEquals(1, people.size)
        assertEquals("Piet Bakker", people.first().name) // but the name is now in the directory for reuse
    }

    @Test
    fun `forEdit reassembles everything create wrote, including child rows`() = runBlocking {
        val id = repository.create(
            aircraftId = null,
            description = "Annual inspection",
            activityTypes = setOf(ActivityType.INSPECTION, ActivityType.SERVICING),
            role = EntryRole.CERTIFIED_BY_ME_IN_APP,
            supervisedAnother = true,
            sessionDates = listOf(LocalDate.of(2026, 1, 15)),
            helperNames = listOf("Jan de Vries"),
            workorderIssuerName = "Piet Bakker",
            workorderReference = "WO-2026-001",
            annualInspection = true,
            concurrentWithArc = true,
            documentationRefs = listOf(DocumentationRefInput("AMM 12-34", "Rev 5")),
            partsUsed = listOf(PartUsedInput("PN-001", batchOrSerial = "SN-9")),
        )

        val edit = repository.forEdit(id)!!
        assertEquals("Annual inspection", edit.description)
        assertEquals(setOf(ActivityType.INSPECTION, ActivityType.SERVICING), edit.activityTypes)
        assertTrue(edit.supervisedAnother)
        assertEquals(listOf(LocalDate.of(2026, 1, 15)), edit.sessionDates)
        assertEquals(listOf("Jan de Vries"), edit.helperNames)
        assertEquals("Piet Bakker", edit.workorderIssuerName)
        assertEquals("WO-2026-001", edit.workorderReference)
        assertTrue(edit.annualInspection)
        assertTrue(edit.concurrentWithArc)
        assertEquals(listOf(DocumentationRefInput("AMM 12-34", "Rev 5")), edit.documentationRefs)
        assertEquals(listOf(PartUsedInput("PN-001", batchOrSerial = "SN-9")), edit.partsUsed)
    }

    @Test
    fun `forEdit returns null for an unknown id`() = runBlocking {
        assertEquals(null, repository.forEdit("does-not-exist"))
    }

    @Test
    fun `update replaces the entry's fields, session date, and every child row`() = runBlocking {
        val id = repository.create(
            aircraftId = null,
            description = "Bench work",
            activityTypes = setOf(ActivityType.SERVICING),
            role = EntryRole.NO_RELEASE,
            supervisedAnother = false,
            sessionDates = listOf(LocalDate.of(2026, 1, 15)),
            helperNames = listOf("Jan de Vries"),
            documentationRefs = listOf(DocumentationRefInput("Old doc")),
            partsUsed = listOf(PartUsedInput("OLD-PN")),
        )

        repository.update(
            id = id,
            aircraftId = null,
            description = "Bench work, corrected",
            activityTypes = setOf(ActivityType.REPAIRING),
            role = EntryRole.CERTIFIED_BY_ME_IN_APP,
            supervisedAnother = false,
            sessionDates = listOf(LocalDate.of(2026, 2, 1)),
            helperNames = emptyList(),
            documentationRefs = listOf(DocumentationRefInput("New doc")),
            partsUsed = listOf(PartUsedInput("NEW-PN")),
        )

        val entry = db.workEntries().byId(id)!!
        assertEquals("Bench work, corrected", entry.description)
        assertEquals(EntryRole.CERTIFIED_BY_ME_IN_APP, entry.role)

        val sessions = db.workSessions().forEntry(id)
        assertEquals(1, sessions.size) // replaced, not appended
        assertEquals(LocalDate.of(2026, 2, 1), sessions.first().date)

        assertEquals(setOf(ActivityType.REPAIRING), db.workEntries().activityTypesForEntry(id).map { it.activityType }.toSet())
        assertTrue(db.entryHelpers().forEntry(id).isEmpty())

        val docs = db.documentationRefs().forEntry(id)
        assertEquals(1, docs.size)
        assertEquals("New doc", docs.first().reference)

        val parts = db.partsUsed().forEntry(id)
        assertEquals(1, parts.size)
        assertEquals("NEW-PN", parts.first().partNumber)
    }

    @Test
    fun `update is a no-op for an unknown id`() = runBlocking {
        repository.update(
            id = "does-not-exist",
            aircraftId = null,
            description = "Should not be stored",
            activityTypes = setOf(ActivityType.SERVICING),
            role = EntryRole.NO_RELEASE,
            supervisedAnother = false,
            sessionDates = listOf(LocalDate.of(2026, 1, 1)),
        )

        assertEquals(null, db.workEntries().byId("does-not-exist"))
    }

    @Test
    fun `delete removes the entry and cascades to its child rows`() = runBlocking {
        val id = repository.create(
            aircraftId = null,
            description = "Bench work",
            activityTypes = setOf(ActivityType.SERVICING),
            role = EntryRole.NO_RELEASE,
            supervisedAnother = false,
            sessionDates = listOf(LocalDate.of(2026, 1, 15)),
            helperNames = listOf("Jan de Vries"),
        )

        repository.delete(id)

        assertEquals(null, db.workEntries().byId(id))
        assertTrue(db.workSessions().forEntry(id).isEmpty())
        assertTrue(db.entryHelpers().forEntry(id).isEmpty())
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
            role = EntryRole.NO_RELEASE, supervisedAnother = false, sessionDates = listOf(LocalDate.of(2026, 1, 1)),
        )
        repository.create(
            aircraftId = null, description = "Newer bench entry", activityTypes = setOf(ActivityType.SERVICING),
            role = EntryRole.NO_RELEASE, supervisedAnother = false, sessionDates = listOf(LocalDate.of(2026, 6, 1)),
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
