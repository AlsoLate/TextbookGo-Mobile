package com.textbookgo.mobile.data.download

import com.textbookgo.mobile.core.download.RangeResponse
import com.textbookgo.mobile.core.download.ResumeOutcome
import com.textbookgo.mobile.core.download.ResumePolicy
import com.textbookgo.mobile.core.download.ResumeRequestPlan
import com.textbookgo.mobile.core.network.UrlPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * OkHttp 流式下载：32 KiB 缓冲，支持 206 续传、200 覆盖、416 完成判断。
 * 主链接失败时按顺序尝试备用链接；下载地址必须先过白名单。
 * 私有 CDN 需要 `X-ND-AUTH`，且只允许发往鉴权主机。
 * 禁止传输压缩，避免 Content-Length 与实际字节不一致。
 */
class HttpTransfer(
    private val client: OkHttpClient,
    private val tokenProvider: () -> String? = { null },
    private val hostAllowed: (HttpUrl) -> Boolean = { UrlPolicy.isAllowed(it) },
    private val tokenAllowed: (HttpUrl) -> Boolean = { UrlPolicy.allowsToken(it) },
    private val validateUrl: (String) -> HttpUrl = { UrlPolicy.requireAllowed(it) },
) : Transfer {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun download(
        spec: ContentSpec,
        target: File,
        onProgress: (Long, Long?) -> Unit,
    ) = withContext(Dispatchers.IO) {
        if (spec.urls.isEmpty()) throw IOException("未找到下载链接")

        var lastError: Exception? = null
        for (rawUrl in spec.urls) {
            try {
                downloadFrom(rawUrl, spec, target, onProgress)
                return@withContext
            } catch (auth: AuthRequiredException) {
                throw auth
            } catch (error: Exception) {
                lastError = error
                // 不同来源不能拼接，失败后清空临时数据，下一次从头下载。
                target.delete()
                File(target.path + ".meta").delete()
            }
        }
        throw lastError ?: IOException("所有下载链接均失败")
    }

    private suspend fun downloadFrom(
        rawUrl: String,
        spec: ContentSpec,
        target: File,
        onProgress: (Long, Long?) -> Unit,
    ) {
        val url = validateUrl(rawUrl)
        target.parentFile?.mkdirs()

        val metaFile = File(target.path + ".meta")
        val meta = runCatching {
            json.decodeFromString<TransferMeta>(metaFile.readText())
        }.getOrNull()

        val existing = if (target.exists()) target.length() else 0L
        val plan = ResumePolicy.plan(
            existingBytes = existing,
            totalBytes = meta?.total ?: spec.size,
            etag = meta?.etag,
            lastModified = meta?.lastModified,
        )

        open(url, plan).use { response ->
            // 私有 CDN 对失效/伪造凭据既可能回 401，也可能回 403；两者都需要重新登录。
            if (response.code == 401 || response.code == 403) throw AuthRequiredException()

            val etag = response.header("ETag")
            val lastModified = response.header("Last-Modified")
            val contentRange = parseContentRange(response.header("Content-Range"))
            val total = contentRange?.total
                ?: response.body?.contentLength()?.takeIf { it > 0 }
                ?: spec.size

            var append = false
            when (plan) {
                ResumeRequestPlan.FullDownload -> {
                    if (response.code != 200) {
                        throw IOException("HTTP ${response.code}")
                    }
                }

                is ResumeRequestPlan.Resume -> {
                    val outcome = ResumePolicy.evaluate(
                        plan = plan,
                        response = RangeResponse(
                            statusCode = response.code,
                            contentRangeStart = contentRange?.start,
                            contentRangeTotal = contentRange?.total,
                            etag = etag,
                            lastModified = lastModified,
                        ),
                    )
                    when (outcome) {
                        ResumeOutcome.Append -> {
                            if (total != null && contentRange?.end != total - 1) {
                                throw IOException("206 返回了不完整的分段范围")
                            }
                            append = true
                        }

                        ResumeOutcome.Restart -> append = false
                        ResumeOutcome.Complete -> {
                            onProgress(total ?: plan.offset, total)
                            return
                        }

                        is ResumeOutcome.Reject -> throw IOException(outcome.reason)
                    }
                }
            }

            if (response.code != 200 && response.code != 206) {
                throw IOException("HTTP ${response.code}")
            }

            metaFile.writeText(
                json.encodeToString(TransferMeta(etag = etag, lastModified = lastModified, total = total)),
            )

            val body = response.body ?: throw IOException("响应为空")
            val startBytes = if (append) existing else 0L

            FileOutputStream(target, append).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(32 * 1024)
                    var read = startBytes
                    var lastReport = 0L

                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        read += count

                        val now = System.currentTimeMillis()
                        if (now - lastReport >= 100) {
                            onProgress(read, total)
                            lastReport = now
                        }
                    }
                    output.flush()
                    onProgress(read, total)
                }
            }
        }
    }

    /**
     * 建立下载连接，并手动跟随重定向。
     *
     * 客户端关闭了自动重定向，避免第三方主机收到 `X-ND-AUTH`。这里只允许
     * 跳到白名单主机，且每一跳都重新判断是否附带凭据。
     */
    private fun open(url: HttpUrl, plan: ResumeRequestPlan): okhttp3.Response {
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            val request = Request.Builder()
                .url(current)
                .header("Accept-Encoding", "identity")
                .apply {
                    val token = tokenProvider()?.takeIf { it.isNotBlank() }
                    if (token != null && tokenAllowed(current)) {
                        header("X-ND-AUTH", """MAC id="$token",nonce="0",mac="0"""")
                    }
                    if (plan is ResumeRequestPlan.Resume) {
                        header("Range", "bytes=${plan.offset}-")
                        header("If-Range", plan.ifRange)
                    }
                }
                .build()

            val response = client.newCall(request).execute()
            if (response.code !in REDIRECT_CODES) return response

            val location = response.header("Location")
            response.close()
            if (location.isNullOrBlank()) throw IOException("重定向缺少 Location")
            val next = current.resolve(location) ?: throw IOException("无法解析重定向地址")
            if (!hostAllowed(next)) {
                throw IOException("下载被重定向到未授权主机：${next.host}")
            }
            current = next
        }
        throw IOException("下载重定向次数过多")
    }

    private fun parseContentRange(header: String?): ContentRange? {
        if (header == null) return null
        val match = CONTENT_RANGE.find(header) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val totalRaw = match.groupValues[3]
        return ContentRange(
            start = start,
            end = end,
            total = if (totalRaw == "*") null else totalRaw.toLongOrNull(),
        )
    }

    private data class ContentRange(val start: Long, val end: Long, val total: Long?)

    @Serializable
    private data class TransferMeta(
        val etag: String? = null,
        val lastModified: String? = null,
        val total: Long? = null,
    )

    private companion object {
        val CONTENT_RANGE = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""")
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        const val MAX_REDIRECTS = 3
    }
}
