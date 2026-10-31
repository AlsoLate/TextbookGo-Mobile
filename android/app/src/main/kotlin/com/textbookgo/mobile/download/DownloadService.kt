package com.textbookgo.mobile.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.textbookgo.mobile.MainActivity
import com.textbookgo.mobile.R
import com.textbookgo.mobile.TextbookGoApplication
import com.textbookgo.mobile.core.download.DownloadState
import com.textbookgo.mobile.core.download.DownloadTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 下载前台服务：只负责“保活 + 通知”，调度仍由 [DownloadRepositoryImpl] 完成。
 *
 * 应用退到后台、锁屏或最近任务被划掉时，前台服务保证进程不被轻易回收，从而
 * 让续传与重试继续；没有活动任务时自动停止，不常驻。
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private lateinit var notifications: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notifications = getSystemService(NotificationManager::class.java)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(emptyList()))
        val container = (application as TextbookGoApplication).container

        observer?.cancel()
        observer = scope.launch {
            container.download.tasks().collectLatest { tasks ->
                val active = tasks.filter { it.state in ACTIVE_STATES }
                if (active.isEmpty()) {
                    ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collectLatest
                }
                notifications.notify(NOTIFICATION_ID, buildNotification(tasks))
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    override fun onDestroy() {
        observer?.cancel()
        super.onDestroy()
    }

    private fun buildNotification(tasks: List<DownloadTask>): Notification {
        val active = tasks.filter { it.state in ACTIVE_STATES }
        val failed = tasks.count { it.state == DownloadState.FAILED || it.state == DownloadState.NEEDS_AUTH }
        val finished = tasks.count { it.state == DownloadState.SUCCEEDED }
        val doneBytes = active.sumOf { it.downloadedBytes }
        val totalBytes = active.mapNotNull { it.totalBytes }.takeIf { it.size == active.size }?.sum()

        val title = when {
            active.isEmpty() -> "准备下载"
            active.size == 1 -> "正在下载《${active.first().title}》"
            else -> "正在下载 ${active.size} 本教材"
        }
        val detail = buildString {
            append("已完成 $finished")
            if (totalBytes != null && totalBytes > 0) {
                append(" · ")
                append((doneBytes * 100 / totalBytes).coerceIn(0, 100))
                append("%")
            }
            if (failed > 0) {
                append(" · 失败 $failed")
            }
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(detail)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setProgress(
                100,
                if (totalBytes != null && totalBytes > 0) (doneBytes * 100 / totalBytes).toInt() else 0,
                totalBytes == null || totalBytes <= 0,
            )
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "教材下载",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "显示正在进行的教材下载进度"
            setShowBadge(false)
        }
        notifications.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "textbookgo-downloads"
        private const val NOTIFICATION_ID = 1001

        private val ACTIVE_STATES = setOf(
            DownloadState.QUEUED,
            DownloadState.RESOLVING,
            DownloadState.DOWNLOADING,
            DownloadState.VERIFYING,
            DownloadState.COMMITTING,
            DownloadState.PAUSED,
            DownloadState.WAITING_NETWORK,
            DownloadState.WAITING_STORAGE,
            DownloadState.RETRY_WAITING,
        )

        /** 必须在应用处于前台时调用（用户点击下载/应用启动时）。 */
        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DownloadService::class.java))
        }
    }
}
