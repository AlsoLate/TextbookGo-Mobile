package com.textbookgo.mobile.data.download

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

class HttpTransferTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()
    private val noRedirectClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun response(bytes: ByteArray, code: Int = 200, etag: String? = "\"v1\""): MockResponse =
        MockResponse()
            .setResponseCode(code)
            .setHeader("Content-Length", bytes.size)
            .apply { etag?.let { setHeader("ETag", it) } }
            .setBody(Buffer().write(bytes))

    @Test
    fun `downloads a full file and reports progress`() = runBlocking {
        val bytes = ByteArray(32) { it.toByte() }
        server.enqueue(response(bytes))
        val target = File(temp.root, "book.pdf")
        val progresses = mutableListOf<Long>()

        HttpTransfer(client) { it.toHttpUrl() }.download(
            spec = ContentSpec("t", listOf(server.url("/book.pdf").toString()), size = bytes.size.toLong()),
            target = target,
            onProgress = { read, _ -> progresses += read },
        )

        assertThat(target.readBytes().toList()).isEqualTo(bytes.toList())
        assertThat(progresses.last()).isEqualTo(bytes.size.toLong())
    }

    @Test
    fun `resumes with 206 when a validator and partial file exist`() = runBlocking {
        val bytes = ByteArray(10) { it.toByte() }
        val target = File(temp.root, "book.pdf")
        val transfer = HttpTransfer(client) { it.toHttpUrl() }

        server.enqueue(response(bytes))
        transfer.download(spec(bytes, "/book.pdf"), target) { _, _ -> }
        server.takeRequest()

        RandomAccessFile(target, "rw").use { it.setLength(5) }
        server.enqueue(
            response(bytes.copyOfRange(5, 10), code = 206)
                .setHeader("Content-Range", "bytes 5-9/10"),
        )

        transfer.download(spec(bytes, "/book.pdf"), target) { _, _ -> }

        assertThat(target.readBytes().toList()).isEqualTo(bytes.toList())
        val rangeRequest = server.takeRequest()
        assertThat(rangeRequest.getHeader("Range")).isEqualTo("bytes=5-")
    }

    @Test
    fun `200 on resume truncates and downloads from scratch`() = runBlocking {
        val bytes = ByteArray(10) { (it + 1).toByte() }
        val target = File(temp.root, "book.pdf")
        val transfer = HttpTransfer(client) { it.toHttpUrl() }

        server.enqueue(response(bytes))
        transfer.download(spec(bytes, "/book.pdf"), target) { _, _ -> }
        server.takeRequest()

        RandomAccessFile(target, "rw").use { it.setLength(5) }
        server.enqueue(response(bytes))

        transfer.download(spec(bytes, "/book.pdf"), target) { _, _ -> }

        assertThat(target.readBytes().toList()).isEqualTo(bytes.toList())
    }

    @Test
    fun `401 maps to auth required`() {
        server.enqueue(MockResponse().setResponseCode(401))
        val target = File(temp.root, "book.pdf")

        val error = runCatching {
            runBlocking {
                HttpTransfer(client) { it.toHttpUrl() }.download(spec(ByteArray(1), "/book.pdf"), target) { _, _ -> }
            }
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(AuthRequiredException::class.java)
    }

    @Test
    fun `403 from the private cdn also maps to auth required`() {
        server.enqueue(MockResponse().setResponseCode(403))
        val target = File(temp.root, "book.pdf")

        val error = runCatching {
            runBlocking {
                HttpTransfer(client) { it.toHttpUrl() }.download(spec(ByteArray(1), "/book.pdf"), target) { _, _ -> }
            }
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(AuthRequiredException::class.java)
    }

    @Test
    fun `falls back to the backup url when the primary fails`() = runBlocking {
        val bytes = ByteArray(10) { (it + 3).toByte() }
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(response(bytes))
        val target = File(temp.root, "book.pdf")
        val spec = ContentSpec(
            title = "t",
            urls = listOf(
                server.url("/primary.pdf").toString(),
                server.url("/backup.pdf").toString(),
            ),
            size = bytes.size.toLong(),
        )

        HttpTransfer(client) { it.toHttpUrl() }.download(spec, target) { _, _ -> }

        assertThat(target.readBytes().toList()).isEqualTo(bytes.toList())
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `attaches the platform auth header for authenticated hosts`() = runBlocking {
        val bytes = ByteArray(8) { 1 }
        server.enqueue(response(bytes))
        val target = File(temp.root, "book.pdf")

        HttpTransfer(
            client = client,
            tokenProvider = { "token-123" },
            hostAllowed = { true },
            tokenAllowed = { true },
            validateUrl = { it.toHttpUrl() },
        ).download(spec(bytes, "/book.pdf"), target) { _, _ -> }

        val request = server.takeRequest()
        assertThat(request.getHeader("X-ND-AUTH")).isEqualTo("""MAC id="token-123",nonce="0",mac="0"""")
    }

    @Test
    fun `does not leak the auth header to public hosts`() = runBlocking {
        val bytes = ByteArray(8) { 1 }
        server.enqueue(response(bytes))
        val target = File(temp.root, "book.pdf")

        HttpTransfer(
            client = client,
            tokenProvider = { "token-123" },
            hostAllowed = { true },
            tokenAllowed = { false },
            validateUrl = { it.toHttpUrl() },
        ).download(spec(bytes, "/book.pdf"), target) { _, _ -> }

        assertThat(server.takeRequest().getHeader("X-ND-AUTH")).isNull()
    }

    @Test
    fun `follows redirects that stay on allowed hosts`() = runBlocking {
        val bytes = ByteArray(12) { 9 }
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", server.url("/final.pdf").toString()),
        )
        server.enqueue(response(bytes))
        val target = File(temp.root, "book.pdf")

        HttpTransfer(
            client = noRedirectClient,
            hostAllowed = { true },
            validateUrl = { it.toHttpUrl() },
        ).download(spec(bytes, "/book.pdf"), target) { _, _ -> }

        assertThat(target.readBytes().toList()).isEqualTo(bytes.toList())
        assertThat(server.requestCount).isEqualTo(2)
        assertThat(server.takeRequest().path).isEqualTo("/book.pdf")
    }

    @Test
    fun `rejects redirects to untrusted hosts`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "https://evil.example.com/book.pdf"),
        )
        val target = File(temp.root, "book.pdf")

        val error = runCatching {
            runBlocking {
                HttpTransfer(
                    client = noRedirectClient,
                    hostAllowed = { it.host.endsWith("ykt.cbern.com.cn") },
                    validateUrl = { it.toHttpUrl() },
                ).download(spec(ByteArray(4), "/book.pdf"), target) { _, _ -> }
            }
        }.exceptionOrNull()

        assertThat(error).isNotNull()
        assertThat(error!!.message).contains("未授权主机")
    }

    private fun spec(bytes: ByteArray, path: String) = ContentSpec(
        title = "t",
        urls = listOf(server.url(path).toString()),
        size = bytes.size.toLong(),
    )
}

