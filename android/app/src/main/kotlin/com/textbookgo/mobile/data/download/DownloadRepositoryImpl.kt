package com.textbookgo.mobile.data.download

import com.textbookgo.mobile.core.catalog.CatalogRules
import com.textbookgo.mobile.core.download.DownloadState
import com.textbookgo.mobile.core.download.DownloadStateMachine
import com.textbookgo.mobile.core.download.DownloadTask
import com.textbookgo.mobile.core.download.IntegrityPolicy
import com.textbookgo.mobile.core.model.ShelfBook
import com.textbookgo.mobile.core.repository.CatalogRepository
import com.textbookgo.mobile.core.repository.DownloadRepository
import com.textbookgo.mobile.core.repository.EnqueueResult
import com.textbookgo.mobile.core.repository.ShelfRepository
import com.textbookgo.mobile.data.store.TaskStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 下载调度：全局最多 3 路、任务持久化、暂停/恢复/取消/重试、成功入库。
 */
class DownloadRepositoryImpl(
    private val content: ContentSource,
    private val catalog: CatalogRepository,
    private val shelf: ShelfRepository,
    private val transfer: Transfer,
    private val store: TaskStore,
    private val booksDir: File,
    private val scope: CoroutineScope,
    maxConcurrent: Int = 3,
    private val onAuthRequired: () -> Unit = {},
) : DownloadRepository {

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    private val jobs = ConcurrentHashMap<String, Job>()
    private val permits = Semaphore(maxConcurrent)
    private val persistLock = Mutex()

    @Volatile
    private var lastPersistAt = 0L

    private val restoreJob = scope.launch {
        val restored = store.load().map { task ->
            val part = File(booksDir, "${task.bookId}.part")
            val resumable = task.state in IN_FLIGHT
            task.copy(
                downloadedBytes = if (part.exists()) part.length() else task.downloadedBytes,
                // 进程被杀时状态停留在 DOWNLOADING 等中间态，重启后统一回到 QUEUED 重新排队，
                // 否则任务会永远卡在“下载中”。暂停/失败/取消等用户可见状态保持不变。
                state = if (resumable) DownloadState.QUEUED else task.state,
                errorMessage = if (resumable) null else task.errorMessage,
            )
        }
        _tasks.value = restored
        restored.filter { it.state == DownloadState.QUEUED }.forEach { launchTask(it.taskId) }
    }

    override fun tasks(batchId: String?): Flow<List<DownloadTask>> =
        _tasks.asStateFlow().map { tasks ->
            if (batchId.isNullOrBlank()) tasks else tasks.filter { it.batchId == batchId }
        }

    override suspend fun enqueue(bookIds: Set<String>): EnqueueResult {
        restoreJob.join()
        shelf.awaitLoaded()
        val existing = _tasks.value.map { it.bookId }.toSet()
        val alreadyOnShelf = shelf.shelf().first().map { it.id }.toSet()
        val newIds = bookIds - existing - alreadyOnShelf
        val batchId = UUID.randomUUID().toString()
        val newTasks = newIds.map { bookId ->
            DownloadTask(
                taskId = UUID.randomUUID().toString(),
                bookId = bookId,
                // 入队时目录已加载，先记下标题；任务恢复时即使目录尚未刷新也能显示真实书名。
                title = catalog.findBook(bookId)?.title ?: bookId,
                state = DownloadState.QUEUED,
                batchId = batchId,
            )
        }
        _tasks.update { it + newTasks }
        persist(force = true)
        newTasks.forEach { launchTask(it.taskId) }
        return EnqueueResult(
            batchId = batchId,
            accepted = newTasks.size,
            deduplicated = bookIds.size - newTasks.size,
        )
    }

    override suspend fun pause(taskId: String) {
        val task = current(taskId) ?: return
        if (!DownloadStateMachine.canPause(task.state)) return
        update(taskId) { it.copy(state = DownloadState.PAUSED) }
        jobs[taskId]?.cancel()
        persist(force = true)
    }

    override suspend fun resume(taskId: String) {
        val task = current(taskId) ?: return
        if (!DownloadStateMachine.canResume(task.state)) return
        jobs[taskId]?.join()
        update(taskId) { it.copy(state = DownloadState.QUEUED, errorMessage = null) }
        persist(force = true)
        launchTask(taskId)
    }

    override suspend fun cancel(taskId: String) {
        val task = current(taskId) ?: return
        if (!DownloadStateMachine.canTransition(task.state, DownloadState.CANCELLED)) return
        update(taskId) { it.copy(state = DownloadState.CANCELLED) }
        jobs[taskId]?.cancel()
        File(booksDir, "${task.bookId}.part").delete()
        File(booksDir, "${task.bookId}.part.meta").delete()
        persist(force = true)
    }

    override suspend fun retry(taskId: String) {
        val task = current(taskId) ?: return
        if (task.state !in RETRYABLE) return
        jobs[taskId]?.join()
        update(taskId) { it.copy(state = DownloadState.QUEUED, errorMessage = null) }
        persist(force = true)
        launchTask(taskId)
    }

    override suspend fun cancelActive() {
        _tasks.value
            .filter { it.state in IN_FLIGHT || it.state == DownloadState.PAUSED }
            .forEach { cancel(it.taskId) }
    }

    private fun launchTask(taskId: String) {
        if (jobs[taskId]?.isActive == true) return
        jobs[taskId] = scope.launch {
            permits.withPermit { runTask(taskId) }
        }
    }

    private suspend fun runTask(taskId: String) {
        try {
            val task = current(taskId) ?: return
            if (task.state != DownloadState.QUEUED) return

            update(taskId) { it.copy(state = DownloadState.RESOLVING, errorMessage = null) }
            val spec = content.resolve(task.bookId)
            if (spec == null) {
                fail(taskId, "未找到可下载的 PDF 资源")
                return
            }
            // 进程重启后目录可能还在加载，此时用入队时保存的标题兜底。
            val known = catalog.findBook(task.bookId)
            val rawTitle = spec.title.ifBlank { known?.title ?: task.title }
            val title = CatalogRules.displayTitle(rawTitle)
            val edition2022 = known?.edition2022 == true || CatalogRules.is2022Edition(rawTitle)

            update(taskId) {
                it.copy(
                    title = title,
                    state = DownloadState.DOWNLOADING,
                    totalBytes = spec.size,
                )
            }

            val temp = File(booksDir, "${task.bookId}.part")
            val target = File(booksDir, "${task.bookId}.pdf")

            transfer.download(spec, temp) { read, total ->
                update(taskId) {
                    it.copy(downloadedBytes = read, totalBytes = total ?: it.totalBytes)
                }
            }

            update(taskId) { it.copy(state = DownloadState.VERIFYING) }
            verify(temp, spec)

            update(taskId) { it.copy(state = DownloadState.COMMITTING) }
            target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }

            val size = target.length()
            shelf.add(
                ShelfBook(
                    id = task.bookId,
                    title = title,
                    filename = target.name,
                    size = size,
                    downloadedAt = Instant.now().toString(),
                    coverUrl = known?.coverUrl,
                    edition2022 = edition2022,
                ),
            )
            update(taskId) {
                it.copy(
                    state = DownloadState.SUCCEEDED,
                    downloadedBytes = size,
                    totalBytes = size,
                    errorMessage = null,
                )
            }
        } catch (cancelled: CancellationException) {
            val state = current(taskId)?.state
            if (state != DownloadState.PAUSED && state != DownloadState.CANCELLED) {
                update(taskId) {
                    it.copy(state = DownloadState.FAILED, errorMessage = "下载已中断")
                }
            }
            throw cancelled
        } catch (auth: AuthRequiredException) {
            update(taskId) {
                it.copy(state = DownloadState.NEEDS_AUTH, errorMessage = auth.message)
            }
            onAuthRequired()
        } catch (error: Exception) {
            fail(taskId, error.message ?: "下载失败")
        } finally {
            persist(force = true)
            jobs.remove(taskId)
        }
    }

    private fun verify(file: File, spec: ContentSpec) {
        if (!file.exists() || file.length() == 0L) {
            throw IOException("下载文件为空")
        }
        val header = ByteArray(4)
        val read = file.inputStream().use { it.read(header) }
        if (read < 4 || String(header, Charsets.US_ASCII) != "%PDF") {
            throw IOException("文件内容不是 PDF")
        }
        spec.size?.takeIf { it > 0 }?.let { expected ->
            if (!IntegrityPolicy.sizeMatches(expected, file.length())) {
                throw IOException("文件大小校验失败")
            }
        }
        spec.md5?.takeIf { it.isNotBlank() }?.let { expected ->
            if (!IntegrityPolicy.md5Matches(expected, md5(file))) {
                throw IOException("文件完整性校验失败")
            }
        }
    }

    private fun md5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun fail(taskId: String, message: String) {
        update(taskId) {
            it.copy(state = DownloadState.FAILED, errorMessage = message)
        }
        scope.launch { persist(force = true) }
    }

    private fun current(taskId: String): DownloadTask? =
        _tasks.value.firstOrNull { it.taskId == taskId }

    private fun update(taskId: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.update { tasks ->
            tasks.map { task -> if (task.taskId == taskId) transform(task) else task }
        }
    }

    private suspend fun persist(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPersistAt < 500) return
        persistLock.withLock {
            if (!force && System.currentTimeMillis() - lastPersistAt < 500) return@withLock
            store.save(_tasks.value)
            lastPersistAt = System.currentTimeMillis()
        }
    }

    private companion object {
        val RETRYABLE = setOf(
            DownloadState.FAILED,
            DownloadState.CANCELLED,
            DownloadState.NEEDS_AUTH,
        )

        /** 进程重启后应重新排队的中间状态。 */
        val IN_FLIGHT = setOf(
            DownloadState.QUEUED,
            DownloadState.RESOLVING,
            DownloadState.DOWNLOADING,
            DownloadState.VERIFYING,
            DownloadState.COMMITTING,
            DownloadState.WAITING_NETWORK,
            DownloadState.WAITING_STORAGE,
            DownloadState.RETRY_WAITING,
        )
    }
}



