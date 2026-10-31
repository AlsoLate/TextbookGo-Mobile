package com.textbookgo.mobile.data

import android.content.Context
import com.textbookgo.mobile.PlatformBindings
import com.textbookgo.mobile.core.repository.AuthRepository
import com.textbookgo.mobile.core.repository.CatalogRepository
import com.textbookgo.mobile.core.repository.PreferencesRepository
import com.textbookgo.mobile.core.repository.ShelfRepository
import com.textbookgo.mobile.data.auth.StoredAuthRepository
import com.textbookgo.mobile.data.auth.TokenStore
import com.textbookgo.mobile.data.catalog.CatalogCache
import com.textbookgo.mobile.data.catalog.HttpCatalogRemote
import com.textbookgo.mobile.data.catalog.HttpCatalogRepository
import com.textbookgo.mobile.data.catalog.HttpContentSource
import com.textbookgo.mobile.data.download.ContentSource
import com.textbookgo.mobile.data.download.HttpTransfer
import com.textbookgo.mobile.data.download.Transfer
import okhttp3.OkHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 真实平台装配：登录凭据 → 目录 → 详情 → 私有 CDN 下载。
 *
 * release 与 verify 构建共用本实现；debug 使用示例数据，不包含这里的装配。
 */
class RealPlatformBindings(
    context: Context,
    shelf: ShelfRepository,
    preferences: PreferencesRepository,
) : PlatformBindings {

    private val appContext = context.applicationContext
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val authRepository = StoredAuthRepository(
        store = TokenStore(File(appContext.filesDir, "auth-token.json")),
        remember = { preferences.preferences.value.rememberSession },
    )

    private val remote = HttpCatalogRemote(
        client = client,
        tokenProvider = { authRepository.currentToken },
    )

    override val auth: AuthRepository = authRepository
    override val catalog: CatalogRepository = HttpCatalogRepository(
        remote = remote,
        shelf = shelf,
        cache = CatalogCache(File(appContext.filesDir, "catalog-cache.json")),
        scope = background,
    )
    override val content: ContentSource = HttpContentSource(remote) {
        authRepository.currentToken != null
    }
    override val transfer: Transfer = HttpTransfer(
        client = client,
        tokenProvider = { authRepository.currentToken },
    )
}
