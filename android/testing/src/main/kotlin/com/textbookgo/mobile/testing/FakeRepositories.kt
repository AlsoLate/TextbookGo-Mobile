package com.textbookgo.mobile.testing

import com.textbookgo.mobile.core.catalog.CatalogRefreshResult
import com.textbookgo.mobile.core.download.DownloadState
import com.textbookgo.mobile.core.download.DownloadTask
import com.textbookgo.mobile.core.model.Book
import com.textbookgo.mobile.core.model.CatalogEntry
import com.textbookgo.mobile.core.model.CatalogFilter
import com.textbookgo.mobile.core.model.ShelfBook
import com.textbookgo.mobile.core.repository.AppPreferences
import com.textbookgo.mobile.core.repository.AppTheme
import com.textbookgo.mobile.core.repository.AuthRepository
import com.textbookgo.mobile.core.repository.AuthResult
import com.textbookgo.mobile.core.repository.CatalogRepository
import com.textbookgo.mobile.core.repository.CatalogStatus
import com.textbookgo.mobile.core.repository.DeleteResult
import com.textbookgo.mobile.core.repository.DownloadRepository
import com.textbookgo.mobile.core.repository.EnqueueResult
import com.textbookgo.mobile.core.repository.PreferencesRepository
import com.textbookgo.mobile.core.repository.ShelfRepository
import com.textbookgo.mobile.core.session.SessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * 供 Compose Preview、UI 测试与离线开发使用的内存仓库。
 * 不访问网络、不写磁盘，行为与领域契约保持一致。
 */
class FakeAuthRepository(
    initialState: SessionState = SessionState.Validated,
) : AuthRepository {
    private val _session = MutableStateFlow(initialState)
    override val session: StateFlow<SessionState> = _session.asStateFlow()

    var nextResult: AuthResult = AuthResult.Success
    var lastRemember: Boolean? = null
    var lastSignedInToken: String? = null

    override suspend fun validate(): AuthResult {
        _session.value = SessionState.Validating
        _session.value = when (val result = nextResult) {
            AuthResult.Success -> SessionState.Validated
            AuthResult.Cancelled -> SessionState.SignedOut
            is AuthResult.Failure -> SessionState.ValidationFailed(result.message)
        }
        return nextResult
    }

    override suspend fun signIn(token: String): AuthResult {
        lastSignedInToken = token
        _session.value = SessionState.Validated
        return AuthResult.Success
    }

    override fun expire(reason: String) {
        _session.value = SessionState.Expired(reason)
    }

    override suspend fun setRememberSession(enabled: Boolean) {
        lastRemember = enabled
    }

    override suspend fun logout() {
        _session.value = SessionState.SignedOut
    }
}

class FakeCatalogRepository(
    private val entries: List<CatalogEntry>,
    private val downloadedIds: Set<String> = emptySet(),
) : CatalogRepository {
    private val _grades = MutableStateFlow(com.textbookgo.mobile.core.catalog.CatalogRules.grades(entries))
    override val grades: StateFlow<List<String>> = _grades.asStateFlow()

    private val _status = MutableStateFlow<CatalogStatus>(
        if (entries.isEmpty()) CatalogStatus.Idle else CatalogStatus.Loaded(entries.size),
    )
    override val status: StateFlow<CatalogStatus> = _status.asStateFlow()

    var refreshResult: CatalogRefreshResult = CatalogRefreshResult.Complete(1, entries.size)
        set(value) {
            field = value
            _status.value = when (value) {
                is CatalogRefreshResult.Complete -> CatalogStatus.Loaded(value.bookCount)
                is CatalogRefreshResult.Partial ->
                    CatalogStatus.Failed("部分分片加载失败（${value.loadedShards}/${value.expectedShards}）")
                is CatalogRefreshResult.Failed -> CatalogStatus.Failed("目录加载失败")
            }
        }

    override fun books(filter: CatalogFilter): Flow<List<Book>> =
        MutableStateFlow(
            com.textbookgo.mobile.core.catalog.CatalogRules
                .books(entries, filter.grade, filter.subject, downloadedIds, filter.version)
                .filter { com.textbookgo.mobile.core.catalog.CatalogRules.titleMatches(it.title, filter.query) },
        )

    override fun subjects(grade: String): Flow<List<String>> =
        MutableStateFlow(com.textbookgo.mobile.core.catalog.CatalogRules.subjects(entries, grade))

    override fun versions(grade: String, subject: String): Flow<List<String>> =
        MutableStateFlow(com.textbookgo.mobile.core.catalog.CatalogRules.versions(entries, grade, subject))

    override suspend fun refresh(force: Boolean): CatalogRefreshResult = refreshResult

    override suspend fun findBook(id: String): Book? =
        entries.firstOrNull { it.id == id }?.let { entry ->
            Book(
                id = entry.id,
                title = entry.title,
                coverUrl = entry.coverUrl,
                downloaded = entry.id in downloadedIds,
            )
        }
}

class FakeDownloadRepository : DownloadRepository {
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    private val batchIds = MutableStateFlow<Map<String, String>>(emptyMap())

    override fun tasks(batchId: String?): Flow<List<DownloadTask>> =
        if (batchId == null) _tasks else _tasks.map { list -> list.filter { batchIds.value[it.taskId] == batchId } }

    override suspend fun enqueue(bookIds: Set<String>): EnqueueResult {
        val existing = _tasks.value.map { it.bookId }.toSet()
        val newIds = bookIds - existing
        val batchId = UUID.randomUUID().toString()
        val newTasks = newIds.map { bookId ->
            DownloadTask(
                taskId = UUID.randomUUID().toString(),
                bookId = bookId,
                title = "教材-$bookId",
                state = DownloadState.QUEUED,
            )
        }
        _tasks.update { it + newTasks }
        batchIds.update { it + newTasks.associate { task -> task.taskId to batchId } }
        return EnqueueResult(batchId = batchId, accepted = newTasks.size, deduplicated = bookIds.size - newTasks.size)
    }

    override suspend fun pause(taskId: String) = move(taskId, DownloadState.PAUSED)

    override suspend fun resume(taskId: String) = move(taskId, DownloadState.QUEUED)

    override suspend fun cancel(taskId: String) = move(taskId, DownloadState.CANCELLED)

    override suspend fun retry(taskId: String) = move(taskId, DownloadState.QUEUED)

    private fun move(taskId: String, state: DownloadState) {
        _tasks.update { list ->
            list.map { task -> if (task.taskId == taskId) task.copy(state = state) else task }
        }
    }
}

class FakeShelfRepository(
    initial: List<ShelfBook> = emptyList(),
) : ShelfRepository {
    private val _shelf = MutableStateFlow(initial)
    override fun shelf(): Flow<List<ShelfBook>> = _shelf

    override suspend fun delete(bookId: String): DeleteResult {
        _shelf.update { books -> books.filterNot { it.id == bookId } }
        return DeleteResult.Deleted
    }

    override suspend fun add(book: ShelfBook) {
        _shelf.update { books -> books.filterNot { it.id == book.id } + book }
    }
}

class FakePreferencesRepository(
    initial: AppPreferences = AppPreferences(),
) : PreferencesRepository {
    private val _preferences = MutableStateFlow(initial)
    override val preferences: StateFlow<AppPreferences> = _preferences.asStateFlow()

    override suspend fun setTheme(theme: AppTheme) {
        _preferences.update { it.copy(theme = theme) }
    }

    override suspend fun setRememberSession(enabled: Boolean) {
        _preferences.update { it.copy(rememberSession = enabled) }
    }
}
