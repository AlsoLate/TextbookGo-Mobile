package com.textbookgo.mobile.core.download

import com.google.common.truth.Truth.assertThat
import com.textbookgo.mobile.core.fixtures.Fixtures
import org.junit.Test

class BatchStatsTest {

    @Test
    fun `each state is counted in its own bucket`() {
        val tasks = listOf(
            Fixtures.task("1", "a", DownloadState.QUEUED),
            Fixtures.task("2", "b", DownloadState.RESOLVING),
            Fixtures.task("3", "c", DownloadState.DOWNLOADING),
            Fixtures.task("4", "d", DownloadState.PAUSED),
            Fixtures.task("5", "e", DownloadState.WAITING_NETWORK),
            Fixtures.task("6", "f", DownloadState.SUCCEEDED),
            Fixtures.task("7", "g", DownloadState.SKIPPED),
            Fixtures.task("8", "h", DownloadState.FAILED),
            Fixtures.task("9", "i", DownloadState.CANCELLED),
            Fixtures.task("10", "j", DownloadState.NEEDS_AUTH),
        )

        val stats = BatchStatsCalculator.calculate(tasks)

        assertThat(stats.queued).isEqualTo(1)
        assertThat(stats.active).isEqualTo(2)
        assertThat(stats.paused).isEqualTo(1)
        assertThat(stats.waiting).isEqualTo(1)
        assertThat(stats.succeeded).isEqualTo(1)
        assertThat(stats.skipped).isEqualTo(1)
        assertThat(stats.failed).isEqualTo(1)
        assertThat(stats.cancelled).isEqualTo(1)
        assertThat(stats.needsAuth).isEqualTo(1)
        assertThat(stats.total).isEqualTo(10)
    }

    @Test
    fun `verifying and committing are active`() {
        val stats = BatchStatsCalculator.calculate(
            listOf(
                Fixtures.task("1", "a", DownloadState.VERIFYING),
                Fixtures.task("2", "b", DownloadState.COMMITTING),
            ),
        )

        assertThat(stats.active).isEqualTo(2)
    }

    @Test
    fun `needs auth is not counted as failed`() {
        val stats = BatchStatsCalculator.calculate(
            listOf(Fixtures.task("1", "a", DownloadState.NEEDS_AUTH)),
        )

        assertThat(stats.needsAuth).isEqualTo(1)
        assertThat(stats.failed).isEqualTo(0)
    }

    @Test
    fun `batch is unfinished while anything is queued active paused waiting or needs auth`() {
        listOf(
            DownloadState.QUEUED,
            DownloadState.DOWNLOADING,
            DownloadState.PAUSED,
            DownloadState.RETRY_WAITING,
            DownloadState.NEEDS_AUTH,
        ).forEach { state ->
            val stats = BatchStatsCalculator.calculate(listOf(Fixtures.task("1", "a", state)))
            assertThat(stats.finished).isFalse()
        }
    }

    @Test
    fun `batch finishes when every task is terminal`() {
        val stats = BatchStatsCalculator.calculate(
            listOf(
                Fixtures.task("1", "a", DownloadState.SUCCEEDED),
                Fixtures.task("2", "b", DownloadState.SKIPPED),
                Fixtures.task("3", "c", DownloadState.FAILED),
                Fixtures.task("4", "d", DownloadState.CANCELLED),
            ),
        )

        assertThat(stats.finished).isTrue()
        assertThat(stats.succeeded + stats.skipped + stats.failed + stats.cancelled).isEqualTo(4)
    }

    @Test
    fun `empty batch is finished and total is zero`() {
        val stats = BatchStatsCalculator.calculate(emptyList())

        assertThat(stats.total).isEqualTo(0)
        assertThat(stats.finished).isTrue()
    }
}
