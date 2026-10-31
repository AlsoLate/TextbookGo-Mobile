package com.textbookgo.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.textbookgo.mobile.AppContainer
import com.textbookgo.mobile.BuildConfig
import com.textbookgo.mobile.core.catalog.CatalogFailure
import com.textbookgo.mobile.core.catalog.CatalogRefreshResult
import com.textbookgo.mobile.core.model.Book
import com.textbookgo.mobile.core.model.CatalogFilter
import com.textbookgo.mobile.core.model.ShelfBook
import com.textbookgo.mobile.core.download.DownloadState
import com.textbookgo.mobile.core.download.DownloadTask
import com.textbookgo.mobile.core.repository.AppPreferences
import com.textbookgo.mobile.core.repository.AppTheme
import com.textbookgo.mobile.core.repository.AuthResult
import com.textbookgo.mobile.core.repository.CatalogStatus
import com.textbookgo.mobile.core.selection.BookSelection
import com.textbookgo.mobile.core.session.SessionState
import com.textbookgo.mobile.core.update.UpdateCheck
import com.textbookgo.mobile.core.update.UpdateManifest
import com.textbookgo.mobile.data.download.AuthRequiredException
import com.textbookgo.mobile.download.DownloadService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DownloadUiState(
    val session: SessionState = SessionState.SignedOut,
    val grades: List<String> = emptyList(),
    val grade: String = "",
    val subjects: List<String> = emptyList(),
    val subject: String = "",
    val versions: List<String> = emptyList(),
    val version: String = "",
    val query: String = "",
    val books: List<Book> = emptyList(),
    val selected: Set<String> = emptySet(),
    val busy: Boolean = false,
    val showLogin: Boolean = false,
    val message: String? = null,
)

sealed interface UiEvent {
    data object NavigateToShelf : UiEvent
    data class Toast(val message: String) : UiEvent
}

data class UpdateUiState(
    val checking: Boolean = false,
    val available: UpdateManifest? = null,
    val mandatory: Boolean = false,
)

class MainViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _ui = MutableStateFlow(DownloadUiState())
    val ui: StateFlow<DownloadUiState> = _ui.asStateFlow()

    val shelf: StateFlow<List<ShelfBook>> = container.shelf.shelf()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tasks: StateFlow<List<DownloadTask>> = container.download.tasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val preferences: StateFlow<AppPreferences> = container.preferences.preferences

    val catalogStatus: StateFlow<CatalogStatus> = container.catalog.status

    private val _update = MutableStateFlow(UpdateUiState())
    val update: StateFlow<UpdateUiState> = _update.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private val filter = MutableStateFlow(CatalogFilter(grade = "", subject = "", query = "", version = ""))
    private var booksJob: Job? = null
    private var filterSwitchJob: Job? = null
    private var refreshedForSession = false

    /** 记住每个年级上次选的学科，以及每个年级+学科上次选的版本。 */
    private val subjectMemory = mutableMapOf<String, String>()
    private val versionMemory = mutableMapOf<String, String>()

    val isDemo: Boolean = BuildConfig.DEBUG
    val storagePath: String = container.booksDir.absolutePath

    init {
        viewModelScope.launch {
            container.auth.session.collect { session ->
                _ui.update { it.copy(session = session) }
                if (session == SessionState.Validated && !refreshedForSession) {
                    refreshedForSession = true
                    refreshCatalog(announce = false)
                }
                if (session != SessionState.Validated) refreshedForSession = false
            }
        }
        viewModelScope.launch {
            container.catalog.grades.collect { grades ->
                _ui.update { it.copy(grades = grades) }
                if (grades.isNotEmpty() && filter.value.grade !in grades) {
                    selectGrade(grades.first())
                }
            }
        }
        viewModelScope.launch {
            filter.collect { current ->
                _ui.update {
                    it.copy(
                        grade = current.grade,
                        subject = current.subject,
                        version = current.version,
                        query = current.query,
                    )
                }
                booksJob?.cancel()
                booksJob = viewModelScope.launch {
                    combine(
                        container.catalog.subjects(current.grade),
                        container.catalog.versions(current.grade, current.subject),
                        container.catalog.books(current),
                    ) { subjects, versions, books -> Triple(subjects, versions, books) }
                        .collect { (subjects, versions, books) ->
                            _ui.update {
                                it.copy(
                                    subjects = subjects,
                                    versions = versions,
                                    books = books,
                                    subject = if (it.subject in subjects) it.subject else subjects.firstOrNull().orEmpty(),
                                    version = if (it.version in versions) it.version else "",
                                )
                            }
                            if (current.subject.isBlank() && subjects.isNotEmpty()) {
                                selectSubject(subjects.first())
                            }
                        }
                }
            }
        }
        viewModelScope.launch {
            // 进程重启后仍有未完成任务时恢复前台服务，避免刚打开就退后台被回收。
            val hasActive = container.download.tasks().first().any { it.state in ACTIVE_STATES }
            if (hasActive) DownloadService.start(container.appContext)
        }
        viewModelScope.launch {
            // 首帧后再静默检查更新，避免和目录加载抢网络。
            delay(3_000)
            checkForUpdates(force = false)
        }
    }

    /** 检查更新；[force] 为 true 时忽略 24 小时节流并反馈结果。 */
    fun checkForUpdates(force: Boolean = false) {
        if (_update.value.checking) return
        if (!force && !container.update.shouldCheck(false)) return
        viewModelScope.launch {
            _update.update { it.copy(checking = true) }
            val result = container.update.fetch()
            container.update.markChecked()
            when (result) {
                is UpdateCheck.Available -> {
                    val skipped = !force &&
                        container.update.skippedVersionCode() == result.manifest.versionCode
                    _update.update {
                        it.copy(
                            checking = false,
                            available = if (skipped) null else result.manifest,
                            mandatory = result.mandatory,
                        )
                    }
                }

                UpdateCheck.UpToDate -> {
                    _update.update { it.copy(checking = false) }
                    if (force) _events.emit(UiEvent.Toast("已是最新版本"))
                }

                is UpdateCheck.Invalid -> {
                    _update.update { it.copy(checking = false) }
                    if (force) _events.emit(UiEvent.Toast(result.reason))
                }
            }
        }
    }

    /** “稍后”：本次不再展示，下次检查仍会提示。 */
    fun dismissUpdate() {
        _update.update { it.copy(available = null) }
    }

    /** “跳过此版本”：记录版本号，后续自动检查不再提示。 */
    fun skipUpdateVersion() {
        _update.value.available?.let { container.update.skip(it.versionCode) }
        _update.update { it.copy(available = null) }
    }

    /** 登录按钮：示例构建直接进入，真实构建打开网页登录。 */
    fun login() {
        if (!isDemo) {
            _ui.update { it.copy(showLogin = true) }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, message = null) }
            refreshedForSession = true
            when (val result = container.auth.validate()) {
                AuthResult.Success -> {
                    _ui.update { it.copy(busy = false, message = null) }
                    refreshCatalog(announce = isDemo.not())
                }

                AuthResult.Cancelled -> {
                    _ui.update { it.copy(busy = false, message = "已取消登录") }
                }

                is AuthResult.Failure -> {
                    _ui.update { it.copy(busy = false, message = result.message) }
                }
            }
        }
    }

    /** 网页登录或手工粘贴得到的凭据。 */
    fun signIn(token: String) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, message = null) }
            refreshedForSession = true
            when (val result = container.auth.signIn(token)) {
                AuthResult.Success -> {
                    _ui.update { it.copy(busy = false, showLogin = false) }
                    refreshCatalog()
                }

                AuthResult.Cancelled -> _ui.update { it.copy(busy = false, message = "已取消登录") }

                is AuthResult.Failure -> _ui.update {
                    it.copy(busy = false, message = result.message)
                }
            }
        }
    }

    fun dismissLogin() {
        _ui.update { it.copy(showLogin = false, message = null) }
    }

    fun retryCatalog() {
        viewModelScope.launch { refreshCatalog(force = true) }
    }

    /** 设置页手动“更新目录”，忽略 12 小时缓存新鲜度。 */
    fun refreshCatalogNow() {
        viewModelScope.launch { refreshCatalog(force = true) }
    }

    fun logout() {
        viewModelScope.launch {
            container.auth.logout()
            container.download.cancelActive()
            _ui.update { it.copy(selected = emptySet(), showLogin = false) }
        }
    }

    fun selectGrade(grade: String) {
        val current = filter.value
        if (grade == current.grade) return
        // 先恢复该年级上次的学科/版本；没有记忆才沿用当前选择，最后才用第一个学科。
        filterSwitchJob?.cancel()
        filterSwitchJob = viewModelScope.launch {
            val subjects = container.catalog.subjects(grade).first()
            val subject = subjectMemory[grade]?.takeIf { it in subjects }
                ?: current.subject.takeIf { it in subjects }
                ?: subjects.firstOrNull().orEmpty()
            val versions = if (subject.isBlank()) {
                emptyList()
            } else {
                container.catalog.versions(grade, subject).first()
            }
            val remembered = versionMemory[versionKey(grade, subject)]
            val version = when {
                remembered != null ->
                    remembered.takeIf { it.isEmpty() || it in versions }.orEmpty()
                current.version.isNotBlank() && current.version in versions -> current.version
                else -> ""
            }
            filter.update { latest ->
                if (latest.grade != current.grade) return@update latest
                if (subject.isNotBlank()) subjectMemory[grade] = subject
                if (remembered == null && version.isNotBlank()) {
                    versionMemory[versionKey(grade, subject)] = version
                }
                latest.copy(grade = grade, subject = subject, version = version)
            }
        }
    }

    fun selectSubject(subject: String) {
        val current = filter.value
        if (subject == current.subject) return
        // 优先恢复该学科上次选的版本；没选过才沿用当前版本；
        // 两者在新学科都不存在时回落“全部”，但不覆盖记忆。
        filterSwitchJob?.cancel()
        filterSwitchJob = viewModelScope.launch {
            val versions = container.catalog.versions(current.grade, subject).first()
            filter.update { latest ->
                if (latest.grade != current.grade) return@update latest
                val key = versionKey(latest.grade, subject)
                val remembered = versionMemory[key]
                val preferred = when {
                    remembered != null ->
                        remembered.takeIf { it.isEmpty() || it in versions }.orEmpty()
                    latest.version.isNotBlank() && latest.version in versions -> latest.version
                    else -> ""
                }
                if (remembered == null && preferred.isNotBlank()) {
                    // 第一次进入该学科时把沿用的版本记下来，下次切回来可恢复。
                    versionMemory[key] = preferred
                }
                if (latest.grade.isNotBlank() && subject.isNotBlank()) {
                    subjectMemory[latest.grade] = subject
                }
                latest.copy(subject = subject, version = preferred)
            }
        }
    }

    fun selectVersion(version: String) {
        val current = filter.value
        if (current.grade.isNotBlank() && current.subject.isNotBlank()) {
            // 记录用户显式选择；选“全部”也记为空串，避免下次切回来又带回旧版本。
            versionMemory[versionKey(current.grade, current.subject)] = version
        }
        filter.update { it.copy(version = version) }
    }

    private fun versionKey(grade: String, subject: String): String = "$grade\u0000$subject"

    fun setQuery(query: String) {
        filter.update { it.copy(query = query) }
    }

    fun toggleBook(id: String) {
        _ui.update { state ->
            state.copy(selected = BookSelection(state.selected).toggle(id).ids)
        }
    }

    fun clearSelection() {
        _ui.update { it.copy(selected = emptySet()) }
    }

    fun startDownload() {
        val selected = _ui.value.selected
        if (selected.isEmpty()) {
            _ui.update { it.copy(message = "请先选择至少一本教材") }
            return
        }
        if (_ui.value.session != SessionState.Validated) {
            _ui.update { it.copy(message = "登录已失效，请重新登录后再下载") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            val result = container.download.enqueue(selected)
            _ui.update {
                it.copy(
                    busy = false,
                    selected = emptySet(),
                    message = if (result.accepted > 0) {
                        "已加入下载队列：${result.accepted} 本"
                    } else {
                        "所选教材已在队列或书架中"
                    },
                )
            }
            if (result.accepted > 0) {
                DownloadService.start(container.appContext)
                _events.emit(UiEvent.NavigateToShelf)
            }
        }
    }

    fun pause(taskId: String) = viewModelScope.launch { container.download.pause(taskId) }

    fun resume(taskId: String) = viewModelScope.launch { container.download.resume(taskId) }

    fun retry(taskId: String) = viewModelScope.launch {
        val task = tasks.value.firstOrNull { it.taskId == taskId }
        if (task?.state == DownloadState.NEEDS_AUTH && _ui.value.session != SessionState.Validated) {
            _ui.update { it.copy(message = "请先重新登录，再重试该任务") }
            if (!isDemo) _ui.update { it.copy(showLogin = true) }
            return@launch
        }
        container.download.retry(taskId)
    }

    fun cancel(taskId: String) = viewModelScope.launch { container.download.cancel(taskId) }

    fun deleteBook(book: ShelfBook) {
        viewModelScope.launch {
            val result = container.shelf.delete(book.id)
            _ui.update {
                it.copy(
                    message = when (result) {
                        com.textbookgo.mobile.core.repository.DeleteResult.Deleted -> "已删除《${book.title}》"
                        is com.textbookgo.mobile.core.repository.DeleteResult.Failed -> result.message
                    },
                )
            }
        }
    }

    fun setTheme(theme: AppTheme) = viewModelScope.launch { container.preferences.setTheme(theme) }

    fun setRememberSession(enabled: Boolean) = viewModelScope.launch {
        container.preferences.setRememberSession(enabled)
        container.auth.setRememberSession(enabled)
    }

    fun consumeMessage() {
        _ui.update { it.copy(message = null) }
    }

    private suspend fun refreshCatalog(force: Boolean = false, announce: Boolean = true) {
        _ui.update { it.copy(busy = true) }
        val result = try {
            container.catalog.refresh(force)
        } catch (auth: AuthRequiredException) {
            container.auth.expire("登录状态已失效，请重新登录")
            _ui.update { it.copy(busy = false, message = "登录状态已失效，请重新登录") }
            return
        } catch (error: Exception) {
            _ui.update {
                it.copy(busy = false, message = "目录加载失败：${error.message ?: "网络异常"}")
            }
            return
        }
        _ui.update { state ->
            state.copy(
                busy = false,
                message = if (announce) messageFor(result) else state.message,
            )
        }
    }

    private fun messageFor(result: CatalogRefreshResult): String? = when (result) {
        is CatalogRefreshResult.Complete -> "已加载 ${result.bookCount} 本教材"
        is CatalogRefreshResult.Partial ->
            "部分教材加载失败（${result.loadedShards}/${result.expectedShards}），已保留上次目录"
        is CatalogRefreshResult.Failed -> when (result.reason) {
            CatalogFailure.NETWORK -> "目录加载失败，请检查网络后重试"
            CatalogFailure.NO_DATA -> "平台暂未返回教材数据"
            CatalogFailure.SCHEMA_CHANGED -> "平台数据格式已变化，请更新应用"
        }
    }

    companion object {
        private val ACTIVE_STATES = setOf(
            DownloadState.QUEUED,
            DownloadState.RESOLVING,
            DownloadState.DOWNLOADING,
            DownloadState.VERIFYING,
            DownloadState.COMMITTING,
            DownloadState.PAUSED,
            DownloadState.WAITING_NETWORK,
            DownloadState.WAITING_STORAGE,
            DownloadState.RETRY_WAITING,
        )

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MainViewModel(container) as T
            }
    }
}
