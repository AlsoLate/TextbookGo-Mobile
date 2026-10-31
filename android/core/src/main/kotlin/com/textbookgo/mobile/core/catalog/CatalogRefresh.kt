package com.textbookgo.mobile.core.catalog

/**
 * 目录刷新结果。任何分片失败都不能静默替换上一份完整目录。
 */
sealed interface CatalogRefreshResult {
    data class Complete(
        val loadedShards: Int,
        val bookCount: Int,
    ) : CatalogRefreshResult

    data class Partial(
        val loadedShards: Int,
        val expectedShards: Int,
        val keptPreviousGeneration: Boolean,
    ) : CatalogRefreshResult

    data class Failed(
        val reason: CatalogFailure,
    ) : CatalogRefreshResult
}

enum class CatalogFailure {
    NO_DATA,
    NETWORK,
    SCHEMA_CHANGED,
}

object CatalogRefreshPolicy {

    /**
     * @param expectedShards 版本文件声明的分片数。
     * @param loadedShards 本次成功解析的分片数。
     * @param bookCount 合并去重后的教材数。
     * @param hasPreviousGeneration 本地是否已有完整目录可继续使用。
     */
    fun evaluate(
        expectedShards: Int,
        loadedShards: Int,
        bookCount: Int,
        hasPreviousGeneration: Boolean,
    ): CatalogRefreshResult {
        if (expectedShards <= 0 || bookCount <= 0) {
            return CatalogRefreshResult.Failed(CatalogFailure.NO_DATA)
        }
        if (loadedShards >= expectedShards) {
            return CatalogRefreshResult.Complete(loadedShards, bookCount)
        }
        if (loadedShards <= 0) {
            return CatalogRefreshResult.Failed(CatalogFailure.NO_DATA)
        }
        return CatalogRefreshResult.Partial(
            loadedShards = loadedShards,
            expectedShards = expectedShards,
            keptPreviousGeneration = hasPreviousGeneration,
        )
    }
}
