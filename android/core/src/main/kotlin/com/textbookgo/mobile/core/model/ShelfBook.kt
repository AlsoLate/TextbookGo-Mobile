package com.textbookgo.mobile.core.model

/** 书架条目：只描述已成功下载并入库的教材。 */
data class ShelfBook(
    val id: String,
    val title: String,
    val filename: String,
    val size: Long,
    val downloadedAt: String,
    val coverUrl: String? = null,
    val availability: ShelfAvailability = ShelfAvailability.AVAILABLE,
    val edition2022: Boolean = false,
)

enum class ShelfAvailability {
    AVAILABLE,
    MISSING,
    PERMISSION_LOST,
}
