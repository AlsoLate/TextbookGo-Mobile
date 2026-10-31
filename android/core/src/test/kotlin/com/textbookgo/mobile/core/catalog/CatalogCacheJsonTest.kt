package com.textbookgo.mobile.core.catalog

import com.google.common.truth.Truth.assertThat
import com.textbookgo.mobile.core.model.CatalogEntry
import com.textbookgo.mobile.core.model.Tag
import org.junit.Test

class CatalogCacheJsonTest {

    private val entry = CatalogEntry(
        id = "b1",
        title = "数学 七年级上册",
        coverUrl = "https://c1.ykt.cbern.com.cn/t.png",
        tags = listOf(
            Tag("g", "七年级", CatalogRules.GRADE_DIMENSION),
            Tag("s", "数学", CatalogRules.SUBJECT_DIMENSION),
        ),
    )

    @Test
    fun `cache round trips the display fields`() {
        val decoded = CatalogCacheJson.decode(
            CatalogCacheJson.encode(listOf(entry), savedAt = "2026-10-31T00:00:00Z"),
        )

        assertThat(decoded).hasSize(1)
        assertThat(decoded[0].id).isEqualTo("b1")
        assertThat(decoded[0].title).isEqualTo("数学 七年级上册")
        assertThat(decoded[0].coverUrl).isEqualTo("https://c1.ykt.cbern.com.cn/t.png")
        assertThat(decoded[0].tags.map { it.name }).containsExactly("七年级", "数学").inOrder()
    }

    @Test
    fun `corrupted cache is treated as empty`() {
        assertThat(CatalogCacheJson.decode("{oops")).isEmpty()
        assertThat(CatalogCacheJson.decode("""{"version":1,"entries":"nope"}""")).isEmpty()
        assertThat(CatalogCacheJson.decode(null)).isEmpty()
    }

    @Test
    fun `unknown cache version is ignored`() {
        val raw = CatalogCacheJson.encode(listOf(entry), savedAt = "now")
            .replace("\"version\":1", "\"version\":99")
        assertThat(CatalogCacheJson.decode(raw)).isEmpty()
    }

    @Test
    fun `entries without id are dropped`() {
        val raw = CatalogCacheJson.encode(listOf(entry.copy(id = "")), savedAt = "now")
        assertThat(CatalogCacheJson.decode(raw)).isEmpty()
    }
}
