package com.textbookgo.mobile.data.catalog

import com.textbookgo.mobile.core.catalog.CatalogEntryDto
import com.textbookgo.mobile.core.catalog.CatalogJson
import com.textbookgo.mobile.core.catalog.DataVersionDto
import com.textbookgo.mobile.core.catalog.ResourceDto
import com.textbookgo.mobile.core.network.UrlPolicy
import com.textbookgo.mobile.data.download.AuthRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * 上游目录 HTTP 客户端。每个 URL 先过白名单，只对鉴权主机附带凭据。
 * [validateUrl] 可在测试中替换为受控地址校验。
 */
class HttpCatalogRemote(
    private val client: OkHttpClient,
    private val tokenProvider: () -> String?,
    private val validateUrl: (String) -> HttpUrl = { UrlPolicy.requireAllowed(it) },
    private val tokenAllowed: (HttpUrl) -> Boolean = { UrlPolicy.allowsToken(it) },
    private val dataVersionUrl: String = Endpoints.DATA_VERSION,
    private val detailsUrl: (String) -> String = Endpoints::details,
) {

    suspend fun dataVersion(): DataVersionDto =
        CatalogJson.parseDataVersion(getText(dataVersionUrl))

    suspend fun entries(url: String): List<CatalogEntryDto> {
        val safe = validateUrl(url).toString()
        return CatalogJson.parseEntries(getText(safe))
    }

    suspend fun resource(contentId: String): ResourceDto =
        CatalogJson.parseResource(getText(detailsUrl(contentId)))

    private suspend fun getText(rawUrl: String): String = withContext(Dispatchers.IO) {
        val url = validateUrl(rawUrl)
        val token = tokenProvider()
        val request = Request.Builder()
            .url(url)
            .apply {
                if (!token.isNullOrBlank() && tokenAllowed(url)) {
                    header("X-ND-AUTH", """MAC id="$token",nonce="0",mac="0"""")
                }
                header("User-Agent", "TextbookGo-Android/0.1")
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 401) throw AuthRequiredException()
            if (response.code == 403) {
                // 平台对受限教材/接口返回 403：可能是登录态问题，也可能是账号没有该资源权限。
                throw IOException("平台拒绝访问（HTTP 403），请重新登录或确认账号权限")
            }
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string() ?: throw IOException("响应为空")
        }
    }
}
