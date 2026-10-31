package com.textbookgo.mobile.data.download

import com.google.common.truth.Truth.assertThat
import com.textbookgo.mobile.core.catalog.CatalogRefreshResult
import com.textbookgo.mobile.core.catalog.CatalogRules
import com.textbookgo.mobile.core.download.DownloadState
import com.textbookgo.mobile.core.download.DownloadTask
import com.textbookgo.mobile.core.model.Book
import com.textbookgo.mobile.core.model.CatalogEntry
import com.textbookgo.mobile.core.model.CatalogFilter
import com.textbookgo.mobile.core.repository.CatalogRepository
import com.textbookgo.mobile.core.repository.ShelfRepository
import com.textbookgo.mobile.data.store.TaskStore
import com.textbookgo.mobile.testing.FakeShelfRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class DownloadRepositoryImplTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var booksDir: File

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        booksDir = temp.newFolder("books")
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private class FakeCatalog(private val entries: List<CatalogEntry>) : CatalogRepository {
        override val grades = MutableStateFlow(CatalogRules.grades(entries))
        override val status = MutableStateFlow<com.textbookgo.mobile.core.repository.CatalogStatus>(
            com.textbookgo.mobile.core.repository.CatalogStatus.Loaded(entries.size),
        )

        override fun subjects(grade: String): Flow<List<String>> =
            flowOf(CatalogRules.subjects(entries, grade))

        override fun versions(grade: String, subject: String): Flow<List<String>> =
            flowOf(CatalogRules.versions(entries, grade, subject))

        override fun books(filter: CatalogFilter): Flow<List<Book>> =
            flowOf(CatalogRules.books(entries, filter.grade, filter.subject, emptySet()))

        override suspend fun findBook(id: String): Book? =
            entries.firstOrNull { it.id == id }?.let { Book(it.id, it.title, coverUrl = it.coverUrl) }

        override suspend fun refresh(force: Boolean): CatalogRefreshResult =
            CatalogRefreshResult.Complete(1, entries.size)
    }

    private class FakeContent(
        private val size: Int = 16,
        private val md5: String? = null,
    ) : ContentSource {
        override suspend fun resolve(bookId: String): ContentSpec =
            ContentSpec(
                title = "t",
                urls = listOf("https://c1.ykt.cbern.com.cn/a.pdf"),
                size = size.toLong(),
                md5 = md5,
            )
    }

    private class FakeTransfer(var failure: Exception? = null, var payload: ByteArray? = null) : Transfer {
        override suspend fun download(
            spec: ContentSpec,
            target: File,
            onProgress: (Long, Long?) -> Unit,
        ) {
            failure?.let { throw it }
            target.parentFile?.mkdirs()
            target.writeBytes(payload ?: (byteArrayOf(0x25, 0x50, 0x44, 0x46) + ByteArray((spec.size?.toInt() ?: 8) - 4) { 7 }))
            onProgress(target.length(), spec.size)
        }
    }

    private class BlockingTransfer : Transfer {
        val started = CompletableDeferred<Unit>()

        override suspend fun download(
            spec: ContentSpec,
            target: File,
            onProgress: (Long, Long?) -> Unit,
        ) {
            started.complete(Unit)
            awaitCancellation()
        }
    }

    private fun create(
        transfer: Transfer,
        shelf: ShelfRepository = FakeShelfRepository(),
        content: ContentSource = FakeContent(),
        onAuthRequired: () -> Unit = {},
    ): DownloadRepositoryImpl = DownloadRepositoryImpl(
        content = content,
        catalog = FakeCatalog(
            listOf(
                CatalogEntry("b1", "教材一"),
                CatalogEntry("b2", "教材二"),
            ),
        ),
        shelf = shelf,
        transfer = transfer,
        store = TaskStore(File(temp.root, "tasks.json")),
        booksDir = booksDir,
        scope = scope,
        onAuthRequired = onAuthRequired,
    )

    private suspend fun DownloadRepositoryImpl.awaitTask(
        predicate: (DownloadTask) -> Boolean,
    ): DownloadTask = withTimeout(5_000) {
        tasks().first { list -> list.any(predicate) }.first(predicate)
    }

    @Test
    fun `successful download commits file and shelf`() = runBlocking {
        val shelf = FakeShelfRepository()
        val repository = create(FakeTransfer(), shelf)

        val result = repository.enqueue(setOf("b1"))
        repository.awaitTask { it.state == DownloadState.SUCCEEDED }

        assertThat(result.accepted).isEqualTo(1)
        assertThat(File(booksDir, "b1.pdf").exists()).isTrue()
        assertThat(shelf.shelf().first()).hasSize(1)
        Unit
    }

    @Test
    fun `shelf keeps the catalog cover for the downloaded book`() = runBlocking {
        val shelf = FakeShelfRepository()
        val catalog = FakeCatalog(
            listOf(
                CatalogEntry(
                    id = "b1",
                    title = "教材一",
                    coverUrl = "https://r2-ndr.ykt.cbern.com.cn/cover.jpg",
                ),
            ),
        )
        val repository = DownloadRepositoryImpl(
            content = FakeContent(),
            catalog = catalog,
            shelf = shelf,
            transfer = FakeTransfer(),
            store = TaskStore(File(temp.root, "tasks.json")),
            booksDir = booksDir,
            scope = scope,
        )

        repository.enqueue(setOf("b1"))
        repository.awaitTask { it.state == DownloadState.SUCCEEDED }

        assertThat(shelf.shelf().first().single().coverUrl)
            .isEqualTo("https://r2-ndr.ykt.cbern.com.cn/cover.jpg")
        Unit
    }

    @Test
    fun `second enqueue deduplicates existing books`() = runBlocking {
        val repository = create(FakeTransfer())

        repository.enqueue(setOf("b1"))
        repository.awaitTask { it.bookId == "b1" && it.state == DownloadState.SUCCEEDED }
        val second = repository.enqueue(setOf("b1", "b2"))
        repository.awaitTask { it.bookId == "b2" && it.state == DownloadState.SUCCEEDED }

        assertThat(second.accepted).isEqualTo(1)
        assertThat(second.deduplicated).isEqualTo(1)
        Unit
    }

    @Test
    fun `failure can be retried`() = runBlocking {
        val transfer = FakeTransfer().apply { failure = IOException("boom") }
        val repository = create(transfer)

        repository.enqueue(setOf("b1"))
        val failed = repository.awaitTask { it.state == DownloadState.FAILED }

        transfer.failure = null
        repository.retry(failed.taskId)
        repository.awaitTask { it.state == DownloadState.SUCCEEDED }
        Unit
    }

    @Test
    fun `non pdf payload is rejected`() = runBlocking {
        val repository = create(FakeTransfer(payload = "not a pdf".toByteArray()))

        repository.enqueue(setOf("b1"))
        val failed = repository.awaitTask { it.state == DownloadState.FAILED }

        assertThat(failed.errorMessage).contains("PDF")
        Unit
    }

    @Test
    fun `books already on the shelf are not downloaded again`() = runBlocking {
        val transfer = FakeTransfer()
        val shelf = FakeShelfRepository(
            initial = listOf(
                com.textbookgo.mobile.core.model.ShelfBook(
                    id = "b1",
                    title = "教材一",
                    filename = "b1.pdf",
                    size = 16,
                    downloadedAt = "2026-10-31T00:00:00Z",
                ),
            ),
        )
        val repository = create(transfer, shelf)

        val result = repository.enqueue(setOf("b1"))

        assertThat(result.accepted).isEqualTo(0)
        assertThat(repository.tasks().first()).isEmpty()
        Unit
    }

    @Test
    fun `missing resource fails and retry can recover`() = runBlocking {
        val content = object : ContentSource {
            var missing = true
            override suspend fun resolve(bookId: String): ContentSpec? =
                if (missing) null else FakeContent().resolve(bookId)
        }
        val repository = create(FakeTransfer(), content = content)

        repository.enqueue(setOf("b1"))
        val failed = repository.awaitTask { it.state == DownloadState.FAILED }
        assertThat(failed.errorMessage).contains("未找到")

        content.missing = false
        repository.retry(failed.taskId)
        repository.awaitTask { it.state == DownloadState.SUCCEEDED }
        Unit
    }

    @Test
    fun `pause stops an active transfer`() = runBlocking {
        val transfer = BlockingTransfer()
        val repository = create(transfer)

        repository.enqueue(setOf("b1"))
        transfer.started.await()
        val running = repository.awaitTask { it.state == DownloadState.DOWNLOADING }

        repository.pause(running.taskId)
        repository.awaitTask { it.state == DownloadState.PAUSED }
        Unit
    }

    @Test
    fun `suffixed upstream md5 is skipped instead of failing a valid pdf`() = runBlocking {
        val repository = create(
            FakeTransfer(),
            content = FakeContent(md5 = "724c9f067bb55729b44f1147465f7bf0-3"),
        )

        repository.enqueue(setOf("b1"))

        repository.awaitTask { it.state == DownloadState.SUCCEEDED }
        Unit
    }

    @Test
    fun `plain md5 mismatch fails verification`() = runBlocking {
        val repository = create(
            FakeTransfer(),
            content = FakeContent(md5 = "0".repeat(32)),
        )

        repository.enqueue(setOf("b1"))
        val failed = repository.awaitTask { it.state == DownloadState.FAILED }

        assertThat(failed.errorMessage).contains("完整性")
        assertThat(File(booksDir, "b1.pdf").exists()).isFalse()
        Unit
    }

    @Test
    fun `auth failure marks the task and expires the session`() = runBlocking {
        var expired = false
        val repository = create(
            FakeTransfer().apply { failure = AuthRequiredException() },
            onAuthRequired = { expired = true },
        )

        repository.enqueue(setOf("b1"))
        val task = repository.awaitTask { it.state == DownloadState.NEEDS_AUTH }

        assertThat(expired).isTrue()
        assertThat(task.errorMessage).contains("登录")
        Unit
    }

    @Test
    fun `in flight tasks are requeued after process restart`() = runBlocking {
        val store = TaskStore(File(temp.root, "tasks.json"))
        store.save(
            listOf(
                DownloadTask(
                    taskId = "t1",
                    bookId = "b1",
                    title = "教材一",
                    state = DownloadState.DOWNLOADING,
                ),
            ),
        )

        val repository = DownloadRepositoryImpl(
            content = FakeContent(),
            catalog = FakeCatalog(listOf(CatalogEntry("b1", "教材一"))),
            shelf = FakeShelfRepository(),
            transfer = FakeTransfer(),
            store = store,
            booksDir = booksDir,
            scope = scope,
        )

        repository.awaitTask { it.state == DownloadState.SUCCEEDED }
        Unit
    }
}

