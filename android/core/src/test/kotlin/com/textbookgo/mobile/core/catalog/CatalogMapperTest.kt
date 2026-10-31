package com.textbookgo.mobile.core.catalog

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CatalogMapperTest {

    private fun dto(
        thumbnails: List<String> = emptyList(),
        preview: Map<String, String> = emptyMap(),
    ) = CatalogEntryDto(
        id = "book-1",
        title = "数学",
        tagList = listOf(
            TagDto(tagId = "g7", tagName = "七年级", tagDimensionId = "zxxnj"),
        ),
        customProperties = CustomPropertiesDto(thumbnails = thumbnails, preview = preview),
    )

    @Test
    fun `dto maps to domain tags`() {
        val entry = CatalogMapper.toEntry(dto())

        assertThat(entry.id).isEqualTo("book-1")
        assertThat(entry.tags.single().name).isEqualTo("七年级")
        assertThat(entry.tags.single().dimensionId).isEqualTo("zxxnj")
    }

    @Test
    fun `thumbnail has priority over preview`() {
        val entry = CatalogMapper.toEntry(
            dto(
                thumbnails = listOf("https://c1.ykt.cbern.com.cn/t.png"),
                preview = mapOf("Slide1" to "https://c1.ykt.cbern.com.cn/p.png"),
            ),
        )

        assertThat(entry.coverUrl).isEqualTo("https://c1.ykt.cbern.com.cn/t.png")
    }

    @Test
    fun `preview slide one is the fallback`() {
        val entry = CatalogMapper.toEntry(
            dto(preview = mapOf("Slide1" to "https://c1.ykt.cbern.com.cn/p.png")),
        )

        assertThat(entry.coverUrl).isEqualTo("https://c1.ykt.cbern.com.cn/p.png")
    }

    @Test
    fun `blank thumbnails fall back to preview`() {
        val entry = CatalogMapper.toEntry(
            dto(
                thumbnails = listOf("  "),
                preview = mapOf("Slide1" to "https://c1.ykt.cbern.com.cn/p.png"),
            ),
        )

        assertThat(entry.coverUrl).isEqualTo("https://c1.ykt.cbern.com.cn/p.png")
    }

    @Test
    fun `no cover data maps to null`() {
        assertThat(CatalogMapper.toEntry(dto()).coverUrl).isNull()
    }
}
