package nl.part66l.logbook.data

import androidx.fragment.app.FragmentActivity
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import nl.part66l.logbook.domain.CertificationBasis
import nl.part66l.logbook.domain.Propulsion
import nl.part66l.logbook.domain.SignatureState
import nl.part66l.logbook.domain.Structure
import nl.part66l.logbook.fakes.FakeDriveApiClient
import nl.part66l.logbook.fakes.FakeDriveAuthManager
import nl.part66l.logbook.fakes.FakeSettingsRepository
import nl.part66l.logbook.drive.DriveManifestStore
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DriveSyncRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: DriveSyncRepositoryImpl
    private lateinit var apiClient: FakeDriveApiClient
    private lateinit var authManager: FakeDriveAuthManager
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var filesDir: File
    private lateinit var activity: FragmentActivity

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        apiClient = FakeDriveApiClient()
        authManager = FakeDriveAuthManager()
        settingsRepository = FakeSettingsRepository()
        repository = DriveSyncRepositoryImpl(
            driveAuthManager = authManager,
            driveApiClient = apiClient,
            workEntryDao = db.workEntries(),
            workSessionDao = db.workSessions(),
            aircraftDao = db.aircraft(),
            crsDao = db.crs(),
            attachmentDao = db.attachments(),
            documentDao = db.documents(),
            settingsRepository = settingsRepository,
            driveManifestStore = DriveManifestStore(apiClient),
        )
        filesDir = java.nio.file.Files.createTempDirectory("drive-sync-test").toFile()
        activity = Robolectric.buildActivity(FragmentActivity::class.java).create().get()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private var nextSerial = 1
    private var nextCrsSerial = 1

    private suspend fun createAircraft(): String {
        val id = "aircraft-${nextSerial}"
        db.aircraft().insert(
            AircraftEntity(
                id = id,
                manufacturer = "Schleicher",
                type = "ASK 21",
                serialNumber = (21100 + nextSerial++).toString(),
                propulsion = Propulsion.UNPOWERED,
                structure = Structure.WOOD_AND_FABRIC,
            ),
        )
        return id
    }

    private suspend fun createEntry(aircraftId: String?, workorderReference: String = "WO-${UUID.randomUUID()}"): String {
        val id = "entry-${UUID.randomUUID()}"
        db.workEntries().insert(
            WorkEntryEntity(
                id = id,
                aircraftId = aircraftId,
                description = "Test work",
                workorderReference = workorderReference,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
        )
        db.workSessions().insert(WorkSessionEntity(id = "session-$id", entryId = id, date = LocalDate.of(2026, 3, 14)))
        return id
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private suspend fun createCrs(entryId: String, driveFileId: String? = null, pdfBytes: ByteArray = byteArrayOf(1, 2, 3)): CrsEntity {
        val pdfFile = File(filesDir, "${UUID.randomUUID()}.pdf").apply { writeBytes(pdfBytes) }
        val crs = CrsEntity(
            id = "crs-${UUID.randomUUID()}",
            entryId = entryId,
            number = "PH1234-2026-%04d".format(nextCrsSerial++),
            numberNormalised = "x",
            sequence = 1,
            year = 2026,
            basis = CertificationBasis.ML_A_801_B2_INDEPENDENT,
            statementVersion = "1",
            completionDate = LocalDate.of(2026, 3, 14),
            signatureState = SignatureState.SIGNED_LOCAL,
            snapshotJson = "{}",
            pdfLocalPath = pdfFile.absolutePath,
            driveFileId = driveFileId,
        )
        db.crs().insert(crs)
        return crs
    }

    private suspend fun createDocument(driveFileId: String? = null, bytes: ByteArray = byteArrayOf(5, 5, 5)): DocumentEntity {
        val pdfFile = File(filesDir, "${UUID.randomUUID()}.pdf").apply { writeBytes(bytes) }
        val document = DocumentEntity(
            id = "doc-${UUID.randomUUID()}",
            name = "AMM Rev 5",
            category = nl.part66l.logbook.domain.DocumentCategory.MANUAL,
            pdfPath = pdfFile.absolutePath,
            pdfFileName = "amm-rev5.pdf",
            driveFileId = driveFileId,
        )
        db.documents().insert(document)
        return document
    }

    private suspend fun createPhotoAttachment(entryId: String, driveFileId: String? = null, bytes: ByteArray = byteArrayOf(9, 9, 9)): AttachmentEntity {
        val photoFile = File(filesDir, "${UUID.randomUUID()}.jpg").apply { writeBytes(bytes) }
        val attachment = AttachmentEntity(
            id = "att-${UUID.randomUUID()}",
            entryId = entryId,
            kind = "PHOTO",
            sha256 = sha256(bytes),
            capturedAt = Instant.now(),
            localPath = photoFile.absolutePath,
            driveFileId = driveFileId,
            bytes = bytes.size.toLong(),
        )
        db.attachments().insert(attachment)
        return attachment
    }

    @Test
    fun `syncNow creates the folder tree once and reuses it on a later run`() = runBlocking {
        val aircraftId = createAircraft()
        val entryId = createEntry(aircraftId)
        val crs = createCrs(entryId)

        val firstSummary = repository.syncNow(activity).getOrThrow()
        assertEquals(1, firstSummary.uploaded)
        assertEquals(0, firstSummary.failed)
        assertEquals(4, apiClient.createdFolders.size) // root, bench, aircraft, entry

        assertNotNull(db.crs().byId(crs.id)!!.driveFileId)
        val foldersAfterFirstSync = apiClient.createdFolders.toList()

        // A second item lands on the same entry/aircraft — root, bench, aircraft and entry folders
        // must all be reused; only the lazily-created "photos" subfolder is genuinely new.
        createPhotoAttachment(entryId)
        val secondSummary = repository.syncNow(activity).getOrThrow()
        assertEquals(1, secondSummary.uploaded)
        assertEquals(foldersAfterFirstSync, apiClient.createdFolders.take(foldersAfterFirstSync.size))
        assertEquals(5, apiClient.createdFolders.size)
        assertEquals("photos", apiClient.createdFolders.last().name)
    }

    @Test
    fun `bench-work entries land in the bench folder, not an aircraft folder`() = runBlocking {
        val entryId = createEntry(aircraftId = null)
        createCrs(entryId)

        repository.syncNow(activity).getOrThrow()

        val benchFolder = apiClient.createdFolders.single { it.name == "Bench — component work" }
        val entryFolder = apiClient.createdFolders.single { it.parentId == benchFolder.id }
        assertNotNull(entryFolder)
        assertTrue(apiClient.createdFolders.none { it.name.startsWith("Schleicher") })
    }

    @Test
    fun `already-uploaded rows are skipped`() = runBlocking {
        val entry1 = createEntry(aircraftId = null)
        val alreadyUploaded = createCrs(entry1, driveFileId = "existing-file-id")
        val entry2 = createEntry(aircraftId = null)
        createCrs(entry2)

        val summary = repository.syncNow(activity).getOrThrow()

        assertEquals(1, summary.uploaded)
        assertEquals("existing-file-id", db.crs().byId(alreadyUploaded.id)!!.driveFileId)
    }

    @Test
    fun `one failing upload does not block the rest`() = runBlocking {
        val entry1 = createEntry(aircraftId = null)
        val crsOk = createCrs(entry1)
        val entry2 = createEntry(aircraftId = null)
        val crsFailing = createCrs(entry2)
        apiClient.uploadFailures["${crsFailing.number}.pdf"] = java.io.IOException("simulated network failure")

        val summary = repository.syncNow(activity).getOrThrow()

        assertEquals(1, summary.uploaded)
        assertEquals(1, summary.failed)
        assertEquals(1, summary.errors.size)
        assertNotNull(db.crs().byId(crsOk.id)!!.driveFileId)
        assertNull(db.crs().byId(crsFailing.id)!!.driveFileId)
    }

    @Test
    fun `a signed CRS's PDF bytes are uploaded exactly as stored`() = runBlocking {
        val entryId = createEntry(aircraftId = null)
        val originalBytes = byteArrayOf(37, 12, 99, 4, 1, 55, -8, 0, 127)
        val crs = createCrs(entryId, pdfBytes = originalBytes)

        repository.syncNow(activity).getOrThrow()

        val uploaded = apiClient.uploadedFiles.single { it.name == "${crs.number}.pdf" }
        assertArrayEquals(originalBytes, uploaded.bytes)
        assertEquals(sha256(originalBytes), sha256(uploaded.bytes))
    }

    @Test
    fun `pending documents upload into a Documents folder and are skipped on the next run`() = runBlocking {
        val alreadyUploaded = createDocument(driveFileId = "existing-file-id")
        val pending = createDocument()

        val firstSummary = repository.syncNow(activity).getOrThrow()

        assertEquals(1, firstSummary.uploaded)
        assertEquals("existing-file-id", db.documents().byId(alreadyUploaded.id)!!.driveFileId)
        val documentsFolder = apiClient.createdFolders.single { it.name == "Documents" }
        val uploaded = apiClient.uploadedFiles.single { it.name == pending.pdfFileName }
        assertEquals(documentsFolder.id, uploaded.parentId)
        assertNotNull(db.documents().byId(pending.id)!!.driveFileId)

        // A second run with nothing new pending must not recreate the Documents folder.
        val foldersAfterFirstSync = apiClient.createdFolders.toList()
        val secondSummary = repository.syncNow(activity).getOrThrow()
        assertEquals(0, secondSummary.uploaded)
        assertEquals(foldersAfterFirstSync, apiClient.createdFolders)
    }
}
