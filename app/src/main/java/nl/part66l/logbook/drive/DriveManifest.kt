package nl.part66l.logbook.drive

import kotlinx.serialization.Serializable

/**
 * Written to `drive.appdata` after every successful sync (§10) and read back by
 * `DriveBackupRepository` on a fresh install — `drive.file` scope alone can't search Drive by
 * name, only list the children of a folder ID it already knows, so this manifest is the only way
 * a reinstall (with no locally cached settings) can relocate the folders an earlier install
 * created.
 */
@Serializable
data class DriveManifest(
    val rootFolderId: String,
    val benchFolderId: String? = null,
    val documentsFolderId: String? = null,
    val backupsFolderId: String? = null,
    val aircraftFolderIds: Map<String, String>,
    val updatedAt: Long,
)
