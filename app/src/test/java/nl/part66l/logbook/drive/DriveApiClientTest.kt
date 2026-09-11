package nl.part66l.logbook.drive

import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises [DriveApiClientImpl] against a real (local) HTTP server rather than mocking OkHttp
 * itself — an interceptor rewrites every request's host/port to the [MockWebServer]'s, so the
 * real request-building code (URLs, headers, multipart bodies) runs unmodified.
 */
class DriveApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: DriveApiClientImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val redirected = original.url.newBuilder()
                    .scheme("http")
                    .host(server.hostName)
                    .port(server.port)
                    .build()
                chain.proceed(original.newBuilder().url(redirected).build())
            }
            .build()
        client = DriveApiClientImpl(httpClient)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `createFolder posts folder metadata and returns the created id`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"folder-123"}"""))

        val result = client.createFolder("token-1", "Part-66L Logbook", "root")

        assertEquals("folder-123", result.getOrThrow())
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/drive/v3/files", request.path)
        assertEquals("Bearer token-1", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"name\":\"Part-66L Logbook\""))
        assertTrue(body.contains("\"mimeType\":\"application/vnd.google-apps.folder\""))
        assertTrue(body.contains("\"parents\":[\"root\"]"))
    }

    @Test
    fun `uploadFile sends a multipart-related POST to the upload endpoint`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"file-456"}"""))

        val result = client.uploadFile("token-1", "CRS-1.pdf", "folder-1", byteArrayOf(1, 2, 3, 4), "application/pdf")

        assertEquals("file-456", result.getOrThrow())
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/upload/drive/v3/files?uploadType=multipart", request.path)
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("multipart/related"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"name\":\"CRS-1.pdf\""))
        assertTrue(body.contains("\"parents\":[\"folder-1\"]"))
    }

    @Test
    fun `uploadAppDataFile targets the appDataFolder parent`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"appdata-1"}"""))

        val result = client.uploadAppDataFile("token-1", "manifest.json", "{}".toByteArray())

        assertEquals("appdata-1", result.getOrThrow())
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"parents\":[\"appDataFolder\"]"))
    }

    @Test
    fun `a non-2xx response surfaces as Result failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"insufficient permissions"}"""))

        val result = client.createFolder("token-1", "Part-66L Logbook", "root")

        assertTrue(result.isFailure)
        assertNull(result.getOrNull())
    }

    @Test
    fun `uploadLargeFile streams a file's contents and returns the created id`() = runBlocking {
        val file = File.createTempFile("backup", ".zip").apply { writeBytes(byteArrayOf(10, 20, 30)) }
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"backup-1"}"""))

        val result = client.uploadLargeFile("token-1", "backup-1.zip", "folder-1", file, "application/zip")

        assertEquals("backup-1", result.getOrThrow())
        val request = server.takeRequest()
        assertEquals("/upload/drive/v3/files?uploadType=multipart", request.path)
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("multipart/related"))
        assertTrue(request.body.readUtf8().contains("\"name\":\"backup-1.zip\""))
    }

    @Test
    fun `listFiles builds a query scoped to the given parent, spaces and name`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"files":[{"id":"f1","name":"manifest.json","createdTime":"2026-01-02T03:04:05.000Z"}]}""",
            ),
        )

        val result = client.listFiles("token-1", parentId = null, spaces = "appDataFolder", nameEquals = "manifest.json")

        val files = result.getOrThrow()
        assertEquals(1, files.size)
        assertEquals("f1", files[0].id)
        assertEquals("manifest.json", files[0].name)
        assertEquals(java.time.Instant.parse("2026-01-02T03:04:05.000Z"), files[0].createdTime)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        val url = request.requestUrl!!
        assertEquals("appDataFolder", url.queryParameter("spaces"))
        assertTrue(url.queryParameter("q")!!.contains("name = 'manifest.json'"))
        assertTrue(url.queryParameter("q")!!.contains("trashed = false"))
    }

    @Test
    fun `listFiles scopes the query to a parent folder when given`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"files":[]}"""))

        client.listFiles("token-1", parentId = "folder-9")

        val url = server.takeRequest().requestUrl!!
        assertEquals("drive", url.queryParameter("spaces"))
        assertTrue(url.queryParameter("q")!!.contains("'folder-9' in parents"))
    }

    @Test
    fun `downloadFile streams the response body to the destination file`() = runBlocking {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(bytes)))
        val destination = File.createTempFile("downloaded", ".zip")

        val result = client.downloadFile("token-1", "file-1", destination)

        assertTrue(result.isSuccess)
        assertArrayEquals(bytes, destination.readBytes())
        val request = server.takeRequest()
        assertEquals("/drive/v3/files/file-1", request.path!!.substringBefore("?"))
        assertEquals("media", request.requestUrl!!.queryParameter("alt"))
        assertEquals("Bearer token-1", request.getHeader("Authorization"))
    }

    @Test
    fun `downloadBytes returns the response body directly`() = runBlocking {
        val bytes = byteArrayOf(9, 8, 7)
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(bytes)))

        val result = client.downloadBytes("token-1", "file-1")

        assertArrayEquals(bytes, result.getOrThrow())
    }
}
