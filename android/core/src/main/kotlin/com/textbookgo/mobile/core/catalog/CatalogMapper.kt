package com.textbookgo.mobile.core.catalog

import com.textbookgo.mobile.core.model.CatalogEntry
import com.textbookgo.mobile.core.model.Tag

/**
 * 上游 DTO → 领域模型。
 *
 * 封面规则沿用旧版：优先 custom_properties.thumbnails[0]，
 * 回退 custom_properties.preview["Slide1"]。
 */
object CatalogMapper {

    fun toEntries(dtos: List<CatalogEntryDto>): List<CatalogEntry> = dtos.map(::toEntry)

    fun toEntry(dto: CatalogEntryDto): CatalogEntry = CatalogEntry(
        id = dto.id,
        title = dto.title,
        tags = dto.tagList.map { tag ->
            Tag(
                id = tag.tagId,
                name = tag.tagName,
                dimensionId = tag.tagDimensionId,
            )
        },
        coverUrl = coverUrl(dto),
    )

    fun coverUrl(dto: CatalogEntryDto): String? =
        dto.customProperties.thumbnails.firstOrNull { it.isNotBlank() }
            ?: dto.customProperties.preview["Slide1"]?.takeIf { it.isNotBlank() }
}
