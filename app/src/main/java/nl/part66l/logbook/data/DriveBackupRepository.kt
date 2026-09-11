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
import nl.part66l.logbook.drive.DriveApiClient
import nl.part66l.logbook.drive.DriveAuthManager
import nl.part66l.logbook.drive.DriveManifest
import nl.part66l.logbook.drive.DriveManifestStore

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
    private val driveManifestStore: DriveManifestStore,
) : DriveBackupRepository {


    override suspend fun createBackup(activity: FragmentActivity): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = driveAuthManager.authorize(activity).getOrThrow()
            hydrateFolderIdsFromManifest(token)
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
            val staging = File(context.cacheDir, "restore-staging")
            try {
                driveApiClient.downloadFile(token, backup.fileId, tempZip).getOrThrow()
                // Everything that can fail happens here, against a scratch directory, while the
                // live data is still untouched: a truncated download, a corrupt archive, a full
                // disk. Only once the whole archive has been extracted and found to contain a
                // database do we close Room and start replacing files. Before this, extraction
                // ran directly over the live folders after deleting them, so a half-downloaded
                // zip destroyed the user's certificates and photos and left the app holding a
                // closed database with nothing to restore from.
                val stagedDatabase = extractToStaging(tempZip, staging)
                appDatabase.close()
                swapIn(staging, stagedDatabase)
            } finally {
                tempZip.delete()
                staging.deleteRecursively()
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
     * Refreshed on every backup so a backup-only user — who never runs "Sync now" — still leaves a
     * manifest a fresh install can read. Merges rather than overwrites; see [DriveManifestStore].
     */
    private suspend fun writeManifest(token: String, rootFolderId: String, backupsFolderId: String) {
        driveManifestStore.update(token) { existing ->
            DriveManifest(
                rootFolderId = rootFolderId,
                // Neither of these is ours. They belong to the file-mirror sync, and blanking them
                // here would make a fresh install re-create folders that already exist in Drive.
                benchFolderId = settingsRepository.driveBenchFolderId.first() ?: existing?.benchFolderId,
                documentsFolderId = existing?.documentsFolderId,
                backupsFolderId = backupsFolderId,
                aircraftFolderIds = existing?.aircraftFolderIds ?: emptyMap(),
                updatedAt = Instant.now().toEpochMilli(),
            )
        }
    }

    /**
     * Fresh install with nothing cached locally: read the manifest the file-mirror sync already
     * writes to `drive.appdata` and hydrate settings from it. `drive.file` scope can't search
     * Drive by name — only list children of a folder ID it already knows — so without this, a
     * reinstall would have no way to find its own Backups folder at all.
     */
    /**
     * Recovers folder IDs from the manifest before anything is created or looked up.
     *
     * `drive.file` scope cannot search Drive by name, so an install with nothing cached — a fresh
     * one, a restore, a reconnect — can only find the folders an earlier install made by reading
     * the manifest. Without it, a backup would create a second "AMLog" tree beside the first.
     */
    private suspend fun hydrateFolderIdsFromManifest(token: String) {
        if (settingsRepository.driveRootFolderId.first() != null) return
        val manifest = runCatching { driveManifestStore.read(token) }.getOrNull() ?: return
        settingsRepository.setDriveRootFolderId(manifest.rootFolderId)
        manifest.benchFolderId?.let { settingsRepository.setDriveBenchFolderId(it) }
        manifest.documentsFolderId?.let { settingsRepository.setDriveDocumentsFolderId(it) }
        manifest.backupsFolderId?.let { settingsRepository.setDriveBackupsFolderId(it) }
    }

    /** The Backups folder for this account, recovering it from the manifest if nothing is cached. */
    private suspend fun resolveBackupsFolderId(token: String): String {
        settingsRepository.driveBackupsFolderId.first()?.let { return it }
        hydrateFolderIdsFromManifest(token)
        return settingsRepository.driveBackupsFolderId.first()
            ?: throw IllegalStateException("No backup has ever been created for this Google account.")
    }

    /**
     * Unpacks [zipFile] into a scratch [staging] directory and returns the extracted database
     * file, or throws if the archive doesn't contain one. Nothing live is touched here.
     */
    private fun extractToStaging(zipFile: File, staging: File): File {
        staging.deleteRecursively()
        staging.mkdirs()
        val stagingRoot = staging.canonicalPath
        ZipInputStream(zipFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val destination = File(staging, entry.name)
                    // An entry name is attacker-controlled data, not a trusted path: "../.." in one
                    // would otherwise resolve outside the directory we are extracting into. The
                    // archive comes from a Drive folder the user can share, so this is cheap
                    // insurance rather than a theoretical concern.
                    if (!destination.canonicalPath.startsWith(stagingRoot + File.separator)) {
                        throw IllegalStateException("Backup contains an entry outside the archive root: ${entry.name}")
                    }
                    destination.parentFile?.mkdirs()
                    destination.outputStream().use { out -> zip.copyTo(out) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return File(staging, "db/$DATABASE_FILE_NAME").takeIf { it.isFile && it.length() > 0 }
            ?: throw IllegalStateException("Backup contains no database — nothing was changed.")
    }

    /**
     * Moves the staged contents over the live ones. Everything destructive lives here, after
     * [extractToStaging] has already proved the archive is complete.
     */
    private fun swapIn(staging: File, stagedDatabase: File) {
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

        /*
         * Only folders the archive actually carries are replaced. A folder the archive does not
         * mention is left alone rather than deleted.
         *
         * That distinction matters for archives written by an older build. `datastore` was added
         * to this list after the first releases, so a backup taken before it exists says nothing
         * about the settings — and deleting them on its behalf would reset the CRS number
         * template, which is the exact damage including datastore here was meant to prevent. The
         * same rule makes the restore forward-compatible with any folder added later.
         *
         * Deleting datastore at all is safe only because the UI goes straight to a static "close
         * and reopen" screen: DataStore caches in memory and would otherwise write its stale copy
         * back over the restored file, which is the same reason Room needs the restart.
         */
        val covered = CONTENT_FOLDER_NAMES.filter { File(staging, it).isDirectory }
        for (folder in covered) File(context.filesDir, folder).deleteRecursively()

        // Moved rather than copied over in place: a crash midway through an in-place overwrite
        // would leave a half-written database where the old one used to be.
        java.nio.file.Files.move(
            stagedDatabase.toPath(),
            dbFile.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        )
        for (folder in covered) {
            File(staging, folder).copyRecursively(File(context.filesDir, folder), overwrite = true)
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
        /**
         * Everything under filesDir that a restore has to bring back.
         *
         * `datastore` holds the DataStore preferences, and leaving it out made "a complete copy
         * of everything on this device" untrue in a way that mattered: the CRS number template
         * lives there, and CrsNumberFormat.nextSequence only counts numbers the *current*
         * template could have produced. Restoring onto a fresh install therefore reverted the
         * template to its default, matched none of the restored numbers, and allocated the next
         * certificate sequence 1 — a logbook silently renumbering from 0001 after a phone
         * migration. The warning thresholds, the CRS contact-details switch and the Drive folder
         * IDs went the same way.
         *
         * Deliberately not listed: crs-export, recency-export and signing, which hold
         * regenerable share artefacts, and backup-export, which is this process's own scratch
         * space.
         */
        private val CONTENT_FOLDER_NAMES = listOf("crs", "attachments", "documents", "datastore")
    }
}
