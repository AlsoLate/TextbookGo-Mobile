package com.textbookgo.mobile.core.model

/** 上游目录中的标签，维度常量见 [com.textbookgo.mobile.core.catalog.CatalogRules]。 */
data class Tag(
    val id: String,
    val name: String,
    val dimensionId: String,
)

/** 教材目录条目：过滤、搜索与展示的数据来源。 */
data class CatalogEntry(
    val id: String,
    val title: String,
    val tags: List<Tag> = emptyList(),
    val coverUrl: String? = null,
)

/** 目录树：年级 → 学科 → 教材，结构对应旧版 /api/catalog。 */
data class Catalog(
    val grades: List<Grade>,
)

data class Grade(
    val name: String,
    val subjects: List<Subject>,
)

data class Subject(
    val name: String,
    val books: List<Book>,
)

/** 可展示、可选择的教材。 */
data class Book(
    val id: String,
    val title: String,
    val version: String = "",
    val volume: String = "",
    val coverUrl: String? = null,
    val downloaded: Boolean = false,
    /** 依据 2022 年版课程标准修订的教材：标题里去掉说明，改在卡片标签上展示。 */
    val edition2022: Boolean = false,
)
