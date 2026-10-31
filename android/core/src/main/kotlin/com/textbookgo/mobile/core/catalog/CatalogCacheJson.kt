package com.textbookgo.mobile.core.catalog

import com.textbookgo.mobile.core.model.CatalogEntry
import com.textbookgo.mobile.core.model.Tag
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * 目录缓存序列化：只保存展示所需的 id/标题/标签/封面，避免把上游
 * `preview` 等大字段落盘。缓存是“加速与离线可读”的副本，损坏即视为无缓存。
 */
object CatalogCacheJson {

    const val VERSION: Int = 1

    private val cacheJson = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(entries: List<CatalogEntry>, savedAt: String): String =
        CatalogCacheDocument(
            version = VERSION,
            savedAt = savedAt,
            entries = entries.map { entry ->
                CatalogCacheEntry(
                    id = entry.id,
                    title = entry.title,
                    coverUrl = entry.coverUrl,
                    tags = entry.tags.map { tag -> CatalogCacheTag(tag.id, tag.name, tag.dimensionId) },
                )
            },
        ).let { cacheJson.encodeToString(it) }

    fun decode(raw: String?): List<CatalogEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        val document = runCatching {
            cacheJson.decodeFromString<CatalogCacheDocument>(raw)
        }.getOrNull() ?: return emptyList()
        if (document.version != VERSION) return emptyList()
        return document.entries
            .asSequence()
            .filter { it.id.isNotBlank() }
            .map { entry ->
                CatalogEntry(
                    id = entry.id,
                    title = entry.title,
                    coverUrl = entry.coverUrl,
                    tags = entry.tags.map { tag -> Tag(tag.id, tag.name, tag.dimensionId) },
                )
            }
            .toList()
    }
}

@Serializable
internal data class CatalogCacheDocument(
    val version: Int = CatalogCacheJson.VERSION,
    val savedAt: String = "",
    val entries: List<CatalogCacheEntry> = emptyList(),
)

@Serializable
internal data class CatalogCacheEntry(
    val id: String = "",
    val title: String = "",
    val coverUrl: String? = null,
    val tags: List<CatalogCacheTag> = emptyList(),
)

@Serializable
internal data class CatalogCacheTag(
    val id: String = "",
    val name: String = "",
    val dimensionId: String = "",
)
