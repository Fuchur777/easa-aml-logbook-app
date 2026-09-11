package nl.part66l.logbook.fakes

import java.io.File
import java.time.Instant
import nl.part66l.logbook.drive.DriveApiClient
import nl.part66l.logbook.drive.DriveFile

/** Stands in for [nl.part66l.logbook.drive.DriveApiClientImpl] — records every call so a repository-level test can assert on folder reuse, upload contents, and per-item failure isolation without a real network. Also keeps a small in-memory "Drive" (by id/parent/space) so [listFiles]/[downloadFile]/[downloadBytes] behave like a real round trip. */
class FakeDriveApiClient(
    private val accountEmail: String = "fake@example.com",
) : DriveApiClient {

    data class CreatedFolder(val name: String, val parentId: String, val id: String)
    data class UploadedFile(val name: String, val parentId: String, val bytes: ByteArray, val mimeType: String, val id: String)

    val createdFolders = mutableListOf<CreatedFolder>()
    val uploadedFiles = mutableListOf<UploadedFile>()
    val appDataFiles = mutableListOf<UploadedFile>()

    /** Keyed by folder/file name — set to make that one call fail (once, until cleared). */
    val createFolderFailures = mutableMapOf<String, Throwable>()
    val uploadFailures = mutableMapOf<String, Throwable>()

    /** Keyed by file id — set to make that one download fail (once, until cleared). */
    val downloadFailures = mutableMapOf<String, Throwable>()

    private var nextId = 1
    private var nextTimestamp = 0L

    private data class StoredEntry(val id: String, val name: String, val parentId: String, val spaces: String, val bytes: ByteArray, val createdTime: Instant)

    private val entriesById = mutableMapOf<String, StoredEntry>()

    private fun remember(id: String, name: String, parentId: String, spaces: String, bytes: ByteArray) {
        entriesById[id] = StoredEntry(id, name, parentId, spaces, bytes, Instant.ofEpochSecond(nextTimestamp++))
    }

    override suspend fun fetchAccountEmail(accessToken: String): Result<String> = Result.success(accountEmail)

    override suspend fun createFolder(accessToken: String, name: String, parentId: String): Result<String> {
        createFolderFailures.remove(name)?.let { return Result.failure(it) }
        val id = "folder-${nextId++}"
        createdFolders += CreatedFolder(name, parentId, id)
        remember(id, name, parentId, spaces = "drive", bytes = ByteArray(0))
        return Result.success(id)
    }

    override suspend fun uploadFile(accessToken: String, name: String, parentId: String, bytes: ByteArray, mimeType: String): Result<String> {
        uploadFailures.remove(name)?.let { return Result.failure(it) }
        val id = "file-${nextId++}"
        uploadedFiles += UploadedFile(name, parentId, bytes, mimeType, id)
        remember(id, name, parentId, spaces = "drive", bytes = bytes)
        return Result.success(id)
    }

    override suspend fun uploadAppDataFile(accessToken: String, name: String, jsonBytes: ByteArray): Result<String> {
        val id = "appdata-${nextId++}"
        appDataFiles += UploadedFile(name, "appDataFolder", jsonBytes, "application/json", id)
        remember(id, name, "appDataFolder", spaces = "appDataFolder", bytes = jsonBytes)
        return Result.success(id)
    }

    override suspend fun uploadLargeFile(accessToken: String, name: String, parentId: String, file: File, mimeType: String): Result<String> {
        uploadFailures.remove(name)?.let { return Result.failure(it) }
        val bytes = file.readBytes()
        val id = "file-${nextId++}"
        uploadedFiles += UploadedFile(name, parentId, bytes, mimeType, id)
        remember(id, name, parentId, spaces = "drive", bytes = bytes)
        return Result.success(id)
    }

    override suspend fun listFiles(accessToken: String, parentId: String?, spaces: String, nameEquals: String?): Result<List<DriveFile>> {
        val matches = entriesById.values.filter { entry ->
            entry.spaces == spaces &&
                (parentId == null || entry.parentId == parentId) &&
                (nameEquals == null || entry.name == nameEquals)
        }.sortedByDescending { it.createdTime }
        return Result.success(matches.map { DriveFile(it.id, it.name, it.createdTime) })
    }

    override suspend fun downloadFile(accessToken: String, fileId: String, destination: File): Result<Unit> {
        downloadFailures.remove(fileId)?.let { return Result.failure(it) }
        val entry = entriesById[fileId] ?: return Result.failure(NoSuchElementException("no such file: $fileId"))
        destination.writeBytes(entry.bytes)
        return Result.success(Unit)
    }

    override suspend fun downloadBytes(accessToken: String, fileId: String): Result<ByteArray> {
        downloadFailures.remove(fileId)?.let { return Result.failure(it) }
        val entry = entriesById[fileId] ?: return Result.failure(NoSuchElementException("no such file: $fileId"))
        return Result.success(entry.bytes)
    }
}
