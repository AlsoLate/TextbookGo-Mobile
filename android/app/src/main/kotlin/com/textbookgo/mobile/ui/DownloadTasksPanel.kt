package com.textbookgo.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.textbookgo.mobile.core.catalog.CatalogRules
import com.textbookgo.mobile.core.download.DownloadState
import com.textbookgo.mobile.core.download.DownloadStateMachine
import com.textbookgo.mobile.core.download.DownloadTask
import com.textbookgo.mobile.ui.theme.LocalAccents

/** 已完成 / 已取消的任务不再占用书架上的展示空间。 */
fun List<DownloadTask>.pendingTasks(): List<DownloadTask> = filter {
    it.state != DownloadState.SUCCEEDED &&
        it.state != DownloadState.SKIPPED &&
        it.state != DownloadState.CANCELLED
}

/**
 * 书架顶部的下载任务区：直接展示进度、失败原因和操作，
 * 替代原来藏在顶栏“任务”胶囊里的底部弹层。
 */
@Composable
fun DownloadTasksPanel(
    tasks: List<DownloadTask>,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tasks.isEmpty()) return
    val accents = LocalAccents.current
    val running = tasks.count {
        it.state == DownloadState.RESOLVING ||
            it.state == DownloadState.DOWNLOADING ||
            it.state == DownloadState.VERIFYING ||
            it.state == DownloadState.COMMITTING
    }
    val failed = tasks.count {
        it.state == DownloadState.FAILED || it.state == DownloadState.NEEDS_AUTH
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accents.selection.copy(alpha = 0.35f)),
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "下载任务",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (running > 0) accents.gradeContainer else accents.goldContainer,
                ) {
                    Text(
                        text = if (running > 0) "进行中 $running" else "待处理 ${tasks.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (running > 0) accents.grade else accents.gold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }
            }
            if (failed > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$failed 个任务未完成，可重试或取消。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                tasks.forEachIndexed { index, task ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        )
                    }
                    DownloadTaskRow(task, onPause, onResume, onRetry, onCancel)
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskRow(
    task: DownloadTask,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
) {
    val accents = LocalAccents.current
    val failed = task.state == DownloadState.FAILED || task.state == DownloadState.NEEDS_AUTH

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = CatalogRules.displayTitle(task.title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = downloadStateLabel(task.state),
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    failed -> MaterialTheme.colorScheme.error
                    task.state == DownloadState.PAUSED -> accents.gold
                    else -> accents.success
                },
            )
        }
        Text(
            text = buildString {
                if (task.downloadedBytes > 0) {
                    append(formatSize(task.downloadedBytes))
                    task.totalBytes?.takeIf { it > 0 }?.let { total ->
                        append(" / ")
                        append(formatSize(total))
                    }
                }
                task.errorMessage?.takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append(" · ")
                    append(it)
                }
            }.ifBlank { "等待开始" },
            style = MaterialTheme.typography.labelSmall,
            color = if (failed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )

        val total = task.totalBytes
        if (task.state == DownloadState.DOWNLOADING || task.state == DownloadState.PAUSED) {
            val progress = if (total != null && total > 0) {
                (task.downloadedBytes.toFloat() / total).coerceIn(0f, 1f)
            } else {
                null
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = if (task.state == DownloadState.PAUSED) accents.gold else accents.selection,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            if (DownloadStateMachine.canPause(task.state)) {
                TextButton(onClick = { onPause(task.taskId) }) {
                    Text("暂停", color = accents.grade)
                }
            }
            if (task.state == DownloadState.PAUSED) {
                TextButton(onClick = { onResume(task.taskId) }) {
                    Text("继续", color = accents.grade)
                }
            }
            if (failed || task.state == DownloadState.CANCELLED) {
                TextButton(onClick = { onRetry(task.taskId) }) {
                    Text("重试", color = accents.selection)
                }
            }
            if (DownloadStateMachine.canTransition(task.state, DownloadState.CANCELLED)) {
                TextButton(onClick = { onCancel(task.taskId) }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
