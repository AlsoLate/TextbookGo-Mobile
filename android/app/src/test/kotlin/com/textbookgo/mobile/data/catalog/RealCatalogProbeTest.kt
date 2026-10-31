package com.textbookgo.mobile.data.catalog

import com.google.common.truth.Truth.assertThat
import com.textbookgo.mobile.core.catalog.CatalogJson
import com.textbookgo.mobile.core.catalog.CatalogMapper
import com.textbookgo.mobile.core.catalog.CatalogRules
import com.textbookgo.mobile.core.download.ResourceSelector
import com.textbookgo.mobile.core.download.ResourceItem
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * 真实平台探针：默认跳过，显式开启后验证线上数据结构仍与解析器匹配。
 *
 * 运行方式：
 * `./gradlew :app:testDebugUnitTest --tests "*RealCatalogProbeTest" -Dnetwork.probe=true`
 */
class RealCatalogProbeTest {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    @Test
    fun `live catalog still matches the parser contract`() {
        assumeTrue("需要 -Dnetwork.probe=true 才访问真实网络", System.getProperty("network.probe") == "true")

        val remote = HttpCatalogRemote(client = client, tokenProvider = { null })
        val version = kotlinx.coroutines.runBlocking { remote.dataVersion() }
        val urls = CatalogJson.splitUrls(version.urls)

        assertThat(urls).isNotEmpty()
        assertThat(urls.all { it.startsWith("https://") }).isTrue()

        val firstShard = kotlinx.coroutines.runBlocking { remote.entries(urls.first()) }
        val entries = CatalogMapper.toEntries(firstShard)

        assertThat(entries).isNotEmpty()
        val titled = entries.count { it.title.isNotBlank() }
        assertThat(titled.toDouble() / entries.size).isAtLeast(0.99)

        val grades = CatalogRules.grades(entries)
        assertThat(grades).isNotEmpty()
        assertThat(grades).contains("七年级")

        val sample = entries.first { it.title.isNotBlank() }
        val subjects = CatalogRules.subjects(entries, grades.first())
        assertThat(subjects).isNotEmpty()

        val detail = kotlinx.coroutines.runBlocking { remote.resource(sample.id) }
        assertThat(detail.tiItems).isNotEmpty()
        val selected = ResourceSelector.select(
            detail.tiItems.map {
                ResourceItem(
                    storage = it.tiStorage,
                    storages = it.tiStorages,
                    fileFlag = it.tiFileFlag,
                    isSource = it.tiIsSourceFile,
                    format = it.tiFormat,
                    md5 = it.tiMd5,
                    size = it.tiSize,
                )
            },
        )
        assertThat(selected).isNotNull()
        assertThat(selected!!.size).isGreaterThan(0L)
        assertThat(selected.storage).contains("cs_path:")
    }
}
