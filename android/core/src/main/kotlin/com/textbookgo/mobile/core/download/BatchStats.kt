package com.textbookgo.mobile.core.download

/** 一批下载任务的终态统计；“全部结束”不等于“全部成功”。 */
data class BatchStats(
    val queued: Int = 0,
    val active: Int = 0,
    val paused: Int = 0,
    val waiting: Int = 0,
    val succeeded: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val cancelled: Int = 0,
    val needsAuth: Int = 0,
) {
    val total: Int
        get() = queued + active + paused + waiting + succeeded + skipped + failed + cancelled + needsAuth

    /** 仅当没有排队、活动、暂停、等待或待授权任务时为 true。 */
    val finished: Boolean
        get() = queued == 0 && active == 0 && paused == 0 && waiting == 0 && needsAuth == 0
}

object BatchStatsCalculator {
    fun calculate(tasks: List<DownloadTask>): BatchStats {
        var queued = 0
        var active = 0
        var paused = 0
        var waiting = 0
        var succeeded = 0
        var skipped = 0
        var failed = 0
        var cancelled = 0
        var needsAuth = 0

        tasks.forEach { task ->
            when (task.state) {
                DownloadState.QUEUED -> queued++
                DownloadState.RESOLVING,
                DownloadState.DOWNLOADING,
                DownloadState.VERIFYING,
                DownloadState.COMMITTING,
                -> active++

                DownloadState.PAUSED -> paused++
                DownloadState.WAITING_NETWORK,
                DownloadState.WAITING_STORAGE,
                DownloadState.RETRY_WAITING,
                -> waiting++

                DownloadState.SUCCEEDED -> succeeded++
                DownloadState.SKIPPED -> skipped++
                DownloadState.FAILED -> failed++
                DownloadState.CANCELLED -> cancelled++
                DownloadState.NEEDS_AUTH -> needsAuth++
            }
        }

        return BatchStats(
            queued = queued,
            active = active,
            paused = paused,
            waiting = waiting,
            succeeded = succeeded,
            skipped = skipped,
            failed = failed,
            cancelled = cancelled,
            needsAuth = needsAuth,
        )
    }
}
