package com.textbookgo.mobile.data.store

import com.textbookgo.mobile.core.download.DownloadState
import com.textbookgo.mobile.core.download.DownloadTask
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class DownloadTaskSnapshot(
    val taskId: String,
    val bookId: String,
    val title: String,
    val state: String,
    val batchId: String = "",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val errorMessage: String? = null,
)

@Serializable
data class TaskSnapshot(val tasks: List<DownloadTaskSnapshot> = emptyList())

/** 任务持久化。进程重建后，活动中的任务回到 Queued，暂停任务保持 Paused。 */
class TaskStore(file: File) {

    private val store = JsonStore(file, TaskSnapshot.serializer(), TaskSnapshot())

    suspend fun load(): List<DownloadTask> =
        store.load().tasks.map { snapshot ->
            val state = runCatching { DownloadState.valueOf(snapshot.state) }
                .getOrDefault(DownloadState.QUEUED)
            val restored = when (state) {
                DownloadState.RESOLVING,
                DownloadState.DOWNLOADING,
                DownloadState.VERIFYING,
                DownloadState.COMMITTING,
                DownloadState.RETRY_WAITING,
                DownloadState.WAITING_NETWORK,
                DownloadState.WAITING_STORAGE,
                -> DownloadState.QUEUED

                else -> state
            }
            DownloadTask(
                taskId = snapshot.taskId,
                bookId = snapshot.bookId,
                title = snapshot.title,
                state = restored,
                batchId = snapshot.batchId,
                downloadedBytes = snapshot.downloadedBytes,
                totalBytes = snapshot.totalBytes,
                errorMessage = snapshot.errorMessage,
            )
        }

    suspend fun save(tasks: List<DownloadTask>) {
        store.save(
            TaskSnapshot(
                tasks = tasks.map { task ->
                    DownloadTaskSnapshot(
                        taskId = task.taskId,
                        bookId = task.bookId,
                        title = task.title,
                        state = task.state.name,
                        batchId = task.batchId,
                        downloadedBytes = task.downloadedBytes,
                        totalBytes = task.totalBytes,
                        errorMessage = task.errorMessage,
                    )
                },
            ),
        )
    }
}
