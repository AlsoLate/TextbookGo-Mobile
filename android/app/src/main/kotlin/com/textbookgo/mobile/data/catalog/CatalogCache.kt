package com.textbookgo.mobile.data.catalog

import com.textbookgo.mobile.core.catalog.CatalogCacheJson
import com.textbookgo.mobile.core.model.CatalogEntry
import java.io.File
import java.time.Instant

/**
 * 目录缓存：刷新成功后覆盖写入，启动时先读缓存。
 *
 * 缓存只含展示字段，体积远小于上游分片；损坏、版本不符或读取失败都按“无缓存”
 * 处理，不阻塞刷新。缓存属于公共目录数据，不包含凭据，退出登录不清除。
 */
class CatalogCache(private val file: File) {

    fun load(): List<CatalogEntry> {
        if (!file.exists()) return emptyList()
        val raw = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        return CatalogCacheJson.decode(raw)
    }

    /** 缓存最近一次成功写入的时间（毫秒），用于判断是否需要重新拉取。 */
    fun savedAtMillis(): Long? = file.lastModified().takeIf { it > 0L }

    fun save(entries: List<CatalogEntry>) {
        if (entries.isEmpty()) return
        val parent = file.parentFile ?: return
        parent.mkdirs()
        val temporary = File(parent, "${file.name}.tmp")
        val payload = CatalogCacheJson.encode(entries, Instant.now().toString())
        temporary.writeText(payload)
        if (!temporary.renameTo(file)) {
            file.writeText(payload)
            temporary.delete()
        }
    }
}
