package com.textbookgo.mobile.core.download

/**
 * 资源文件候选。字段与上游 ti_items 对齐，但不依赖 JSON DTO。
 */
data class ResourceItem(
    val storage: String,
    val storages: List<String> = emptyList(),
    val fileFlag: String = "",
    val isSource: Boolean = false,
    val format: String = "",
    val md5: String = "",
    val size: Long = 0L,
)

/**
 * 资源选择优先级（与旧版 api.ParseResourceURL 一致）：
 * 1. 源文件且不是 ebook_mapping；
 * 2. PDF；
 * 3. 任意非 ebook_mapping 且有 storage 的文件。
 */
object ResourceSelector {

    private const val MAPPING_FLAG = "ebook_mapping"

    fun select(items: List<ResourceItem>): ResourceItem? {
        items.firstOrNull {
            it.isSource && it.fileFlag != MAPPING_FLAG && it.storage.isNotBlank()
        }?.let { return it }
        items.firstOrNull {
            it.fileFlag != MAPPING_FLAG &&
                it.format.equals("pdf", ignoreCase = true) &&
                it.storage.isNotBlank()
        }?.let { return it }
        return items.firstOrNull { it.fileFlag != MAPPING_FLAG && it.storage.isNotBlank() }
    }
}
