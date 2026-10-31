package com.textbookgo.mobile.core.download

/**
 * 下载状态机约束。
 *
 * 关键约定：
 * - Verifying/Committing 是文件事务关键区，不允许即时暂停；
 * - Paused 是用户意图，只有显式 resume 才回到 Queued；
 * - Failed/Cancelled 不作为“全部成功”，重试是显式动作。
 */
object DownloadStateMachine {

    private val transitions: Map<DownloadState, Set<DownloadState>> = mapOf(
        DownloadState.QUEUED to setOf(
            DownloadState.RESOLVING,
            DownloadState.PAUSED,
            DownloadState.SKIPPED,
            DownloadState.CANCELLED,
            DownloadState.NEEDS_AUTH,
            DownloadState.WAITING_NETWORK,
            DownloadState.WAITING_STORAGE,
            DownloadState.RETRY_WAITING,
        ),
        DownloadState.RESOLVING to setOf(
            DownloadState.DOWNLOADING,
            DownloadState.PAUSED,
            DownloadState.FAILED,
            DownloadState.CANCELLED,
            DownloadState.NEEDS_AUTH,
            DownloadState.WAITING_NETWORK,
            DownloadState.WAITING_STORAGE,
            DownloadState.RETRY_WAITING,
        ),
        DownloadState.DOWNLOADING to setOf(
            DownloadState.VERIFYING,
            DownloadState.PAUSED,
            DownloadState.FAILED,
            DownloadState.CANCELLED,
            DownloadState.NEEDS_AUTH,
            DownloadState.WAITING_NETWORK,
            DownloadState.WAITING_STORAGE,
            DownloadState.RETRY_WAITING,
        ),
        DownloadState.PAUSED to setOf(
            DownloadState.QUEUED,
            DownloadState.CANCELLED,
        ),
        DownloadState.VERIFYING to setOf(
            DownloadState.COMMITTING,
            DownloadState.FAILED,
            DownloadState.CANCELLED,
        ),
        DownloadState.COMMITTING to setOf(
            DownloadState.SUCCEEDED,
            DownloadState.FAILED,
            DownloadState.CANCELLED,
        ),
        DownloadState.FAILED to setOf(DownloadState.QUEUED),
        DownloadState.CANCELLED to setOf(DownloadState.QUEUED),
        DownloadState.NEEDS_AUTH to setOf(
            DownloadState.QUEUED,
            DownloadState.CANCELLED,
        ),
        DownloadState.WAITING_NETWORK to setOf(
            DownloadState.QUEUED,
            DownloadState.CANCELLED,
        ),
        DownloadState.WAITING_STORAGE to setOf(
            DownloadState.QUEUED,
            DownloadState.CANCELLED,
        ),
        DownloadState.RETRY_WAITING to setOf(
            DownloadState.QUEUED,
            DownloadState.CANCELLED,
        ),
        DownloadState.SUCCEEDED to emptySet(),
        DownloadState.SKIPPED to emptySet(),
    )

    fun canTransition(from: DownloadState, to: DownloadState): Boolean =
        transitions[from]?.contains(to) == true

    fun isTerminal(state: DownloadState): Boolean = when (state) {
        DownloadState.SUCCEEDED,
        DownloadState.SKIPPED,
        DownloadState.CANCELLED,
        -> true

        else -> false
    }

    fun isActive(state: DownloadState): Boolean = when (state) {
        DownloadState.RESOLVING,
        DownloadState.DOWNLOADING,
        DownloadState.VERIFYING,
        DownloadState.COMMITTING,
        -> true

        else -> false
    }

    fun canPause(state: DownloadState): Boolean = when (state) {
        DownloadState.QUEUED,
        DownloadState.RESOLVING,
        DownloadState.DOWNLOADING,
        -> true

        else -> false
    }

    fun canResume(state: DownloadState): Boolean = state == DownloadState.PAUSED
}
