package nl.part66l.logbook.data

import android.content.Context
import androidx.fragment.app.FragmentActivity
import java.io.File
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import nl.part66l.logbook.drive.DriveApiClient
import nl.part66l.logbook.drive.DriveAuthManager
import nl.part66l.logbook.drive.DriveManifest
import nl.part66l.logbook.drive.DriveManifestStore

/** Result of one "Sync now" run — [errors] holds a human-readable line per failed item, so one bad file never hides the rest. */
data class SyncSummary(val uploaded: Int, val failed: Int, val errors: List<String>)

interface DriveSyncRepository {
    /** Authorizes Drive access and stores the connected account's email. */
    suspend fun connect(activity: FragmentActivity): Result<String>

    /**
     * Uploads every CRS PDF, photo and document that doesn't yet have a Drive file ID, creating
     * whatever folders (§10's layout) don't already exist. One failing item is recorded in
     * [SyncSummary.errors] rather than aborting the run — see the class doc on
     * [DriveSyncRepositoryImpl] for the full algorithm.
     */
    suspend fun syncNow(activity: FragmentActivity): Result<SyncSummary>

    /**
     * As [syncNow], but authorizes with a plain [Context] instead of an [FragmentActivity] — for
     * the periodic background worker (§10 auto-sync). Fails cleanly, without showing any UI, if
     * Drive access needs interactive re-consent; the next manual "Sync now" completes it.
     */
    suspend fun syncNowSilently(context: Context): Result<SyncSummary>

    /** Clears the stored account only — per-row Drive IDs are left alone, so reconnecting the same account resumes rather than re-uploading everything. */
    suspend fun disconnect()
}

/**
 * Google Drive backup (§10) — manual "Sync now" plus an optional background auto-sync. The core
 * sync algorithm ([runSync]):
 * 1. Gets a fresh access token (tokens are 1-hour-lived; never cached beyond one run).
 * 2. Ensures the root "AMLog" folder exists.
 * 3. Ensures the single bench/component-work folder exists.
 * 4. For every aircraft an item needs uploaded for, ensures its own folder exists.
 * 5. For every work entry with something pending, ensures its session folder exists.
 * 6. Uploads every CRS PDF that isn't yet on Drive, byte-identical — never re-rendered.
 * 7. Uploads every photo attachment that isn't yet on Drive, into a lazily-created "photos" subfolder.
 * 8. Uploads every document library PDF that isn't yet on Drive, into a lazily-created "Documents" folder.
 * 9. Writes a manifest of every folder ID to the hidden `drive.appdata` space.
 * 10. Returns a [SyncSummary] — a failed item is recorded, not fatal to the rest of the run.
 */
