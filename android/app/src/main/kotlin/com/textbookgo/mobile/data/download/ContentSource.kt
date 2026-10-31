package com.textbookgo.mobile.data.download

import java.io.File

/** 一次下载所需的资源描述。 */
data class ContentSpec(
    val title: String,
    val urls: List<String>,
    val size: Long? = null,
    val md5: String? = null,
    /** 仅 debug 示例源使用；真实网络源为 null。 */
    val assetName: String? = null,
)

interface ContentSource {
    suspend fun resolve(bookId: String): ContentSpec?
}

interface Transfer {
    /**
     * 将资源写入 [target]；支持时使用 Range 续传。
     * [onProgress] 报告已落盘字节数与总字节数（未知为 null）。
     */
    suspend fun download(
        spec: ContentSpec,
        target: File,
        onProgress: (Long, Long?) -> Unit,
    )
}

class AuthRequiredException(message: String = "登录已过期，请重新登录") : Exception(message)
