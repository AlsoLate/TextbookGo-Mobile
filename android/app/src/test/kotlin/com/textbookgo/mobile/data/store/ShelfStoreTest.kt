package com.textbookgo.mobile.data.store

import com.google.common.truth.Truth.assertThat
import com.textbookgo.mobile.core.model.ShelfAvailability
import com.textbookgo.mobile.core.model.ShelfBook
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ShelfStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `shelf round trips`() = runTest {
        val store = ShelfStore(File(temp.root, "shelf.json"))
        store.save(
            listOf(
                ShelfBook(
                    id = "b1",
                    title = "教材一",
                    filename = "b1.pdf",
                    size = 1024,
                    downloadedAt = "2026-10-31T00:00:00Z",
                    coverUrl = "https://r2-ndr.ykt.cbern.com.cn/cover.jpg",
                    availability = ShelfAvailability.AVAILABLE,
                ),
            ),
        )

        val restored = ShelfStore(File(temp.root, "shelf.json")).load()

        assertThat(restored).hasSize(1)
        assertThat(restored.single().title).isEqualTo("教材一")
        assertThat(restored.single().coverUrl).isEqualTo("https://r2-ndr.ykt.cbern.com.cn/cover.jpg")
        assertThat(restored.single().availability).isEqualTo(ShelfAvailability.AVAILABLE)
    }

    @Test
    fun `old shelf files without cover still load`() = runTest {
        File(temp.root, "shelf.json").writeText(
            """
            {"books":[{"id":"b1","title":"旧教材","filename":"b1.pdf","size":1,
            "downloadedAt":"2026-10-31T00:00:00Z","availability":"AVAILABLE"}]}
            """.trimIndent(),
        )

        val restored = ShelfStore(File(temp.root, "shelf.json")).load()

        assertThat(restored.single().coverUrl).isNull()
        assertThat(restored.single().title).isEqualTo("旧教材")
    }

    @Test
    fun `missing file yields empty shelf`() = runTest {
        assertThat(ShelfStore(File(temp.root, "missing.json")).load()).isEmpty()
    }
}
