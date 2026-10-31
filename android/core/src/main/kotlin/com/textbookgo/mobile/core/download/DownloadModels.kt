package com.textbookgo.mobile.core.download

/**
 * 下载任务状态。终态为 [SUCCEEDED]、[SKIPPED]、[CANCELLED]；
 * [FAILED] 表示本次尝试失败，可由用户显式重试。
 */
enum class DownloadState {
    QUEUED,
    RESOLVING,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    COMMITTING,
    SUCCEEDED,
    SKIPPED,
    FAILED,
    CANCELLED,
    NEEDS_AUTH,
    WAITING_NETWORK,
    WAITING_STORAGE,
    RETRY_WAITING,
}

/**
 * 下载任务快照。数据库/文件存储是事实源，UI 与通知只观察它。
 *
 * @param batchId 入队批次，用于任务面板按批次筛选。
 * @param downloadedBytes 已真实落盘的字节数；暂停/恢复以此为准。
 * @param totalBytes 未知长度时为 null，不得伪造 0%。
 * @param errorMessage 失败时的去敏用户提示。
 */
data class DownloadTask(
    val taskId: String,
    val bookId: String,
    val title: String,
    val state: DownloadState,
    val batchId: String = "",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val errorMessage: String? = null,
)
