package com.textbookgo.mobile.core.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI

/** 远程更新清单；字段都可以缺省，解析后再做校验。 */
@Serializable
data class UpdateManifest(
    val versionCode: Int = 0,
    val versionName: String = "",
    val minSupportedVersionCode: Int = 0,
    val apkUrl: String = "",
    val sha256: String = "",
    val size: Long = 0,
    val changelog: String = "",
    val publishedAt: String = "",
)

sealed interface UpdateCheck {
    /** 当前已是最新（或还没有配置更新源）。 */
    data object UpToDate : UpdateCheck

    data class Available(
        val manifest: UpdateManifest,
        val mandatory: Boolean,
    ) : UpdateCheck

    data class Invalid(val reason: String) : UpdateCheck
}

/**
 * 更新清单的纯规则层：只做解析、版本比较和 APK 地址校验，不发起网络请求。
 */
object UpdatePolicy {

    /** Gitee、GitHub 的网页、API 与附件域名。 */
    val DEFAULT_ALLOWED_HOSTS: Set<String> = setOf(
        "gitee.com",
        "raw.gitee.com",
        "file.gitee.com",
        "api.github.com",
        "github.com",
        "raw.githubusercontent.com",
        "objects.githubusercontent.com",
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun check(
        raw: String,
        currentVersionCode: Int,
        allowedHosts: Set<String> = DEFAULT_ALLOWED_HOSTS,
    ): UpdateCheck {
        val manifest = runCatching { json.decodeFromString<UpdateManifest>(raw) }
            .getOrElse { return UpdateCheck.Invalid("更新清单格式不正确") }

        if (manifest.versionCode <= 0) return UpdateCheck.Invalid("更新清单缺少 versionCode")
        if (manifest.versionName.isBlank()) return UpdateCheck.Invalid("更新清单缺少 versionName")
        if (manifest.minSupportedVersionCode < 0) {
            return UpdateCheck.Invalid("minSupportedVersionCode 非法")
        }
        if (manifest.versionCode <= currentVersionCode) return UpdateCheck.UpToDate
        if (!isAllowedUrl(manifest.apkUrl, allowedHosts)) {
            return UpdateCheck.Invalid("APK 地址不在允许的域名内")
        }

        return UpdateCheck.Available(
            manifest = manifest.copy(
                changelog = manifest.changelog.take(2000),
                publishedAt = manifest.publishedAt.take(40),
            ),
            mandatory = currentVersionCode < manifest.minSupportedVersionCode,
        )
    }

    fun isAllowedUrl(url: String, allowedHosts: Set<String> = DEFAULT_ALLOWED_HOSTS): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        return allowedHosts.any { allowed -> host == allowed || host.endsWith(".$allowed") }
    }
}
