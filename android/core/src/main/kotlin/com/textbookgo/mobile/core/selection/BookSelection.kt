package com.textbookgo.mobile.core.selection

/**
 * 跨年级、跨学科的教材选择集合。
 *
 * 旧版把选中 ID 放在对象里，但提交时只遍历当前年级，导致“已选 N 本”和实际提交不一致。
 * 这里以不可变 Set 作为唯一事实源，计数与提交都来自同一集合。
 */
data class BookSelection(
    val ids: Set<String> = emptySet(),
) {
    fun toggle(id: String): BookSelection =
        if (id in ids) copy(ids = ids - id) else copy(ids = ids + id)

    fun clear(): BookSelection = BookSelection()

    fun contains(id: String): Boolean = id in ids

    val count: Int
        get() = ids.size

    /** 提交给下载队列的 ID：去重后的快照。 */
    fun submitIds(): Set<String> = ids
}
