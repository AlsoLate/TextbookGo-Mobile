package com.textbookgo.mobile.core.download

/**
 * 文件名策略：内部文件名稳定且防路径穿越，展示名可读且不与同标题教材冲突。
 */
object FilenamePolicy {

    private val ILLEGAL = Regex("[\\\\/:*?\"<>|]")
    private val CONTROL = Regex("[\\u0000-\\u001F\\u007F]")
    private const val MAX_NAME_LENGTH = 80
    private const val FALLBACK_TITLE = "未命名教材"
    private const val FALLBACK_ID = "book"

    /** 内部主文件：仅由 bookId 决定，避免同标题覆盖。 */
    fun internalName(bookId: String): String = "${sanitizeId(bookId)}.pdf"

    /** 展示/导出文件名：清理标题 + 短 ID。 */
    fun displayName(title: String, bookId: String): String =
        "${sanitize(title)}_${shortId(bookId)}.pdf"

    /** 清理标题中的非法字符与控制字符，保留中文，限制长度。 */
    fun sanitize(title: String): String {
        val replaced = title.replace(ILLEGAL, "_").replace(CONTROL, "")
        val trimmed = replaced.trim().trimEnd('.', ' ').trim()
        return trimmed.take(MAX_NAME_LENGTH).ifBlank { FALLBACK_TITLE }
    }

    private fun sanitizeId(bookId: String): String =
        bookId.replace(ILLEGAL, "_")
            .replace(CONTROL, "")
            .trim()
            .take(MAX_NAME_LENGTH)
            .ifBlank { FALLBACK_ID }

    private fun shortId(bookId: String): String =
        sanitizeId(bookId).take(8).ifBlank { FALLBACK_ID }
}
