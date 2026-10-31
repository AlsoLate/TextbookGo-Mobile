package com.textbookgo.mobile.core.fixtures

import com.textbookgo.mobile.core.download.DownloadState
import com.textbookgo.mobile.core.download.DownloadTask
import com.textbookgo.mobile.core.model.CatalogEntry
import com.textbookgo.mobile.core.model.Tag

/**
 * 测试数据工厂与资源读取。测试只依赖人工构造/去敏 fixtures，
 * 不在 CI 请求真实平台或读取用户账号数据。
 */
object Fixtures {

    fun read(name: String): String {
        val stream = checkNotNull(Fixtures::class.java.classLoader?.getResourceAsStream("fixtures/$name")) {
            "fixture not found: fixtures/$name"
        }
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    fun tag(name: String, dimensionId: String, id: String = "$dimensionId-$name"): Tag =
        Tag(id = id, name = name, dimensionId = dimensionId)

    fun entry(
        id: String,
        title: String,
        grade: String? = null,
        subject: String? = null,
        version: String? = null,
        volume: String? = null,
        coverUrl: String? = null,
    ): CatalogEntry {
        val tags = buildList {
            grade?.let { add(tag(it, "zxxnj")) }
            subject?.let { add(tag(it, "zxxxk")) }
            version?.let { add(tag(it, "zxxbb")) }
            volume?.let { add(tag(it, "zxxcc")) }
        }
        return CatalogEntry(id = id, title = title, tags = tags, coverUrl = coverUrl)
    }

    fun task(
        taskId: String = "task-1",
        bookId: String = "book-1",
        state: DownloadState,
        downloadedBytes: Long = 0L,
        totalBytes: Long? = null,
    ): DownloadTask = DownloadTask(
        taskId = taskId,
        bookId = bookId,
        title = "教材-$bookId",
        state = state,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
    )
}
