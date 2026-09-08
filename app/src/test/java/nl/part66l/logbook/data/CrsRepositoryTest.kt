package nl.part66l.logbook.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import nl.part66l.logbook.domain.ActivityType
import nl.part66l.logbook.domain.DocumentCategory
import nl.part66l.logbook.domain.EntryRole
import nl.part66l.logbook.domain.Propulsion
import nl.part66l.logbook.domain.SignatureState
import nl.part66l.logbook.domain.Structure
import nl.part66l.logbook.fakes.FakeSettingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrsRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: CrsRepository
    private lateinit var workEntryRepository: WorkEntryRepository
    private lateinit var settingsRepository: FakeSettingsRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsRepository = FakeSettingsRepository()
        repository = CrsRepositoryImpl(
            context = ApplicationProvider.getApplicationContext(),
            workEntryDao = db.workEntries(),
            workSessionDao = db.workSessions(),
            aircraftDao = db.aircraft(),
            profileDao = db.profile(),
            entryHelperDao = db.entryHelpers(),
            personDao = db.people(),
            documentationRefDao = db.documentationRefs(),
            partUsedDao = db.partsUsed(),
            taskCompletionDao = db.taskCompletions(),
            crsDao = db.crs(),
            settingsRepository = settingsRepository,
        )
        workEntryRepository = WorkEntryRepositoryImpl(
            db.workEntries(), db.workSessions(), db.entryHelpers(), PersonRepositoryImpl(db.people()), db.people(),
            db.catalogue(), db.taskCompletions(), db.documentationRefs(), db.partsUsed(),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedCatalogueTask(): String {
        val task = CatalogueTaskEntity(
            id = "task-1", catalogueVersion = "2026.1", table = "B", section = "General", sectionCode = "GEN",
            text = "Weighing, weight & balance sheet", reference = "AMC1 M.A.803", appliesToL1 = true,
            appliesToL1C = true, appliesToL2 = true, appliesToL2C = true,
        )
        db.catalogue().upsertAll(listOf(task))
        return task.id
    }

    private suspend fun createEntry(withAircraft: Boolean = true, completedTaskIds: Set<String> = emptySet()): String {
        db.profile().upsert(
            ProfileEntity(
                name = "F. Example", licenceNumber = "NL.66.00000", issuingAuthority = "ILT", licenceExpiry = null, holdsL1 = true,
            ),
        )
        val aircraftId = if (withAircraft) {
            AircraftRepositoryImpl(db.aircraft()).create(
                manufacturer = "Schleicher", type = "ASK 21", serialNumber = "21123",
                propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
                subcategoryOverride = null, registration = "PH-1234", validFrom = LocalDate.of(2020, 1, 1),
            )
        } else {
            null
        }
        return workEntryRepository.create(
            aircraftId = aircraftId,
            description = "Annual inspection in accordance with the approved maintenance programme.",
            activityTypes = setOf(ActivityType.INSPECTION),
            role = EntryRole.CERTIFIED_BY_ME_IN_APP,
            supervisedAnother = true,
            sessionDate = LocalDate.of(2026, 3, 14),
            helperNames = listOf("J. de Vries"),
            completedTaskIds = completedTaskIds,
            documentationRefs = listOf(
                DocumentationRefInput("AMM", "Rev 5", LocalDate.of(2025, 6, 1), DocumentCategory.MANUAL),
            ),
            partsUsed = listOf(
                PartUsedInput("6204-2RS", description = "Wheel bearing", batchOrSerial = "B-77412", formOneRef = "55120"),
            ),
        )
    }

    @Test
    fun `generateUnsigned allocates a REG-based number, renders a PDF on disk, and records the CRS`() = runBlocking {
        val taskId = seedCatalogueTask()
        val entryId = createEntry(completedTaskIds = setOf(taskId))

        val crs = repository.generateUnsigned(entryId, limitations = "None.", maintenanceIncomplete = false)

        assertNotNull(crs)
        assertEquals("PH1234-2026-0001", crs!!.number)
        assertEquals(1, crs.sequence)
        assertEquals(2026, crs.year)
        assertEquals(entryId, crs.entryId)
        assertEquals(LocalDate.of(2026, 3, 14), crs.completionDate)
        assertEquals(SignatureState.ISSUED_UNSIGNED_PRINT, crs.signatureState)
        assertEquals("None.", crs.limitations)
        assertTrue(crs.snapshotJson.contains("AMM"))
        assertTrue(crs.snapshotJson.contains("Wheel bearing"))

        val pdfFile = File(crs.pdfLocalPath!!)
        assertTrue("expected the rendered PDF to exist on disk", pdfFile.exists())
        assertTrue(pdfFile.length() > 0)
        assertNotNull(crs.pdfSha256)

        val text = PDDocument.load(pdfFile).use { PDFTextStripper().getText(it) }
        assertTrue("expected the parts table to include the part actually recorded on the entry", text.contains("6204-2RS"))
        assertTrue(text.contains("Wheel bearing"))
        assertTrue("manufacturer and type should be split into separate fields", text.contains("MANUFACTURER") && text.contains("TYPE"))
        assertTrue(text.contains("Schleicher"))
        assertTrue(text.contains("ASK 21"))
        assertTrue(text.contains("Assisted / On the job training"))
        assertTrue("the licence holder should be listed among the personnel", text.contains("F. Example") && text.contains("Certifying staff"))
        assertTrue(text.contains("ACTIVITIES AND TASKS"))
        assertTrue(text.contains("Inspection"))
        assertTrue(text.contains("Weighing, weight & balance sheet"))
    }

    @Test
    fun `a ticked maintenance-incomplete checkbox actually shows up in the limitations block`() = runBlocking {
        val entryId = createEntry()

        val tickedOnly = repository.generateUnsigned(entryId, limitations = null, maintenanceIncomplete = true)
        val tickedWithText = repository.generateUnsigned(entryId, limitations = "Transponder recal outstanding.", maintenanceIncomplete = true)
        val unticked = repository.generateUnsigned(entryId, limitations = null, maintenanceIncomplete = false)

        val tickedOnlyText = PDDocument.load(File(tickedOnly!!.pdfLocalPath!!)).use { PDFTextStripper().getText(it) }
        assertTrue(tickedOnlyText.contains("Maintenance could not be completed in full."))

        val tickedWithTextText = PDDocument.load(File(tickedWithText!!.pdfLocalPath!!)).use { PDFTextStripper().getText(it) }
        assertTrue(tickedWithTextText.contains("Maintenance could not be completed in full."))
        assertTrue(tickedWithTextText.contains("Transponder recal outstanding."))

        val unticketText = PDDocument.load(File(unticked!!.pdfLocalPath!!)).use { PDFTextStripper().getText(it) }
        assertTrue(!unticketText.contains("Maintenance could not be completed in full."))
    }

    @Test
    fun `generateUnsigned uses NOREG for bench or component work`() = runBlocking {
        val entryId = createEntry(withAircraft = false)

        val crs = repository.generateUnsigned(entryId, limitations = null, maintenanceIncomplete = false)

        assertEquals("NOREG-2026-0001", crs!!.number)
    }

    @Test
    fun `generateUnsigned returns null for an unknown entry`() = runBlocking {
        assertNull(repository.generateUnsigned("does-not-exist", limitations = null, maintenanceIncomplete = false))
    }

    @Test
    fun `a second generation for the same entry is a revision of the same base number, not a new one`() = runBlocking {
        val entryId = createEntry()

        val first = repository.generateUnsigned(entryId, limitations = null, maintenanceIncomplete = false)
        val second = repository.generateUnsigned(entryId, limitations = null, maintenanceIncomplete = false)
        val third = repository.generateUnsigned(entryId, limitations = null, maintenanceIncomplete = false)

        assertEquals("PH1234-2026-0001", first!!.number)
        assertEquals(0, first.revision)
        assertEquals("PH1234-2026-0001", first.baseNumber)
        assertEquals(null, first.supersedesCrsId)

        assertEquals("PH1234-2026-0001-rev1", second!!.number)
        assertEquals(1, second.revision)
        assertEquals("PH1234-2026-0001", second.baseNumber)
        assertEquals(first.id, second.supersedesCrsId)
        // A revision reuses the base's own sequence rather than allocating a fresh one.
        assertEquals(first.sequence, second.sequence)

        assertEquals("PH1234-2026-0001-rev2", third!!.number)
        assertEquals(2, third.revision)
        assertEquals(second.id, third.supersedesCrsId)
    }

    @Test
    fun `a revision for one entry does not consume a sequence number needed by another entry`() = runBlocking {
        val firstEntryId = createEntry()
        repository.generateUnsigned(firstEntryId, limitations = null, maintenanceIncomplete = false) // PH1234-2026-0001
        repository.generateUnsigned(firstEntryId, limitations = null, maintenanceIncomplete = false) // PH1234-2026-0001-rev1

        val secondEntryId = createEntry()
        val secondCrs = repository.generateUnsigned(secondEntryId, limitations = null, maintenanceIncomplete = false)

        assertEquals("PH1234-2026-0002", secondCrs!!.number)
    }

    @Test
    fun `forEntry reflects generated certificates, newest revision first`() = runBlocking {
        val entryId = createEntry()
        repository.generateUnsigned(entryId, limitations = null, maintenanceIncomplete = false)
        repository.generateUnsigned(entryId, limitations = null, maintenanceIncomplete = false)

        val issued = repository.forEntry(entryId).first()

        assertEquals(listOf("PH1234-2026-0001-rev1", "PH1234-2026-0001"), issued.map { it.number })
    }
}
