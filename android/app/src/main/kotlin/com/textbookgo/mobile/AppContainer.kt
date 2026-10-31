package com.textbookgo.mobile

import android.app.Application
import android.content.Context
import com.textbookgo.mobile.core.repository.AuthRepository
import com.textbookgo.mobile.core.repository.CatalogRepository
import com.textbookgo.mobile.core.repository.DownloadRepository
import com.textbookgo.mobile.core.repository.PreferencesRepository
import com.textbookgo.mobile.core.repository.ShelfRepository
import com.textbookgo.mobile.data.download.DownloadRepositoryImpl
import com.textbookgo.mobile.data.prefs.DataStorePreferencesRepository
import com.textbookgo.mobile.data.reader.ReaderProgressStore
import com.textbookgo.mobile.data.shelf.FileShelfRepository
import com.textbookgo.mobile.data.store.ShelfStore
import com.textbookgo.mobile.data.store.TaskStore
import com.textbookgo.mobile.data.update.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** 手工装配；依赖均通过构造注入，便于替换为测试/真实实现。 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 应用级后台作用域，供仓库做缓存加载等非阻塞初始化。 */
    val appScope: CoroutineScope
        get() = scope

    val booksDir: File = File(appContext.filesDir, "books").apply { mkdirs() }

    val preferences: PreferencesRepository = DataStorePreferencesRepository(appContext)

    /** 应用内阅读器的页码进度与夜读偏好。 */
    val readerProgress: ReaderProgressStore = ReaderProgressStore(appContext)

    val shelf: ShelfRepository = FileShelfRepository(
        store = ShelfStore(File(appContext.filesDir, "shelf.json")),
        booksDir = booksDir,
    )

    private val bindings: PlatformBindings = Bindings.create(appContext, shelf, preferences)

    val auth: AuthRepository = bindings.auth
    val catalog: CatalogRepository = bindings.catalog

    val download: DownloadRepository = DownloadRepositoryImpl(
        content = bindings.content,
        catalog = catalog,
        shelf = shelf,
        transfer = bindings.transfer,
        store = TaskStore(File(appContext.filesDir, "tasks.json")),
        booksDir = booksDir,
        scope = scope,
        onAuthRequired = { auth.expire("登录状态已失效，请重新登录") },
    )

    /** 更新检查独立于平台数据源，只读 Gitee 上的 latest.json；失败不影响主流程。 */
    val update: UpdateRepository = UpdateRepository(
        client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build(),
        manifestUrls = listOf(
            BuildConfig.UPDATE_MANIFEST_URL,
            BuildConfig.UPDATE_MANIFEST_FALLBACK_URL,
        ),
        currentVersionCode = BuildConfig.VERSION_CODE,
        preferences = appContext.getSharedPreferences("update-check", Context.MODE_PRIVATE),
    )
}

class TextbookGoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
