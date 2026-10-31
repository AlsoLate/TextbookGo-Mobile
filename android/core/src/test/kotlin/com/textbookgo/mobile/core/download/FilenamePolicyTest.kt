package com.textbookgo.mobile.core.download

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FilenamePolicyTest {

    @Test
    fun `internal name depends only on book id`() {
        assertThat(FilenamePolicy.internalName("book-1")).isEqualTo("book-1.pdf")
    }

    @Test
    fun `internal name cannot contain path separators`() {
        assertThat(FilenamePolicy.internalName("a/b\\c:d")).isEqualTo("a_b_c_d.pdf")
    }

    @Test
    fun `empty book id has a safe fallback`() {
        assertThat(FilenamePolicy.internalName("")).isEqualTo("book.pdf")
    }

    @Test
    fun `display name appends a short id to keep same titles distinct`() {
        val first = FilenamePolicy.displayName("数学 七年级上册", "abcdef1234567890")
        val second = FilenamePolicy.displayName("数学 七年级上册", "ffffffff99999999")

        assertThat(first).isEqualTo("数学 七年级上册_abcdef12.pdf")
        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `sanitize replaces cross platform illegal characters`() {
        assertThat(FilenamePolicy.sanitize("a\\b/c:d*e?f\"g<h>i|j"))
            .isEqualTo("a_b_c_d_e_f_g_h_i_j")
    }

    @Test
    fun `sanitize keeps chinese and removes control characters`() {
        assertThat(FilenamePolicy.sanitize("数学\u0000七年级\u001f上册"))
            .isEqualTo("数学七年级上册")
    }

    @Test
    fun `sanitize trims trailing dots and spaces`() {
        assertThat(FilenamePolicy.sanitize("  教材...   ")).isEqualTo("教材")
    }

    @Test
    fun `blank title falls back to a readable default`() {
        assertThat(FilenamePolicy.sanitize("   ")).isEqualTo("未命名教材")
    }

    @Test
    fun `long titles are capped without breaking the extension`() {
        val sanitized = FilenamePolicy.sanitize("教".repeat(300))

        assertThat(sanitized.length).isAtMost(80)
        assertThat(FilenamePolicy.displayName("教".repeat(300), "abcdef123456")).endsWith(".pdf")
    }

    @Test
    fun `path traversal cannot escape the output directory`() {
        val name = FilenamePolicy.sanitize("..\\..\\etc\\passwd")
        val display = FilenamePolicy.displayName("..\\..\\etc\\passwd", "book-1")

        assertThat(name).doesNotContain("/")
        assertThat(name).doesNotContain("\\")
        assertThat(display).doesNotContain("/")
        assertThat(display).doesNotContain("\\")
    }
}
