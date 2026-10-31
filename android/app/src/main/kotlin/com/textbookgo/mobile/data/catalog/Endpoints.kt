package com.textbookgo.mobile.data.catalog

/** 上游地址。与旧版 internal/api/endpoints.go 对齐，只使用 HTTPS。 */
object Endpoints {
    const val BASE = "https://s-file-1.ykt.cbern.com.cn"
    const val DATA_VERSION = "$BASE/zxx/ndrs/resources/tch_material/version/data_version.json"
    const val PUBLIC_CDN = "https://c1.ykt.cbern.com.cn"
    const val PRIVATE_CDN = "https://r1-ndr-private.ykt.cbern.com.cn"

    fun details(contentId: String) =
        "$BASE/zxx/ndrv2/resources/tch_material/details/$contentId.json"
}
