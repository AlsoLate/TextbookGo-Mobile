package com.textbookgo.mobile.core.model

/** 当前选书页的筛选条件。 */
data class CatalogFilter(
    val grade: String,
    val subject: String,
    val query: String = "",
    /** 版本（zxxbb），空串表示全部版本。 */
    val version: String = "",
)
