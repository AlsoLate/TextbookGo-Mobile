package com.textbookgo.mobile.data.catalog

import com.google.common.truth.Truth.assertThat
import com.textbookgo.mobile.core.catalog.CatalogRefreshResult
import com.textbookgo.mobile.core.model.CatalogFilter
import com.textbookgo.mobile.core.repository.CatalogStatus
import com.textbookgo.mobile.testing.FakeShelfRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HttpCatalogRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun entriesJson(id: String, title: String, grade: String, subject: String): String = """
        [{
          "id": "$id",
          "title": "$title",
          "tag_list": [
            {"tag_id": "g-$grade", "tag_name": "$grade", "tag_dimension_id": "zxxnj"},
            {"tag_id": "s-$subject", "tag_name": "$subject", "tag_dimension_id": "zxxxk"},
            {"tag_id": "v", "tag_name": "人教版", "tag_dimension_id": "zxxbb"}
          ],
          "custom_properties": {"thumbnails": ["https://c1.ykt.cbern.com.cn/$id.png"]}
        }]
    """.trimIndent()

    private fun remote() = HttpCatalogRemote(
        client = client,
        tokenProvider = { null },
        validateUrl = { it.toHttpUrl() },
        dataVersionUrl = server.url("/version.json").toString(),
    )

    private fun repository(cache: CatalogCache? = null) = HttpCatalogRepository(
        remote = remote(),
        shelf = FakeShelfRepository(),
        cache = cache,
    )

    @Test
    fun `complete refresh loads shards and exposes grades`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"urls":"${server.url("/p1.json")},${server.url("/p2.json")}"}""",
            ),
        )
        server.enqueue(MockResponse().setBody(entriesJson("b1", "数学 七年级上册", "七年级", "数学")))
        server.enqueue(MockResponse().setBody(entriesJson("b2", "语文 七年级上册", "七年级", "语文")))

        val repository = repository()
        val result = repository.refresh()

        assertThat(result).isInstanceOf(CatalogRefreshResult.Complete::class.java)
        assertThat(repository.grades.value).containsExactly("七年级")
        assertThat(repository.status.value).isEqualTo(CatalogStatus.Loaded(2))
        val books = repository.books(CatalogFilter("七年级", "数学", "")).first()
        assertThat(books.map { it.id }).containsExactly("b1")
        Unit
    }

    @Test
    fun `successful refresh writes a cache that a new instance can read offline`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"urls":"${server.url("/p1.json")}"}"""),
        )
        server.enqueue(MockResponse().setBody(entriesJson("b1", "数学 七年级上册", "七年级", "数学")))

        val cacheFile = File(temp.root, "catalog-cache.json")
        repository(CatalogCache(cacheFile)).refresh()
        assertThat(cacheFile.exists()).isTrue()

        val offline = repository(CatalogCache(cacheFile))
        assertThat(offline.status.value).isEqualTo(CatalogStatus.Loaded(1))
        assertThat(offline.books(CatalogFilter("七年级", "数学", "")).first().map { it.title })
            .containsExactly("数学 七年级上册")
        Unit
    }

    @Test
    fun `partial shard failure keeps the previous generation and reports it`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"urls":"${server.url("/p1.json")},${server.url("/p2.json")}"}""",
            ),
        )
        server.enqueue(MockResponse().setBody(entriesJson("b1", "数学 七年级上册", "七年级", "数学")))
        server.enqueue(MockResponse().setBody(entriesJson("b2", "语文 七年级上册", "七年级", "语文")))

        val repository = repository()
        repository.refresh()
        val cachedBooks = repository.books(CatalogFilter("七年级", "数学", "")).first()

        server.enqueue(
            MockResponse().setBody(
                """{"urls":"${server.url("/p1.json")},${server.url("/p2.json")}"}""",
            ),
        )
        server.enqueue(MockResponse().setBody(entriesJson("b3", "英语 七年级上册", "七年级", "英语")))
        server.enqueue(MockResponse().setResponseCode(500))

        val result = repository.refresh()

        assertThat(result).isInstanceOf(CatalogRefreshResult.Partial::class.java)
        assertThat(repository.status.value).isInstanceOf(CatalogStatus.Failed::class.java)
        assertThat(repository.books(CatalogFilter("七年级", "数学", "")).first()).isEqualTo(cachedBooks)
    }

    @Test
    fun `network failure with no cache reports failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))

        val repository = repository()
        val result = repository.refresh()

        assertThat(result).isInstanceOf(CatalogRefreshResult.Failed::class.java)
        assertThat(repository.status.value).isInstanceOf(CatalogStatus.Failed::class.java)
        assertThat(repository.books(CatalogFilter("七年级", "数学", "")).first()).isEmpty()
    }

    @Test
    fun `fresh cache skips the network unless refresh is forced`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"urls":"${server.url("/p1.json")}"}"""),
        )
        server.enqueue(MockResponse().setBody(entriesJson("b1", "数学 七年级上册", "七年级", "数学")))

        val cacheFile = File(temp.root, "catalog-cache.json")
        repository(CatalogCache(cacheFile)).refresh()
        val requestsAfterFirst = server.requestCount

        val cached = repository(CatalogCache(cacheFile))
        val result = cached.refresh()

        assertThat(result).isInstanceOf(CatalogRefreshResult.Complete::class.java)
        assertThat(server.requestCount).isEqualTo(requestsAfterFirst)

        server.enqueue(
            MockResponse().setBody("""{"urls":"${server.url("/p1.json")}"}"""),
        )
        server.enqueue(MockResponse().setBody(entriesJson("b2", "数学 八年级上册", "八年级", "数学")))
        cached.refresh(force = true)
        assertThat(server.requestCount).isGreaterThan(requestsAfterFirst)
    }
}
