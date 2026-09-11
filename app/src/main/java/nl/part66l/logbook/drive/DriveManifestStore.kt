package nl.part66l.logbook.drive

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Read-merge-write access to the single `drive.appdata` manifest.
 *
 * Both the file-mirror sync and the backup path need to record folder IDs there, but each knows
 * only its own half: sync owns the root, bench and per-aircraft folders, backup owns the Backups
 * folder. They used to write the whole document from their own local state, so whichever ran last
 * blanked the other's fields — a sync on a replacement phone published `backupsFolderId = null`
 * and made existing backups permanently unreachable, since `drive.file` scope cannot search Drive
 * by name and the manifest is the only way to relocate them.
 *
 * [update] hands the writer whatever is already in Drive so it can carry those fields forward.
 */
@Singleton
class DriveManifestStore @Inject constructor(private val driveApiClient: DriveApiClient) {

    private val json = Json { ignoreUnknownKeys = true }

    /** The manifest currently in appdata, or null if there is none (or it can't be parsed). */
    suspend fun read(token: String): DriveManifest? {
        val file = driveApiClient.listFiles(token, spaces = APPDATA_SPACE, nameEquals = MANIFEST_NAME)
            .getOrThrow()
            .firstOrNull()
            ?: return null
        val bytes = driveApiClient.downloadBytes(token, file.id).getOrThrow()
        // A manifest we can't parse is treated as absent rather than fatal: the caller is about to
        // write a fresh one anyway, and refusing to sync because of a corrupt bookkeeping file
        // would be a worse outcome than losing the fields it held.
        return runCatching { json.decodeFromString<DriveManifest>(String(bytes)) }.getOrNull()
    }

    /** Applies [update] to the manifest already in Drive (null if none) and writes the result back. */
    suspend fun update(token: String, update: suspend (DriveManifest?) -> DriveManifest) {
        val merged = update(read(token))
        driveApiClient.uploadAppDataFile(token, MANIFEST_NAME, json.encodeToString(merged).toByteArray()).getOrThrow()
    }

    companion object {
        const val MANIFEST_NAME = "manifest.json"
        const val APPDATA_SPACE = "appDataFolder"
    }
}
