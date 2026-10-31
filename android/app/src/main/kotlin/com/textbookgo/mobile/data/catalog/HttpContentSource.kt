package com.textbookgo.mobile.data.catalog

import com.textbookgo.mobile.core.download.ResourceItem
import com.textbookgo.mobile.core.download.ResourceSelector
import com.textbookgo.mobile.core.network.UrlPolicy
import com.textbookgo.mobile.data.download.ContentSource
import com.textbookgo.mobile.data.download.ContentSpec

/** 从资源详情解析 PDF 候选；主链接按模板解析，备用取 ti_storages 第一项。 */
class HttpContentSource(
    private val remote: HttpCatalogRemote,
    private val hasToken: () -> Boolean,
) : ContentSource {

    override suspend fun resolve(bookId: String): ContentSpec? {
        val detail = remote.resource(bookId)
        val items = detail.tiItems.map { item ->
            ResourceItem(
                storage = item.tiStorage,
                storages = item.tiStorages,
                fileFlag = item.tiFileFlag,
                isSource = item.tiIsSourceFile,
                format = item.tiFormat,
                md5 = item.tiMd5,
                size = item.tiSize,
            )
        }
        val selected = ResourceSelector.select(items) ?: return null
        val primary = UrlPolicy.resolveCdnTemplate(selected.storage, hasToken())
        val backup = selected.storages.firstOrNull { it.isNotBlank() }

        return ContentSpec(
            title = detail.title.ifBlank { bookId },
            urls = listOfNotNull(primary.takeIf { it.isNotBlank() }, backup),
            size = selected.size.takeIf { it > 0 },
            md5 = selected.md5.takeIf { it.isNotBlank() },
        )
    }
}
