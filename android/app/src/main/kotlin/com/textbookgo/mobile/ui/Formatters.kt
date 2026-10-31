package com.textbookgo.mobile.ui

import java.util.Locale

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "未知大小"
    val kb = bytes / 1024.0
    return when {
        kb < 1024 -> String.format(Locale.ROOT, "%.1f KB", kb)
        else -> String.format(Locale.ROOT, "%.1f MB", kb / 1024.0)
    }
}

fun formatDate(iso: String): String {
    if (iso.length < 10) return iso
    return iso.substring(0, 10)
}

fun downloadStateLabel(state: com.textbookgo.mobile.core.download.DownloadState): String =
    when (state) {
        com.textbookgo.mobile.core.download.DownloadState.QUEUED -> "等待中"
        com.textbookgo.mobile.core.download.DownloadState.RESOLVING -> "解析资源"
        com.textbookgo.mobile.core.download.DownloadState.DOWNLOADING -> "下载中"
        com.textbookgo.mobile.core.download.DownloadState.PAUSED -> "已暂停"
        com.textbookgo.mobile.core.download.DownloadState.VERIFYING -> "校验中"
        com.textbookgo.mobile.core.download.DownloadState.COMMITTING -> "写入书架"
        com.textbookgo.mobile.core.download.DownloadState.SUCCEEDED -> "已完成"
        com.textbookgo.mobile.core.download.DownloadState.SKIPPED -> "已跳过"
        com.textbookgo.mobile.core.download.DownloadState.FAILED -> "失败"
        com.textbookgo.mobile.core.download.DownloadState.CANCELLED -> "已取消"
        com.textbookgo.mobile.core.download.DownloadState.NEEDS_AUTH -> "需登录"
        com.textbookgo.mobile.core.download.DownloadState.WAITING_NETWORK -> "等待网络"
        com.textbookgo.mobile.core.download.DownloadState.WAITING_STORAGE -> "等待存储"
        com.textbookgo.mobile.core.download.DownloadState.RETRY_WAITING -> "等待重试"
    }