@Singleton
class DriveSyncRepositoryImpl @Inject constructor(
    private val driveAuthManager: DriveAuthManager,
    private val driveApiClient: DriveApiClient,
    private val workEntryDao: WorkEntryDao,
    private val workSessionDao: WorkSessionDao,
    private val aircraftDao: AircraftDao,
    private val crsDao: CrsDao,
    private val attachmentDao: AttachmentDao,
    private val documentDao: DocumentDao,
    private val settingsRepository: SettingsRepository,
    private val driveManifestStore: DriveManifestStore,
) : DriveSyncRepository {

    override suspend fun connect(activity: FragmentActivity): Result<String> {
        val token = driveAuthManager.authorize(activity).getOrElse { return Result.failure(it) }
        val email = driveApiClient.fetchAccountEmail(token).getOrElse { return Result.failure(it) }
        settingsRepository.setConnectedGoogleAccountEmail(email)
        return Result.success(email)
    }

    override suspend fun disconnect() {
        settingsRepository.setConnectedGoogleAccountEmail(null)
    }

    override suspend fun syncNow(activity: FragmentActivity): Result<SyncSummary> {
        val token = driveAuthManager.authorize(activity).getOrElse { return Result.failure(it) }
        return runSync(token)
    }

    /**
     * Refuses to run at all when no account is connected.
     *
     * Cancelling the work on disconnect is the primary fix, but a periodic request enqueued by
     * an older build outlives an app update, so the worker itself has to check. Without this a
     * user who disconnected before updating would keep uploading until they happened to open
     * the Drive screen.
     */
    override suspend fun syncNowSilently(context: Context): Result<SyncSummary> {
        if (settingsRepository.connectedGoogleAccountEmail.first() == null) {
            return Result.failure(IllegalStateException("Google Drive is not connected"))
        }
        val token = driveAuthManager.authorizeSilently(context).getOrElse { return Result.failure(it) }
        return runSync(token)
    }

    private suspend fun runSync(token: String): Result<SyncSummary> {
        val rootFolderId = ensureRootFolder(token).getOrElse { return Result.failure(it) }
        val benchFolderId = ensureBenchFolder(token, rootFolderId).getOrElse { return Result.failure(it) }

        var uploaded = 0
        var failed = 0
        val errors = mutableListOf<String>()

        for (crs in crsDao.pendingDriveUploads()) {
            try {
                val entryFolderId = ensureEntryFolder(token, crs.entryId, rootFolderId, benchFolderId)
                val pdfPath = crs.pdfLocalPath ?: throw IllegalStateException("no PDF on disk")
                val bytes = File(pdfPath).readBytes()
                val fileId = driveApiClient.uploadFile(token, "${crs.number}.pdf", entryFolderId, bytes, "application/pdf").getOrThrow()
                crsDao.setDriveFileId(crs.id, fileId)
                uploaded++
            } catch (e: Exception) {
                failed++
                errors += "CRS ${crs.number}: ${e.message ?: e::class.simpleName}"
            }
        }

        for (attachment in attachmentDao.pendingPhotoUploads()) {
            try {
                val entryFolderId = ensureEntryFolder(token, attachment.entryId, rootFolderId, benchFolderId)
                val photosFolderId = ensurePhotosFolder(token, attachment.entryId, entryFolderId)
                val bytes = File(attachment.localPath).readBytes()
                val fileId = driveApiClient.uploadFile(token, attachment.id, photosFolderId, bytes, "image/jpeg").getOrThrow()
                attachmentDao.setDriveFileId(attachment.id, fileId)
                uploaded++
            } catch (e: Exception) {
                failed++
                errors += "Photo ${attachment.id}: ${e.message ?: e::class.simpleName}"
            }
        }

        for (document in documentDao.pendingDriveUploads()) {
            try {
                val documentsFolderId = ensureDocumentsFolder(token, rootFolderId)
                val pdfPath = document.pdfPath ?: throw IllegalStateException("no PDF on disk")
                val bytes = File(pdfPath).readBytes()
                val name = document.pdfFileName?.ifBlank { null } ?: "${document.name}.pdf"
                val fileId = driveApiClient.uploadFile(token, name, documentsFolderId, bytes, "application/pdf").getOrThrow()
                documentDao.setDriveFileId(document.id, fileId)
                uploaded++
            } catch (e: Exception) {
                failed++
                errors += "Document ${document.name}: ${e.message ?: e::class.simpleName}"
            }
        }

        try {
            writeManifest(token, rootFolderId, benchFolderId)
        } catch (e: Exception) {
            errors += "Manifest: ${e.message ?: e::class.simpleName}"
        }

        settingsRepository.setLastDriveSyncAt(Instant.now())
        return Result.success(SyncSummary(uploaded, failed, errors))
    }

    private suspend fun ensureRootFolder(token: String): Result<String> {
        settingsRepository.driveRootFolderId.first()?.let { return Result.success(it) }
        return driveApiClient.createFolder(token, "AMLog", parentId = "root")
            .onSuccess { settingsRepository.setDriveRootFolderId(it) }
    }

    private suspend fun ensureBenchFolder(token: String, rootFolderId: String): Result<String> {
        settingsRepository.driveBenchFolderId.first()?.let { return Result.success(it) }
        return driveApiClient.createFolder(token, "Bench — component work", rootFolderId)
            .onSuccess { settingsRepository.setDriveBenchFolderId(it) }
    }

    /** Documents aren't aircraft-specific, so unlike aircraft/entry folders this is a single flat folder — created lazily, only once a document actually needs uploading. */
    private suspend fun ensureDocumentsFolder(token: String, rootFolderId: String): String {
        settingsRepository.driveDocumentsFolderId.first()?.let { return it }
        val id = driveApiClient.createFolder(token, "Documents", rootFolderId).getOrThrow()
        settingsRepository.setDriveDocumentsFolderId(id)
        return id
    }

    /** Aircraft's own top-level folder — created once, reused on every later sync. Null [aircraftId] (bench/component work) always resolves to nothing here; the caller falls back to the bench folder. */
    private suspend fun ensureAircraftFolder(token: String, aircraftId: String, rootFolderId: String): String {
        val aircraft = requireNotNull(aircraftDao.byId(aircraftId)) { "aircraft $aircraftId no longer exists" }
        aircraft.driveFolderId?.let { return it }
        val registration = aircraftDao.currentRegistrationRow(aircraftId)?.registration
        val name = listOfNotNull(registration, "${aircraft.manufacturer} ${aircraft.type}", "s.n. ${aircraft.serialNumber}").joinToString(" — ")
        val id = driveApiClient.createFolder(token, name, rootFolderId).getOrThrow()
        aircraftDao.setDriveFolderId(aircraftId, id)
        return id
    }

    private suspend fun ensureEntryFolder(token: String, entryId: String, rootFolderId: String, benchFolderId: String): String {
        val entry = requireNotNull(workEntryDao.byId(entryId)) { "work entry $entryId no longer exists" }
        entry.driveFolderId?.let { return it }
        val parentFolderId = entry.aircraftId?.let { ensureAircraftFolder(token, it, rootFolderId) } ?: benchFolderId
        val sessionDate = workSessionDao.forEntry(entryId).maxOfOrNull { it.date } ?: LocalDate.now()
        val activityTypes = workEntryDao.activityTypesForEntry(entryId).map { it.activityType.name }
        val name = listOfNotNull(
            sessionDate.toString(),
            entry.workorderReference ?: entry.id,
            activityTypes.joinToString("+").ifBlank { null },
        ).joinToString(" ")
        val id = driveApiClient.createFolder(token, name, parentFolderId).getOrThrow()
        workEntryDao.setDriveFolderId(entryId, id)
        return id
    }

    private suspend fun ensurePhotosFolder(token: String, entryId: String, entryFolderId: String): String {
        val entry = requireNotNull(workEntryDao.byId(entryId)) { "work entry $entryId no longer exists" }
        entry.drivePhotosFolderId?.let { return it }
        val id = driveApiClient.createFolder(token, "photos", entryFolderId).getOrThrow()
        workEntryDao.setDrivePhotosFolderId(entryId, id)
        return id
    }

    /**
     * Records the folders this repository owns — root, bench and per-aircraft — carrying forward
     * whatever the backup path wrote for its own. See [DriveManifestStore].
     */
    private suspend fun writeManifest(token: String, rootFolderId: String, benchFolderId: String) {
        val aircraftFolderIds = aircraftDao.observeAllWithRegistration(includeArchived = true).first()
            .mapNotNull { row -> row.aircraft.driveFolderId?.let { row.aircraft.id to it } }
            .toMap()
        driveManifestStore.update(token) { existing ->
            DriveManifest(
                rootFolderId = rootFolderId,
                benchFolderId = benchFolderId,
                // Not ours to set. A device that has never taken a backup has no local value, and
                // writing that null would hide every backup the account already holds.
                backupsFolderId = settingsRepository.driveBackupsFolderId.first() ?: existing?.backupsFolderId,
                aircraftFolderIds = aircraftFolderIds,
                updatedAt = Instant.now().toEpochMilli(),
            )
        }
    }
}
