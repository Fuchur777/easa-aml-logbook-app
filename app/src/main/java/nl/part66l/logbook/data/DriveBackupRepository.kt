package nl.part66l.logbook.data

import android.content.Context
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nl.part66l.logbook.drive.DriveApiClient
import nl.part66l.logbook.drive.DriveAuthManager
import nl.part66l.logbook.drive.DriveManifest

/** One backup listed in Drive's "Backups" folder. */
data class DriveBackupEntry(val fileId: String, val name: String, val createdAt: Instant)

interface DriveBackupRepository {
    /** Zips the local database file plus every synced-content folder and uploads it to Drive's "Backups" folder, unencrypted — Drive's own account access is the protection (§11). */
    suspend fun createBackup(activity: FragmentActivity): Result<Unit>

    /** Lists available backups, newest first — hydrating local folder-ID settings from the `drive.appdata` manifest first if this is a fresh install with nothing cached locally. */
    suspend fun listBackups(activity: FragmentActivity): Result<List<DriveBackupEntry>>

    /**
     * Downloads [backup] and replaces every local database/content file with its contents. The
     * caller must close and reopen the app afterwards — Room can't safely reopen a database file
     * that was swapped out from under an already-running process.
     */
    suspend fun restoreBackup(activity: FragmentActivity, backup: DriveBackupEntry): Result<Unit>
}

private val BACKUP_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

/**
 * Full-device backup/restore (§11) — a raw copy of the Room database file plus the folders it
 * references (`crs/`, `attachments/`, `documents/`), zipped and uploaded as-is. Deliberately not
 * a hand-rolled JSON export of every entity: restoring is "swap the files back and let Room's own
 * migrations run," which needs no bespoke entity-graph reconstruction.
 */
