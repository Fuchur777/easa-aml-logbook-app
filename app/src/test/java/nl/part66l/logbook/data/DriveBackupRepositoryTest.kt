package nl.part66l.logbook.data

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import nl.part66l.logbook.domain.CertificationBasis
import nl.part66l.logbook.domain.SignatureState
import nl.part66l.logbook.fakes.FakeDriveApiClient
import nl.part66l.logbook.fakes.FakeDriveAuthManager
import nl.part66l.logbook.fakes.FakeSettingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Must match [DriveBackupRepositoryImpl]'s own hardcoded database filename. */
private const val DATABASE_FILE_NAME = "part66log.db"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DriveBackupRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: DriveBackupRepositoryImpl
    private lateinit var apiClient: FakeDriveApiClient
    private lateinit var authManager: FakeDriveAuthManager
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var activity: FragmentActivity

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearLocalState()
        db = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_FILE_NAME).allowMainThreadQueries().build()
        apiClient = FakeDriveApiClient()
        authManager = FakeDriveAuthManager()
        settingsRepository = FakeSettingsRepository()
        repository = DriveBackupRepositoryImpl(context, db, authManager, apiClient, settingsRepository)
        activity = Robolectric.buildActivity(FragmentActivity::class.java).create().get()
    }

    @After
    fun tearDown() {
        try {
            db.close()
        } catch (e: Exception) {
            // Already closed by a restore test — fine.
        }
        clearLocalState()
    }

    private fun clearLocalState() {
        context.getDatabasePath(DATABASE_FILE_NAME).delete()
        File(context.getDatabasePath(DATABASE_FILE_NAME).path + "-wal").delete()
        File(context.getDatabasePath(DATABASE_FILE_NAME).path + "-shm").delete()
        for (folder in listOf("crs", "attachments", "documents")) File(context.filesDir, folder).deleteRecursively()
    }

    private suspend fun seedOneCrs(pdfBytes: ByteArray = byteArrayOf(1, 2, 3)): CrsEntity {
        val entry = WorkEntryEntity(
            id = "entry-${UUID.randomUUID()}",
            aircraftId = null,
            description = "Test work",
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
        db.workEntries().insert(entry)
        val crsDir = File(context.filesDir, "crs").apply { mkdirs() }
        val pdfFile = File(crsDir, "${UUID.randomUUID()}.pdf").apply { writeBytes(pdfBytes) }
        val crs = CrsEntity(
            id = "crs-${UUID.randomUUID()}",
            entryId = entry.id,
            number = "PH1234-2026-0001",
            numberNormalised = "x",
            sequence = 1,
            year = 2026,
            basis = CertificationBasis.ML_A_801_B2_INDEPENDENT,
            statementVersion = "1",
            completionDate = LocalDate.of(2026, 3, 14),
            signatureState = SignatureState.SIGNED_LOCAL,
            snapshotJson = "{}",
            pdfLocalPath = pdfFile.absolutePath,
        )
        db.crs().insert(crs)
        return crs
    }

    @Test
    fun `createBackup zips the database and content folders and uploads it`() = runBlocking {
        seedOneCrs()

        val result = repository.createBackup(activity)

        assertTrue(result.isSuccess)
        val uploaded = apiClient.uploadedFiles.single()
        assertTrue(uploaded.name.startsWith("backup-") && uploaded.name.endsWith(".zip"))
        val backupsFolder = apiClient.createdFolders.single { it.name == "Backups" }
        assertEquals(backupsFolder.id, uploaded.parentId)

        val entryNames = mutableListOf<String>()
        ZipInputStream(uploaded.bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entryNames += entry.name
                entry = zip.nextEntry
            }
        }
        assertTrue(entryNames.any { it == "db/$DATABASE_FILE_NAME" })
        assertTrue(entryNames.any { it.startsWith("crs/") && it.endsWith(".pdf") })
    }

    @Test
    fun `createBackup also writes the appdata manifest so a fresh install can find the Backups folder`() = runBlocking {
        repository.createBackup(activity).getOrThrow()

        assertTrue(apiClient.appDataFiles.any { it.name == "manifest.json" })
    }

    @Test
    fun `listBackups uses the locally cached folder id when present`() = runBlocking {
        repository.createBackup(activity).getOrThrow()
        repository.createBackup(activity).getOrThrow()

        val backups = repository.listBackups(activity).getOrThrow()

        assertEquals(2, backups.size)
        assertTrue(backups.all { it.name.endsWith(".zip") })
    }

    @Test
    fun `listBackups finds the Backups folder via the appdata manifest on a fresh install`() = runBlocking {
        repository.createBackup(activity).getOrThrow()
        // Simulate a fresh install: nothing cached locally, even though Drive itself still has everything.
        val freshSettings = FakeSettingsRepository()
        val freshRepository = DriveBackupRepositoryImpl(context, db, authManager, apiClient, freshSettings)

        val backups = freshRepository.listBackups(activity).getOrThrow()

        assertEquals(1, backups.size)
        assertNotNull(freshSettings.driveBackupsFolderId.first())
    }

    // `restoreBackup` itself has no test here on purpose. It swaps out part66log.db while this
    // process still holds a Room instance on it — fine on Android (Linux lets you replace an open
    // file), but on Windows the OS keeps the handle locked well past appDatabase.close(), so the
    // move fails with a sharing violation no matter how long you wait for it. That's a property of
    // the machine running the tests, not of the code under test, and no amount of production-side
    // retrying changes it. Restore is verified on-device instead (see the manual checklist:
    // restore onto the same device, and onto a fresh install).
}
