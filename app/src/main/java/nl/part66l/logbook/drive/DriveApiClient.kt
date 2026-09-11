package nl.part66l.logbook.drive

import java.io.File
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
private const val USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo"
private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"

/** One file/folder returned by [DriveApiClient.listFiles]. */
data class DriveFile(val id: String, val name: String, val createdTime: Instant)

/**
 * Thin REST wrapper over Drive v3 (§10) — deliberately not the heavy `google-api-services-drive`
 * client, per current Google guidance for Android. Never caches the access token: every call
 * takes it fresh, since [DriveAuthManager.authorize] tokens are 1-hour-lived and a sync run
 * fetches one up front and threads it through.
 */
interface DriveApiClient {
    suspend fun fetchAccountEmail(accessToken: String): Result<String>

    /** Creates a plain folder under [parentId] (`"root"` for Drive's own top level). */
    suspend fun createFolder(accessToken: String, name: String, parentId: String): Result<String>

    suspend fun uploadFile(accessToken: String, name: String, parentId: String, bytes: ByteArray, mimeType: String): Result<String>

    /** As [uploadFile], but into the hidden per-app `appDataFolder` space rather than a visible parent. */
    suspend fun uploadAppDataFile(accessToken: String, name: String, jsonBytes: ByteArray): Result<String>

    /** As [uploadFile], but streams [file] from disk instead of loading it into memory first — for full-device backups, which can be much larger than a CRS PDF or photo. */
    suspend fun uploadLargeFile(accessToken: String, name: String, parentId: String, file: File, mimeType: String): Result<String>

    /**
     * Lists files under [parentId] (or, when null, every file the [spaces] scope can see) and
     * optionally filtered to an exact [nameEquals]. `drive.file` scope only ever returns files
     * this app created, regardless of query shape — so this is safe to call broadly.
     */
    suspend fun listFiles(accessToken: String, parentId: String? = null, spaces: String = "drive", nameEquals: String? = null): Result<List<DriveFile>>

    /** Streams [fileId]'s content straight to [destination] — for a full-device backup zip, which can be too large to hold entirely in memory. */
    suspend fun downloadFile(accessToken: String, fileId: String, destination: File): Result<Unit>

    /** As [downloadFile], but returns the bytes directly — for small files only (the `drive.appdata` manifest). */
    suspend fun downloadBytes(accessToken: String, fileId: String): Result<ByteArray>
}

@Serializable
internal data class DriveFileMetadata(val name: String, val mimeType: String? = null, val parents: List<String>? = null)

@Serializable
internal data class DriveFileIdResponse(val id: String)

@Serializable
internal data class UserInfoResponse(val email: String? = null)

@Serializable
internal data class DriveFileListItem(val id: String, val name: String, val createdTime: String)

@Serializable
internal data class DriveFileListResponse(val files: List<DriveFileListItem> = emptyList())