@Singleton
class DriveBackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDatabase: AppDatabase,
    private val driveAuthManager: DriveAuthManager,
    private val driveApiClient: DriveApiClient,
    private val settingsRepository: SettingsRepository,
) : DriveBackupRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun createBackup(activity: FragmentActivity): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = driveAuthManager.authorize(activity).getOrThrow()
            checkpointDatabase()
            val zipFile = buildBackupZip()
            try {
                val rootFolderId = ensureRootFolder(token).getOrThrow()
                val backupsFolderId = ensureBackupsFolder(token, rootFolderId).getOrThrow()
                val name = "backup-${BACKUP_NAME_FORMAT.format(Instant.now())}.zip"
                driveApiClient.uploadLargeFile(token, name, backupsFolderId, zipFile, "application/zip").getOrThrow()
                // Writes/refreshes the manifest even for a user who only ever creates backups and
                // never runs the file-mirror sync — otherwise a fresh install with nothing cached
                // locally would have no way to find this Backups folder at all (see resolveBackupsFolderId).
                writeManifest(token, rootFolderId, backupsFolderId)
            } finally {
                zipFile.delete()
            }
        }
    }

    override suspend fun listBackups(activity: FragmentActivity): Result<List<DriveBackupEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val token = driveAuthManager.authorize(activity).getOrThrow()
            val backupsFolderId = resolveBackupsFolderId(token)
            driveApiClient.listFiles(token, parentId = backupsFolderId).getOrThrow()
                .map { DriveBackupEntry(it.id, it.name, it.createdTime) }
        }
    }

    override suspend fun restoreBackup(activity: FragmentActivity, backup: DriveBackupEntry): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = driveAuthManager.authorize(activity).getOrThrow()
            val tempZip = File.createTempFile("restore-", ".zip", context.cacheDir)
            try {
                driveApiClient.downloadFile(token, backup.fileId, tempZip).getOrThrow()
                // Closing here means any other screen still observing a Room Flow can throw once
                // this call returns — acceptable only because the caller immediately replaces the
                // UI with a static "restore complete, please restart" screen (see DriveRestoreScreen).
                appDatabase.close()
                replaceLocalContentWith(tempZip)
            } finally {
                tempZip.delete()
            }
        }
    }

    private fun checkpointDatabase() {
        // Merges the WAL back into the main file so part66log.db alone is complete and
        // consistent — otherwise a backup could miss recently-written rows still sitting in -wal.
        // PRAGMA wal_checkpoint returns a result row, so — unlike a plain DDL/DML statement —
        // it has to go through query()/rawQuery(), not execSQL() (which rejects anything that
        // returns a cursor).
        appDatabase.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
    }

    private fun buildBackupZip(): File {
        val destDir = File(context.filesDir, "backup-export").apply {
            deleteRecursively()
            mkdirs()
        }
        val zipFile = File(destDir, "backup.zip")
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            val dbFile = context.getDatabasePath(DATABASE_FILE_NAME)
            if (dbFile.exists()) zip.addFile(dbFile, "db/${dbFile.name}")
            for (folder in CONTENT_FOLDER_NAMES) {
                val dir = File(context.filesDir, folder)
                if (dir.exists()) zip.addDirectory(dir, "$folder/")
            }
        }
        return zipFile
    }

    private fun ZipOutputStream.addFile(source: File, entryName: String) {
        putNextEntry(ZipEntry(entryName))
        source.inputStream().use { it.copyTo(this) }
        closeEntry()
    }

    private fun ZipOutputStream.addDirectory(dir: File, prefix: String) {
        dir.walkTopDown().filter { it.isFile }.forEach { file ->
            addFile(file, prefix + file.relativeTo(dir).path.replace(File.separatorChar, '/'))
        }
    }

    private suspend fun ensureRootFolder(token: String): Result<String> {
        settingsRepository.driveRootFolderId.first()?.let { return Result.success(it) }
        return driveApiClient.createFolder(token, "AMLog", parentId = "root")
            .onSuccess { settingsRepository.setDriveRootFolderId(it) }
    }

    private suspend fun ensureBackupsFolder(token: String, rootFolderId: String): Result<String> {
        settingsRepository.driveBackupsFolderId.first()?.let { return Result.success(it) }
        return driveApiClient.createFolder(token, "Backups", rootFolderId)
            .onSuccess { settingsRepository.setDriveBackupsFolderId(it) }
    }

    /**
     * Same manifest [DriveSyncRepositoryImpl] writes (see its own doc comment) — refreshed here
     * too so a backup-only user (who never runs "Sync now") still leaves a manifest a fresh
     * install can read. `aircraftFolderIds` is left empty: this repository doesn't track those,
     * and a later regular sync's own manifest write fills it back in.
     */
    private suspend fun writeManifest(token: String, rootFolderId: String, backupsFolderId: String) {
        val manifest = DriveManifest(
            rootFolderId = rootFolderId,
            benchFolderId = settingsRepository.driveBenchFolderId.first(),
            backupsFolderId = backupsFolderId,
            aircraftFolderIds = emptyMap(),
            updatedAt = Instant.now().toEpochMilli(),
        )
        driveApiClient.uploadAppDataFile(token, "manifest.json", json.encodeToString(manifest).toByteArray()).getOrThrow()
    }

    /**
     * Fresh install with nothing cached locally: read the manifest the file-mirror sync already
     * writes to `drive.appdata` and hydrate settings from it. `drive.file` scope can't search
     * Drive by name — only list children of a folder ID it already knows — so without this, a
     * reinstall would have no way to find its own Backups folder at all.
     */
    private suspend fun resolveBackupsFolderId(token: String): String {
        settingsRepository.driveBackupsFolderId.first()?.let { return it }
        val manifestFile = driveApiClient.listFiles(token, spaces = "appDataFolder", nameEquals = "manifest.json").getOrThrow().firstOrNull()
            ?: throw IllegalStateException("No backups found for this Google account.")
        val manifestBytes = driveApiClient.downloadBytes(token, manifestFile.id).getOrThrow()
        val manifest = json.decodeFromString<DriveManifest>(String(manifestBytes))
        settingsRepository.setDriveRootFolderId(manifest.rootFolderId)
        settingsRepository.setDriveBenchFolderId(manifest.benchFolderId)
        val backupsFolderId = manifest.backupsFolderId
            ?: throw IllegalStateException("No backup has ever been created for this Google account.")
        settingsRepository.setDriveBackupsFolderId(backupsFolderId)
        return backupsFolderId
    }

    private fun replaceLocalContentWith(zipFile: File) {
        val dbFile = context.getDatabasePath(DATABASE_FILE_NAME)
        // The backup zip has no -wal/-shm entries (createBackup checkpoints before zipping, so
        // part66log.db alone is already complete) — nothing below re-touches these two paths, so
        // any leftover WAL content that fails to delete would otherwise survive untouched and get
        // replayed by SQLite the next time it opens this file, silently resurrecting whatever was
        // written locally after the backup was taken. delete() can silently fail (most commonly
        // on Windows, if any handle on the file hasn't fully released yet) — truncating in that
        // case is enough to make the leftover content inert even when the file can't be unlinked.
        clearFile(File(dbFile.path + "-wal"))
        clearFile(File(dbFile.path + "-shm"))
        for (folder in CONTENT_FOLDER_NAMES) File(context.filesDir, folder).deleteRecursively()

        ZipInputStream(zipFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    if (entry.name.startsWith("db/")) {
                        // Written to a temp file and moved into place rather than truncating the
                        // live file: a crash midway through an in-place overwrite would leave a
                        // half-written database where the old one used to be.
                        val temp = File.createTempFile("restored-db-", ".tmp", dbFile.parentFile)
                        temp.outputStream().use { out -> zip.copyTo(out) }
                        java.nio.file.Files.move(
                            temp.toPath(),
                            dbFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        )
                    } else {
                        val destination = File(context.filesDir, entry.name)
                        destination.parentFile?.mkdirs()
                        destination.outputStream().use { out -> zip.copyTo(out) }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    /** Deletes [file] if possible; if the OS refuses (a held handle, most commonly on Windows), truncates it in place instead so no stale content is left readable. */
    private fun clearFile(file: File) {
        if (!file.exists()) return
        if (!file.delete()) {
            runCatching { file.outputStream().close() }
        }
    }

    companion object {
        /** Must match the name `DatabaseModule.provideDatabase` passes to `Room.databaseBuilder`. */
        private const val DATABASE_FILE_NAME = "part66log.db"
        private val CONTENT_FOLDER_NAMES = listOf("crs", "attachments", "documents")
    }
}
