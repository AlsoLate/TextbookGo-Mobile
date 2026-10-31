package com.textbookgo.mobile.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 外部地址白名单与凭据边界。
 *
 * 只允许 HTTPS 的已知教材 CDN 主机；鉴权头只能发往私有下载主机。
 */
object UrlPolicy {

    val PUBLIC_HOSTS: Set<String> = setOf(
        "s-file-1.ykt.cbern.com.cn",
        "s-file-2.ykt.cbern.com.cn",
        "s-file-3.ykt.cbern.com.cn",
        "c1.ykt.cbern.com.cn",
    )

    val AUTHENTICATED_HOSTS: Set<String> = setOf(
        "r1-ndr-private.ykt.cbern.com.cn",
        "r2-ndr-private.ykt.cbern.com.cn",
        "r3-ndr-private.ykt.cbern.com.cn",
    )

    /**
     * 允许携带凭据的主机：私有 CDN + 平台 s-file 数据接口。
     *
     * 旧版 Go 客户端对所有平台请求都附带 `X-ND-AUTH`；受限教材的详情接口
     * 也可能依赖登录态，因此这里保留同样的行为，但仍限制在已知平台域名内。
     */
    val TOKEN_HOSTS: Set<String> = AUTHENTICATED_HOSTS + setOf(
        "s-file-1.ykt.cbern.com.cn",
        "s-file-2.ykt.cbern.com.cn",
        "s-file-3.ykt.cbern.com.cn",
    )

    fun isAllowed(url: HttpUrl): Boolean =
        url.isHttps && (url.host in PUBLIC_HOSTS || url.host in AUTHENTICATED_HOSTS)

    fun requireAllowed(raw: String): HttpUrl {
        val url = raw.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("invalid URL: $raw")
        require(isAllowed(url)) { "untrusted host: ${url.host}" }
        return url
    }

    fun allowsToken(url: HttpUrl): Boolean = url.isHttps && url.host in TOKEN_HOSTS

    /** 旧版 ti_storage 模板替换：cs_path:${ref-path} / ${ref-path} → CDN 根。 */
    fun resolveCdnTemplate(template: String, hasToken: Boolean): String {
        val cdn = if (hasToken) {
            "https://r1-ndr-private.ykt.cbern.com.cn"
        } else {
            "https://c1.ykt.cbern.com.cn"
        }
        return template
            .replace("cs_path:\${ref-path}", cdn)
            .replace("\${ref-path}", cdn)
    }
}
