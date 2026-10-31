package com.textbookgo.mobile.core.design

/**
 * 封面拼贴变体：与 old webui/frontend/js/ui.js 的哈希保持一致，
 * 保证同一教材在两端的几何拼贴相同。
 */
object CoverPalette {

    const val VARIANT_COUNT = 6

    /** 返回 0..5 的确定性变体。 */
    fun variant(id: String): Int {
        var hash = 0
        id.forEach { char ->
            hash = hash * 31 + char.code
        }
        return ((hash.toLong() and 0xFFFF_FFFFL) % VARIANT_COUNT).toInt()
    }
}
