package com.textbookgo.mobile.core.pdf

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PdfBookmarkRulesTest {

    private val tree = listOf(
        BookmarkNode(
            id = "unit-1",
            title = "第一单元",
            children = listOf(
                BookmarkNode("lesson-1", "第1课"),
                BookmarkNode("lesson-2", "第2课"),
            ),
        ),
        BookmarkNode("unit-2", "第二单元"),
    )

    @Test
    fun `nested tree becomes nested bookmarks with levels`() {
        val bookmarks = PdfBookmarkRules.build(
            nodes = tree,
            pages = mapOf("unit-1" to 1, "lesson-1" to 2, "lesson-2" to 5, "unit-2" to 8),
        )

        assertThat(bookmarks.map { it.title }).containsExactly("第一单元", "第二单元").inOrder()
        assertThat(bookmarks[0].level).isEqualTo(1)
        assertThat(bookmarks[0].children.map { it.level }).containsExactly(2, 2)
        assertThat(bookmarks[0].children[1].page).isEqualTo(5)
    }

    @Test
    fun `missing page is recorded as zero`() {
        val bookmarks = PdfBookmarkRules.build(tree, pages = mapOf("unit-1" to 1))

        assertThat(bookmarks[0].children[0].page).isEqualTo(0)
        assertThat(bookmarks[1].page).isEqualTo(0)
    }

    @Test
    fun `flatten skips nodes without a valid page`() {
        val bookmarks = PdfBookmarkRules.build(
            tree,
            pages = mapOf("unit-1" to 1, "lesson-1" to 0, "lesson-2" to 5, "unit-2" to 8),
        )

        val flattened = PdfBookmarkRules.flattenForWriting(bookmarks)

        assertThat(flattened.map { it.title })
            .containsExactly("第一单元", "第2课", "第二单元")
            .inOrder()
    }

    @Test
    fun `pdfcpu line escapes quotes`() {
        val line = PdfBookmarkRules.formatLine(
            PdfBookmark(title = "第一章 \"引言\"", page = 3, level = 1),
        )

        assertThat(line).isEqualTo("1 \"第一章 \\\"引言\\\"\" 3")
    }

    @Test
    fun `empty tree produces no bookmarks`() {
        assertThat(PdfBookmarkRules.build(emptyList(), emptyMap())).isEmpty()
        assertThat(PdfBookmarkRules.flattenForWriting(emptyList())).isEmpty()
    }
}
