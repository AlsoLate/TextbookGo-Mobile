package com.textbookgo.mobile.data.update

import android.content.SharedPreferences
import android.util.Base64
import com.textbookgo.mobile.core.update.UpdateCheck
import com.textbookgo.mobile.core.update.UpdatePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * 更新清单拉取：24 小时节流、64 KiB 上限、失败不抛出，统一映射成 [UpdateCheck]。
 * 按顺序尝试 Gitee、GitHub 两个清单地址，第一个成功的结果即采用。
 * 只读取元数据，不下载 APK；安装交给系统浏览器/安装器。
 */
class UpdateRepository(
    private val client: OkHttpClient,
    manifestUrls: List<String>,
    private val currentVersionCode: Int,
    private val preferences: SharedPreferences,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val urls = manifestUrls.filter { it.isNotBlank() }

    fun shouldCheck(force: Boolean): Boolean {
        if (urls.isEmpty()) return false
        if (force) return true
        val last = preferences.getLong(KEY_LAST_CHECK, 0L)
        return now() - last >= CHECK_INTERVAL_MILLIS
    }

    fun markChecked() {
        preferences.edit().putLong(KEY_LAST_CHECK, now()).apply()
    }

    fun skippedVersionCode(): Int = preferences.getInt(KEY_SKIPPED_VERSION, 0)

    fun skip(versionCode: Int) {
        preferences.edit().putInt(KEY_SKIPPED_VERSION, versionCode).apply()
    }

    suspend fun fetch(): UpdateCheck = withContext(Dispatchers.IO) {
        if (urls.isEmpty()) return@withContext UpdateCheck.UpToDate

        var lastFailure: UpdateCheck = UpdateCheck.Invalid("未配置更新地址")
        for (url in urls) {
            if (!UpdatePolicy.isAllowedUrl(url)) {
                lastFailure = UpdateCheck.Invalid("更新地址未使用 HTTPS 或域名不受信任")
                continue
            }
            val result = fetchOne(url)
            if (result !is UpdateCheck.Invalid) return@withContext result
            lastFailure = result
        }
        lastFailure
    }

    private fun fetchOne(url: String): UpdateCheck {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "TextbookGo-Mobile/$currentVersionCode")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return UpdateCheck.Invalid("更新服务返回 ${response.code}")
                }
                val body = response.body ?: return UpdateCheck.Invalid("更新服务没有返回内容")
                val bytes = body.byteStream().use { it.readLimited(MAX_BYTES) }
                    ?: return UpdateCheck.Invalid("更新清单超过 64 KiB")
                val raw = String(bytes, Charsets.UTF_8)
                UpdatePolicy.check(unwrapContentsApi(raw), currentVersionCode)
            }
        } catch (error: Exception) {
            UpdateCheck.Invalid("检查更新失败：${error.message ?: "网络异常"}")
        }
    }

    /**
     * Gitee/GitHub 的 contents API 返回 base64 包装的文件对象；纯 raw 清单则原样返回。
     * 使用 API 而不是 raw，是为了绕开 CDN 缓存，发版后能立刻读到新清单。
     */
    private fun unwrapContentsApi(raw: String): String = runCatching {
        val json = Json.parseToJsonElement(raw).jsonObject
        val encoding = json["encoding"]?.jsonPrimitive?.contentOrNull
        val content = json["content"]?.jsonPrimitive?.contentOrNull
        if (encoding == "base64" && content != null) {
            String(Base64.decode(content, Base64.DEFAULT), Charsets.UTF_8)
        } else {
            raw
        }
    }.getOrDefault(raw)

    private fun InputStream.readLimited(maxBytes: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private companion object {
        const val MAX_BYTES = 64 * 1024
        const val CHECK_INTERVAL_MILLIS = 24L * 60 * 60 * 1000
        const val KEY_LAST_CHECK = "last_check_at"
        const val KEY_SKIPPED_VERSION = "skipped_version_code"
    }
}