class DriveApiClientImpl @Inject constructor(private val httpClient: OkHttpClient) : DriveApiClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=UTF-8".toMediaType()

    override suspend fun fetchAccountEmail(accessToken: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(USERINFO_URL)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            val body = httpClient.newCall(request).execute().use { requireSuccessBody(it) }
            json.decodeFromString<UserInfoResponse>(body).email
                ?: throw IOException("Drive account userinfo response had no email")
        }
    }

    override suspend fun createFolder(accessToken: String, name: String, parentId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val metadata = DriveFileMetadata(name = name, mimeType = FOLDER_MIME_TYPE, parents = listOf(parentId))
            val request = Request.Builder()
                .url(DRIVE_FILES_URL)
                .header("Authorization", "Bearer $accessToken")
                .post(json.encodeToString(metadata).toRequestBody(jsonMediaType))
                .build()
            val body = httpClient.newCall(request).execute().use { requireSuccessBody(it) }
            json.decodeFromString<DriveFileIdResponse>(body).id
        }
    }

    override suspend fun uploadFile(accessToken: String, name: String, parentId: String, bytes: ByteArray, mimeType: String): Result<String> =
        upload(accessToken, DriveFileMetadata(name = name, parents = listOf(parentId)), bytes.toRequestBody(mimeType.toMediaType()))

    override suspend fun uploadAppDataFile(accessToken: String, name: String, jsonBytes: ByteArray): Result<String> =
        upload(accessToken, DriveFileMetadata(name = name, parents = listOf("appDataFolder")), jsonBytes.toRequestBody("application/json".toMediaType()))

    override suspend fun uploadLargeFile(accessToken: String, name: String, parentId: String, file: File, mimeType: String): Result<String> =
        upload(accessToken, DriveFileMetadata(name = name, parents = listOf(parentId)), file.asRequestBody(mimeType.toMediaType()))

    override suspend fun listFiles(accessToken: String, parentId: String?, spaces: String, nameEquals: String?): Result<List<DriveFile>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val conditions = mutableListOf("trashed = false")
                parentId?.let { conditions += "'${escapeForQuery(it)}' in parents" }
                nameEquals?.let { conditions += "name = '${escapeForQuery(it)}'" }
                val url = DRIVE_FILES_URL.toHttpUrl().newBuilder()
                    .addQueryParameter("q", conditions.joinToString(" and "))
                    .addQueryParameter("spaces", spaces)
                    .addQueryParameter("fields", "files(id,name,createdTime)")
                    .addQueryParameter("orderBy", "createdTime desc")
                    .build()
                val request = Request.Builder().url(url).header("Authorization", "Bearer $accessToken").get().build()
                val body = httpClient.newCall(request).execute().use { requireSuccessBody(it) }
                json.decodeFromString<DriveFileListResponse>(body).files.map { DriveFile(it.id, it.name, Instant.parse(it.createdTime)) }
            }
        }

    override suspend fun downloadFile(accessToken: String, fileId: String, destination: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = downloadRequest(accessToken, fileId)
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw driveApiError(response)
                val body = response.body ?: throw IOException("Drive download of $fileId had no response body")
                destination.outputStream().use { out -> body.byteStream().copyTo(out) }
                Unit
            }
        }
    }

    override suspend fun downloadBytes(accessToken: String, fileId: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val request = downloadRequest(accessToken, fileId)
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw driveApiError(response)
                response.body?.bytes() ?: throw IOException("Drive download of $fileId had no response body")
            }
        }
    }

    private fun downloadRequest(accessToken: String, fileId: String): Request {
        val url = "$DRIVE_FILES_URL/$fileId".toHttpUrl().newBuilder().addQueryParameter("alt", "media").build()
        return Request.Builder().url(url).header("Authorization", "Bearer $accessToken").get().build()
    }

    /** Drive query strings delimit with single quotes — the only character in a folder/file name or ID that needs escaping. */
    private fun escapeForQuery(value: String): String = value.replace("'", "\\'")

    /**
     * Drive's `multipart` upload: a JSON metadata part followed by the file content, both as
     * plain [MultipartBody] parts (no per-part Content-Disposition needed — Drive keys each part
     * by position, not name) under a `multipart/related` envelope.
     */
    private suspend fun upload(accessToken: String, metadata: DriveFileMetadata, content: RequestBody): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val multipartBody = MultipartBody.Builder()
                    .setType("multipart/related".toMediaType())
                    .addPart(json.encodeToString(metadata).toRequestBody(jsonMediaType))
                    .addPart(content)
                    .build()
                val request = Request.Builder()
                    .url(DRIVE_UPLOAD_URL)
                    .header("Authorization", "Bearer $accessToken")
                    .post(multipartBody)
                    .build()
                val body = httpClient.newCall(request).execute().use { requireSuccessBody(it) }
                json.decodeFromString<DriveFileIdResponse>(body).id
            }
        }

    private fun requireSuccessBody(response: Response): String {
        if (!response.isSuccessful) throw driveApiError(response)
        return response.body?.string().orEmpty()
    }

    private fun driveApiError(response: Response): IOException =
        IOException("Drive API call failed: HTTP ${response.code} — ${response.body?.string().orEmpty()}")
}
