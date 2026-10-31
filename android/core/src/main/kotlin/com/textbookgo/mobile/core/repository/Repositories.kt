package com.textbookgo.mobile.core.repository

import com.textbookgo.mobile.core.catalog.CatalogRefreshResult
import com.textbookgo.mobile.core.model.Book
import com.textbookgo.mobile.core.model.CatalogFilter
import com.textbookgo.mobile.core.model.ShelfBook
import com.textbookgo.mobile.core.download.DownloadTask
import com.textbookgo.mobile.core.session.SessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 客户端唯一依赖的数据契约。UI 只依赖这些接口，由 data 层提供实现，
 * 测试与 Compose Preview 使用内存假实现。
 */
interface AuthRepository {
    val session: StateFlow<SessionState>

    /** 校验现有凭据并完成目录预检；取消时返回 [AuthResult.Cancelled]。 */
    suspend fun validate(): AuthResult

    /** 保存用户在网页登录/手工粘贴中获得的凭据，成功后会话进入 [SessionState.Validated]。 */
    suspend fun signIn(token: String): AuthResult =
        AuthResult.Failure("当前构建不支持登录")

    /** 受保护请求返回 401 时清除本地凭据，提示重新登录。 */
    fun expire(reason: String) = Unit

    suspend fun setRememberSession(enabled: Boolean)

    suspend fun logout()
}

sealed interface AuthResult {
    data object Success : AuthResult
    data object Cancelled : AuthResult
    data class Failure(val message: String) : AuthResult
}

interface CatalogRepository {
    val grades: StateFlow<List<String>>

    /** 目录加载状态，用于区分“暂无教材”与“加载失败”。 */
    val status: StateFlow<CatalogStatus>

    fun subjects(grade: String): Flow<List<String>>

    /** 指定年级 + 学科下出现过的版本，用于进一步区分同名教材。 */
    fun versions(grade: String, subject: String): Flow<List<String>>

    fun books(filter: CatalogFilter): Flow<List<Book>>

    suspend fun findBook(id: String): Book?

    /** 刷新目录；[force] 为 true 时忽略缓存新鲜度。 */
    suspend fun refresh(force: Boolean = false): CatalogRefreshResult
}

/** 目录加载状态。缓存命中时先 [Loaded]，随后刷新再更新。 */
sealed interface CatalogStatus {
    data object Idle : CatalogStatus
    data object Loading : CatalogStatus
    data class Loaded(val bookCount: Int) : CatalogStatus
    data class Failed(val message: String) : CatalogStatus
}

interface DownloadRepository {
    fun tasks(batchId: String? = null): Flow<List<DownloadTask>>

    /** 入队并返回去重后的结果；同一 bookId 不得重复创建活动任务。 */
    suspend fun enqueue(bookIds: Set<String>): EnqueueResult

    suspend fun pause(taskId: String)

    suspend fun resume(taskId: String)

    suspend fun cancel(taskId: String)

    suspend fun retry(taskId: String)

    /** 退出登录等场景：停止所有未完成任务。默认实现供内存替身使用。 */
    suspend fun cancelActive() = Unit
}

data class EnqueueResult(
    val batchId: String,
    val accepted: Int,
    val deduplicated: Int,
)

interface ShelfRepository {
    fun shelf(): Flow<List<ShelfBook>>

    /** 等待持久化书架完成首次加载；内存实现可保持默认空实现。 */
    suspend fun awaitLoaded() = Unit

    /** 下载成功后的入库；同一 bookId 覆盖旧记录。 */
    suspend fun add(book: ShelfBook)

    suspend fun delete(bookId: String): DeleteResult
}

sealed interface DeleteResult {
    data object Deleted : DeleteResult
    data class Failed(val message: String) : DeleteResult
}

enum class AppTheme {
    /** 跟随系统深浅色，默认值。 */
    SYSTEM,
    DAY,
    NIGHT,
    ;

    /** 把主题偏好落到“是否使用暗色”，系统值由界面层传入，便于纯 JVM 测试。 */
    fun isNight(systemInDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDark
        DAY -> false
        NIGHT -> true
    }
}

data class AppPreferences(
    val theme: AppTheme = AppTheme.SYSTEM,
    val rememberSession: Boolean = true,
)

interface PreferencesRepository {
    val preferences: StateFlow<AppPreferences>

    suspend fun setTheme(theme: AppTheme)

    suspend fun setRememberSession(enabled: Boolean)
}
