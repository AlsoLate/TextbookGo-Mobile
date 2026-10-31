package com.textbookgo.mobile.core.catalog

import com.google.common.truth.Truth.assertThat
import com.textbookgo.mobile.core.fixtures.Fixtures
import kotlinx.serialization.SerializationException
import org.junit.Test

class CatalogJsonTest {

    @Test
    fun `urls are comma separated trimmed and blank filtered`() {
        assertThat(CatalogJson.splitUrls(" a , ,b, ")).containsExactly("a", "b").inOrder()
        assertThat(CatalogJson.splitUrls("")).isEmpty()
    }

    @Test
    fun `data version keeps long version and unknown fields are ignored`() {
        val dto = CatalogJson.parseDataVersion(Fixtures.read("data_version.json"))

        assertThat(dto.module).isEqualTo("tch_material")
        assertThat(dto.moduleVersion).isEqualTo(20261031001L)
        assertThat(CatalogJson.splitUrls(dto.urls))
            .containsExactly(
                "https://s-file-1.ykt.cbern.com.cn/a.json",
                "https://s-file-2.ykt.cbern.com.cn/b.json",
            )
    }

    @Test
    fun `wrong type for critical field fails parsing`() {
        val raw = """{"module_version":"not-a-number"}"""

        org.junit.Assert.assertThrows(SerializationException::class.java) {
            CatalogJson.parseDataVersion(raw)
        }
    }

    @Test
    fun `invalid json fails parsing`() {
        org.junit.Assert.assertThrows(SerializationException::class.java) {
            CatalogJson.parseEntries("{not json}")
        }
    }

    @Test
    fun `entries parse tags and custom properties`() {
        val entries = CatalogJson.parseEntries(Fixtures.read("catalog_entries.json"))
        val math = entries.first { it.id == "book-math-7a" }

        assertThat(math.title).isEqualTo("数学 七年级上册")
        assertThat(math.tagList.map { it.tagDimensionId })
            .containsExactly("zxxnj", "zxxxk", "zxxbb", "zxxcc")
        assertThat(math.customProperties.preview["Slide1"])
            .isEqualTo("https://c1.ykt.cbern.com.cn/preview/1.png")
    }

    @Test
    fun `optional fields fall back to defaults`() {
        val entries = CatalogJson.parseEntries("""[{"id":"x","title":"t"}]""")

        assertThat(entries.single().tagList).isEmpty()
        assertThat(entries.single().customProperties.thumbnails).isEmpty()
    }

    @Test
    fun `resource parses string array storages and long size`() {
        val resource = CatalogJson.parseResource(Fixtures.read("resource_detail.json"))
        val source = resource.tiItems.first { it.tiFileFlag == "source" }

        assertThat(source.tiIsSourceFile).isTrue()
        assertThat(source.tiStorages).hasSize(1)
        assertThat(source.tiSize).isEqualTo(104857600L)
        assertThat(source.tiMd5).isEqualTo("0123456789abcdef0123456789abcdef")
    }

    @Test
    fun `storages must be an array not an object`() {
        val raw = """
            {"ti_items":[{"ti_storages":{"url":"https://example.com/a.pdf"}}]}
        """.trimIndent()

        org.junit.Assert.assertThrows(SerializationException::class.java) {
            CatalogJson.parseResource(raw)
        }
    }
}
