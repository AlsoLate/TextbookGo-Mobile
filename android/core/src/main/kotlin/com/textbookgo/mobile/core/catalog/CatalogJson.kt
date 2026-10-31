package com.textbookgo.mobile.core.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * 上游 JSON 解析。字段以旧版 Go struct tag 为准：
 * - urls 是逗号分隔字符串，不是数组；
 * - ti_storages 是字符串数组；
 * - 允许未知字段，但关键字段类型错误必须暴露为解析失败。
 */
object CatalogJson {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        explicitNulls = false
        encodeDefaults = false
    }

    fun splitUrls(raw: String): List<String> =
        raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun parseDataVersion(raw: String): DataVersionDto =
        json.decodeFromString<DataVersionDto>(raw)

    fun parseEntries(raw: String): List<CatalogEntryDto> =
        json.decodeFromString<List<CatalogEntryDto>>(raw)

    fun parseResource(raw: String): ResourceDto =
        json.decodeFromString<ResourceDto>(raw)
}

@Serializable
data class DataVersionDto(
    val module: String = "",
    @SerialName("module_version") val moduleVersion: Long = 0L,
    val urls: String = "",
)

@Serializable
data class CatalogEntryDto(
    val id: String = "",
    val title: String = "",
    @SerialName("tag_list") val tagList: List<TagDto> = emptyList(),
    @SerialName("custom_properties") val customProperties: CustomPropertiesDto = CustomPropertiesDto(),
)

@Serializable
data class TagDto(
    @SerialName("tag_id") val tagId: String = "",
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("tag_dimension_id") val tagDimensionId: String = "",
)

@Serializable
data class CustomPropertiesDto(
    val thumbnails: List<String> = emptyList(),
    val preview: Map<String, String> = emptyMap(),
)

@Serializable
data class ResourceDto(
    val id: String = "",
    val title: String = "",
    @SerialName("ti_items") val tiItems: List<TiItemDto> = emptyList(),
)

@Serializable
data class TiItemDto(
    @SerialName("ti_storage") val tiStorage: String = "",
    @SerialName("ti_storages") val tiStorages: List<String> = emptyList(),
    @SerialName("ti_file_flag") val tiFileFlag: String = "",
    @SerialName("ti_is_source_file") val tiIsSourceFile: Boolean = false,
    @SerialName("ti_format") val tiFormat: String = "",
    @SerialName("ti_md5") val tiMd5: String = "",
    @SerialName("ti_size") val tiSize: Long = 0L,
)
