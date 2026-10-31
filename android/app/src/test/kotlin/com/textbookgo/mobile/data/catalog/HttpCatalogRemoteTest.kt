package com.textbookgo.mobile.data.catalog

import com.google.common.truth.Truth.assertThat
import com.textbookgo.mobile.data.download.AuthRequiredException
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class HttpCatalogRemoteTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()
    private val permissive: (String) -> okhttp3.HttpUrl = { it.toHttpUrl() }

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun remote(): HttpCatalogRemote = HttpCatalogRemote(
        client = client,
        tokenProvider = { null },
        validateUrl = permissive,
        dataVersionUrl = server.url("/version.json").toString(),
        detailsUrl = { server.url("/details/$it.json").toString() },
    )

    @Test
    fun `parses data version and shard entries`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {"module":"tch_material","module_version":1,"urls":"${server.url("/entries.json")}"}
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """
                [{"id":"b1","title":"数学","tag_list":[{"tag_id":"g","tag_name":"七年级","tag_dimension_id":"zxxnj"}]}]
                """.trimIndent(),
            ),
        )

        val remote = remote()
        val version = remote.dataVersion()
        val entries = remote.entries(version.urls)

        assertThat(version.moduleVersion).isEqualTo(1L)
        assertThat(entries.single().id).isEqualTo("b1")
    }

    @Test
    fun `parses resource details`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {"id":"b1","title":"数学","ti_items":[{"ti_storage":"cs_path:${'$'}{ref-path}/a.pdf","ti_size":10}]}
                """.trimIndent(),
            ),
        )

        val resource = remote().resource("b1")

        assertThat(resource.tiItems.single().tiSize).isEqualTo(10L)
    }

    @Test
    fun `401 maps to auth required`() {
        server.enqueue(MockResponse().setResponseCode(401))

        val error = runCatching {
            runBlocking { remote().dataVersion() }
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(AuthRequiredException::class.java)
    }

    @Test
    fun `403 produces an actionable permission message`() {
        server.enqueue(MockResponse().setResponseCode(403))

        val error = runCatching {
            runBlocking { remote().dataVersion() }
        }.exceptionOrNull()

        assertThat(error).isNotNull()
        assertThat(error!!.message).contains("403")
        assertThat(error.message).contains("权限")
    }

    @Test
    fun `attaches the auth header when the policy allows the host`() {
        server.enqueue(MockResponse().setBody("""{"urls":""}"""))
        val remote = HttpCatalogRemote(
            client = client,
            tokenProvider = { "token-abc" },
            validateUrl = permissive,
            tokenAllowed = { true },
            dataVersionUrl = server.url("/version.json").toString(),
        )

        runBlocking { remote.dataVersion() }

        assertThat(server.takeRequest().getHeader("X-ND-AUTH"))
            .isEqualTo("""MAC id="token-abc",nonce="0",mac="0"""")
    }

    @Test
    fun `omits the auth header when the policy rejects the host`() {
        server.enqueue(MockResponse().setBody("""{"urls":""}"""))
        val remote = HttpCatalogRemote(
            client = client,
            tokenProvider = { "token-abc" },
            validateUrl = permissive,
            tokenAllowed = { false },
            dataVersionUrl = server.url("/version.json").toString(),
        )

        runBlocking { remote.dataVersion() }

        assertThat(server.takeRequest().getHeader("X-ND-AUTH")).isNull()
    }
}
