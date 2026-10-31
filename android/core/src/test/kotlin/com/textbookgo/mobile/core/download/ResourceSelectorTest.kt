package com.textbookgo.mobile.core.download

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ResourceSelectorTest {

    @Test
    fun `source file wins over pdf`() {
        val selected = ResourceSelector.select(
            listOf(
                ResourceItem(storage = "cs_path:pdf", format = "pdf"),
                ResourceItem(storage = "cs_path:source", isSource = true, format = "zip"),
            ),
        )

        assertThat(selected?.storage).isEqualTo("cs_path:source")
    }

    @Test
    fun `ebook mapping is never selected as source`() {
        val selected = ResourceSelector.select(
            listOf(
                ResourceItem(storage = "cs_path:mapping", isSource = true, fileFlag = "ebook_mapping"),
                ResourceItem(storage = "cs_path:pdf", format = "pdf"),
            ),
        )

        assertThat(selected?.storage).isEqualTo("cs_path:pdf")
    }

    @Test
    fun `pdf is the fallback when no source file exists`() {
        val selected = ResourceSelector.select(
            listOf(
                ResourceItem(storage = "cs_path:a", format = "txt"),
                ResourceItem(storage = "cs_path:b", format = "PDF"),
            ),
        )

        assertThat(selected?.storage).isEqualTo("cs_path:b")
    }

    @Test
    fun `mapping format is never chosen as pdf fallback`() {
        val selected = ResourceSelector.select(
            listOf(
                ResourceItem(storage = "cs_path:mapping", format = "pdf", fileFlag = "ebook_mapping"),
                ResourceItem(storage = "cs_path:other", format = "bin"),
            ),
        )

        assertThat(selected?.storage).isEqualTo("cs_path:other")
    }

    @Test
    fun `any non mapping file with storage is the last resort`() {
        val selected = ResourceSelector.select(
            listOf(ResourceItem(storage = "cs_path:x", format = "bin")),
        )

        assertThat(selected?.storage).isEqualTo("cs_path:x")
    }

    @Test
    fun `files without storage are ignored`() {
        val selected = ResourceSelector.select(
            listOf(
                ResourceItem(storage = "", format = "pdf"),
                ResourceItem(storage = "  ", format = "pdf"),
            ),
        )

        assertThat(selected).isNull()
    }

    @Test
    fun `empty list has no selection`() {
        assertThat(ResourceSelector.select(emptyList())).isNull()
    }
}
