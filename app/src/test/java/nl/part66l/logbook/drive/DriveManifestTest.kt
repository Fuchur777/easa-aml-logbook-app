package nl.part66l.logbook.drive

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class DriveManifestTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `round-trips through JSON`() {
        val manifest = DriveManifest(
            rootFolderId = "root-1",
            benchFolderId = "bench-1",
            backupsFolderId = "backups-1",
            aircraftFolderIds = mapOf("aircraft-1" to "folder-1", "aircraft-2" to "folder-2"),
            updatedAt = 1_700_000_000_000L,
        )

        val encoded = json.encodeToString(manifest)
        val decoded = json.decodeFromString<DriveManifest>(encoded)

        assertEquals(manifest, decoded)
    }
}
