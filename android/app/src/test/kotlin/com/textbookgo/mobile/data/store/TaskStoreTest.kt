package com.textbookgo.mobile.data.store

import com.google.common.truth.Truth.assertThat
import com.textbookgo.mobile.core.download.DownloadState
import com.textbookgo.mobile.core.download.DownloadTask
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TaskStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `active tasks are restored as queued and paused stay paused`() = runTest {
        val store = TaskStore(File(temp.root, "tasks.json"))
        store.save(
            listOf(
                DownloadTask("1", "b1", "教材一", DownloadState.DOWNLOADING, batchId = "batch", downloadedBytes = 12, totalBytes = 100),
                DownloadTask("2", "b2", "教材二", DownloadState.PAUSED, downloadedBytes = 5),
            ),
        )

        val restored = TaskStore(File(temp.root, "tasks.json")).load()

        assertThat(restored[0].state).isEqualTo(DownloadState.QUEUED)
        assertThat(restored[0].downloadedBytes).isEqualTo(12)
        assertThat(restored[0].batchId).isEqualTo("batch")
        assertThat(restored[1].state).isEqualTo(DownloadState.PAUSED)
    }

    @Test
    fun `missing or corrupt file yields empty tasks`() = runTest {
        assertThat(TaskStore(File(temp.root, "missing.json")).load()).isEmpty()

        val corrupt = File(temp.root, "corrupt.json").apply { writeText("{not json") }
        assertThat(TaskStore(corrupt).load()).isEmpty()
    }
}
