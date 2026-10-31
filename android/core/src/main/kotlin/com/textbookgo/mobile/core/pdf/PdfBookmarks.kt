package com.textbookgo.mobile.core.pdf

/**
 * PDF 目录书签（P1 可选）。旧版通过 ebook_mapping + 目录树生成层级书签。
 *
 * PdfRenderer 只能渲染，不能写 outline；本轮只定义纯映射规则，
 * 实际写入库在后续 ADR 后引入。
 */
data class BookmarkNode(
    val id: String,
    val title: String,
    val children: List<BookmarkNode> = emptyList(),
)

data class PdfBookmark(
    val title: String,
    val page: Int,
    val level: Int,
    val children: List<PdfBookmark> = emptyList(),
)

object PdfBookmarkRules {

    /** 由目录树与 nodeId→页码 映射构建嵌套书签；缺失页码记为 0。 */
    fun build(nodes: List<BookmarkNode>, pages: Map<String, Int>): List<PdfBookmark> =
        buildAt(nodes, pages, level = 1)

    private fun buildAt(
        nodes: List<BookmarkNode>,
        pages: Map<String, Int>,
        level: Int,
    ): List<PdfBookmark> = nodes.map { node ->
        PdfBookmark(
            title = node.title,
            page = pages[node.id] ?: 0,
            level = level,
            children = buildAt(node.children, pages, level + 1),
        )
    }

    /** 展平为写入顺序，跳过 page <= 0 的节点（与旧版一致，父节点无效时整枝跳过）。 */
    fun flattenForWriting(bookmarks: List<PdfBookmark>): List<PdfBookmark> {
        val result = mutableListOf<PdfBookmark>()
        fun visit(items: List<PdfBookmark>) {
            items.forEach { bookmark ->
                if (bookmark.page > 0) {
                    result += bookmark.copy(children = emptyList())
                    visit(bookmark.children)
                }
            }
        }
        visit(bookmarks)
        return result
    }

    /** pdfcpu 行格式：level "title" page。 */
    fun formatLine(bookmark: PdfBookmark): String {
        val escapedTitle = bookmark.title.replace("\"", "\\\"")
        return "${bookmark.level} \"$escapedTitle\" ${bookmark.page}"
    }
}
