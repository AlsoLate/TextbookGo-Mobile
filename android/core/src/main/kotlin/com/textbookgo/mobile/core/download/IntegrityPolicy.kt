package com.textbookgo.mobile.core.download

/**
 * 下载完整性校验规则。
 *
 * 上游 `ti_md5` 不总是裸 MD5：真实目录里会出现 `724c9f06...-3` 这类
 * “MD5-分片数”写法。旧版 Go 客户端从不校验 MD5，因此这里只在值确实是
 * 32 位十六进制时才比对，避免把正确文件误判为损坏；大小与 PDF 魔数始终校验。
 */
object IntegrityPolicy {

    private val HEX32 = Regex("^[0-9a-fA-F]{32}$")

    /** 可用于整文件比对的 MD5；带后缀、长度异常或空值都返回 null（跳过比对）。 */
    fun verifiableMd5(raw: String?): String? =
        raw?.trim()?.takeIf { HEX32.matches(it) }

    fun md5Matches(raw: String?, actualHex: String): Boolean {
        val expected = verifiableMd5(raw) ?: return true
        return expected.equals(actualHex, ignoreCase = true)
    }

    /** 期望大小未知（缺失或 <= 0）时不做大小约束。 */
    fun sizeMatches(expectedBytes: Long?, actualBytes: Long): Boolean =
        expectedBytes == null || expectedBytes <= 0L || expectedBytes == actualBytes
}
