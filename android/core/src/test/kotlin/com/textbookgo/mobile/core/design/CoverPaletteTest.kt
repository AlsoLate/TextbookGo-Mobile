package com.textbookgo.mobile.core.design

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CoverPaletteTest {

    @Test
    fun `variant is deterministic`() {
        assertThat(CoverPalette.variant("book-1")).isEqualTo(CoverPalette.variant("book-1"))
    }

    @Test
    fun `variant always falls in the six palettes`() {
        listOf("", "a", "book-1", "教材", "数学 七年级上册", "🎓").forEach {
            val variant = CoverPalette.variant(it)
            assertThat(variant).isAtLeast(0)
            assertThat(variant).isLessThan(CoverPalette.VARIANT_COUNT)
        }
    }

    @Test
    fun `known vectors match the legacy javascript hash`() {
        assertThat(CoverPalette.variant("")).isEqualTo(0)
        assertThat(CoverPalette.variant("a")).isEqualTo(1)
        assertThat(CoverPalette.variant("abc")).isEqualTo(0)
        assertThat(CoverPalette.variant("book-1")).isEqualTo(5)
    }

    @Test
    fun `hash wraps as unsigned 32 bit like javascript`() {
        val id = "教材".repeat(40)
        assertThat(CoverPalette.variant(id)).isEqualTo(referenceVariant(id))
    }

    private fun referenceVariant(id: String): Int {
        var hash = 0L
        id.forEach { unit ->
            hash = (hash * 31L + unit.code) and 0xFFFF_FFFFL
        }
        return (hash % CoverPalette.VARIANT_COUNT).toInt()
    }
}
