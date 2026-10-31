package com.textbookgo.mobile.data.catalog

import com.textbookgo.mobile.core.catalog.CatalogEntryDto
import com.textbookgo.mobile.core.catalog.CatalogFailure
import com.textbookgo.mobile.core.catalog.CatalogMapper
import com.textbookgo.mobile.core.catalog.CatalogRefreshPolicy
import com.textbookgo.mobile.core.catalog.CatalogRefreshResult
import com.textbookgo.mobile.core.catalog.CatalogRules
import com.textbookgo.mobile.core.catalog.CatalogJson
import com.textbookgo.mobile.core.model.Book
import com.textbookgo.mobile.core.model.CatalogEntry
import com.textbookgo.mobile.core.model.CatalogFilter
import com.textbookgo.mobile.core.repository.CatalogRepository
import com.textbookgo.mobile.core.repository.CatalogStatus
import com.textbookgo.mobile.core.repository.ShelfRepository
import com.textbookgo.mobile.data.download.AuthRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * 真实目录仓库：并发拉取分片，完整分片才替换缓存；失败时保留上一代目录并暴露状态。
 * 启动即从 [CatalogCache] 恢复，因此断网也能浏览已缓存目录。
 */
class HttpCatalogRepository(
    private val remote: HttpCatalogRemote,
    private val shelf: ShelfRepository,
    private val cache: CatalogCache? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxParallelShards: Int = 2,
    scope: CoroutineScope? = null,
) : CatalogRepository {

    private val entries = MutableStateFlow<List<CatalogEntry>>(emptyList())
    private val _grades = MutableStateFlow<List<String>>(emptyList())
    override val grades: StateFlow<List<String>> = _grades.asStateFlow()

    private val _status = MutableStateFlow<CatalogStatus>(CatalogStatus.Idle)
    override val status: StateFlow<CatalogStatus> = _status.asStateFlow()

    private val cacheReady = CompletableDeferred<Unit>()

    init {
        if (cache != null) {
            if (scope == null) {
                applyCache(cache.load())
                cacheReady.complete(Unit)
            } else {
                // 缓存解析放到后台，避免 Application 启动时主线程读大 JSON。
                _status.value = CatalogStatus.Loading
                scope.launch(dispatcher) {
                    try {
                        applyCache(cache.load())
                    } finally {
                        cacheReady.complete(Unit)
                    }
                }
            }
        } else {
            cacheReady.complete(Unit)
        }
    }

    private fun applyCache(cached: List<CatalogEntry>) {
        if (cached.isEmpty()) {
            if (_status.value == CatalogStatus.Loading) _status.value = CatalogStatus.Idle
            return
        }
        if (entries.value.isNotEmpty()) return
        entries.value = cached
        _grades.value = CatalogRules.grades(cached)
        _status.value = CatalogStatus.Loaded(cached.size)
    }

    override fun books(filter: CatalogFilter): Flow<List<Book>> =
        combine(entries, shelf.shelf()) { currentEntries, shelfBooks ->
            CatalogRules.books(
                entries = currentEntries,
                grade = filter.grade,
                subject = filter.subject,
                downloadedIds = shelfBooks.map { it.id }.toSet(),
                version = filter.version,
            ).filter { CatalogRules.titleMatches(it.title, filter.query) }
        }

    override fun subjects(grade: String): Flow<List<String>> =
        entries.map { CatalogRules.subjects(it, grade) }

    override fun versions(grade: String, subject: String): Flow<List<String>> =
        entries.map { CatalogRules.versions(it, grade, subject) }

    override suspend fun findBook(id: String): Book? =
        entries.value.firstOrNull { it.id == id }?.let { entry ->
            Book(
                id = entry.id,
                title = CatalogRules.displayTitle(entry.title),
                coverUrl = entry.coverUrl,
                edition2022 = CatalogRules.is2022Edition(entry.title),
            )
        }

    override suspend fun refresh(force: Boolean): CatalogRefreshResult = withContext(dispatcher) {
        cacheReady.await()
        _status.value = CatalogStatus.Loading
        val previous = entries.value

        // 目录变化不频繁：12 小时内且缓存可用时不重复下载约 40 MB 分片，避免每次启动都拉全量。
        if (!force) {
            val savedAt = cache?.savedAtMillis()
            if (previous.isNotEmpty() && savedAt != null &&
                System.currentTimeMillis() - savedAt < CACHE_TTL_MS
            ) {
                _status.value = CatalogStatus.Loaded(previous.size)
                return@withContext CatalogRefreshResult.Complete(1, previous.size)
            }
        }

        try {
            val version = remote.dataVersion()
            val urls = CatalogJson.splitUrls(version.urls)
            if (urls.isEmpty()) {
                _status.value = CatalogStatus.Failed("平台未返回教材数据")
                return@withContext CatalogRefreshResult.Failed(CatalogFailure.NO_DATA)
            }

            val permits = Semaphore(maxParallelShards)
            val shards = coroutineScope {
                urls.map { url ->
                    async {
                        permits.withPermit { loadShard(url) }
                    }
                }.awaitAll()
            }

            val loadedEntries = shards.filterNotNull().flatMap { CatalogMapper.toEntries(it) }
            val unique = loadedEntries.distinctBy { it.id }
            val result = CatalogRefreshPolicy.evaluate(
                expectedShards = urls.size,
                loadedShards = shards.count { it != null },
                bookCount = unique.size,
                hasPreviousGeneration = previous.isNotEmpty(),
            )

            when (result) {
                is CatalogRefreshResult.Complete -> {
                    entries.value = unique
                    _grades.value = CatalogRules.grades(unique)
                    cache?.save(unique)
                    _status.value = CatalogStatus.Loaded(unique.size)
                }

                is CatalogRefreshResult.Partial -> {
                    _status.value = CatalogStatus.Failed(
                        "部分教材数据加载失败（${result.loadedShards}/${result.expectedShards}），" +
                            if (previous.isNotEmpty()) "已继续使用上次的目录" else "请检查网络后重试",
                    )
                }

                is CatalogRefreshResult.Failed -> {
                    _status.value = CatalogStatus.Failed(
                        if (previous.isNotEmpty()) "目录更新失败，已继续使用上次的目录" else "目录加载失败，请检查网络后重试",
                    )
                }
            }
            result
        } catch (auth: AuthRequiredException) {
            _status.value = CatalogStatus.Failed("登录状态已失效，请重新登录")
            throw auth
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _status.value = CatalogStatus.Failed(
                if (previous.isNotEmpty()) "目录更新失败，已继续使用上次的目录" else "目录加载失败：${error.message ?: "网络异常"}",
            )
            CatalogRefreshResult.Failed(CatalogFailure.NETWORK)
        }
    }

    /** 单个分片失败不影响其他分片；取消与鉴权失败必须向上抛。 */
    private suspend fun loadShard(url: String): List<CatalogEntryDto>? =
        try {
            remote.entries(url)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (auth: AuthRequiredException) {
            throw auth
        } catch (error: Exception) {
            null
        }

    private companion object {
        const val CACHE_TTL_MS = 12L * 60 * 60 * 1000
    }
}
