package com.textbookgo.mobile.core.catalog

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CatalogRefreshPolicyTest {

    @Test
    fun `all shards loaded produces a complete generation`() {
        val result = CatalogRefreshPolicy.evaluate(
            expectedShards = 5,
            loadedShards = 5,
            bookCount = 120,
            hasPreviousGeneration = false,
        )

        assertThat(result).isEqualTo(CatalogRefreshResult.Complete(5, 120))
    }

    @Test
    fun `partial shard failure keeps the previous generation`() {
        val result = CatalogRefreshPolicy.evaluate(
            expectedShards = 5,
            loadedShards = 4,
            bookCount = 100,
            hasPreviousGeneration = true,
        )

        assertThat(result).isEqualTo(CatalogRefreshResult.Partial(4, 5, keptPreviousGeneration = true))
    }

    @Test
    fun `partial without a previous generation is still reported as partial`() {
        val result = CatalogRefreshPolicy.evaluate(
            expectedShards = 5,
            loadedShards = 2,
            bookCount = 20,
            hasPreviousGeneration = false,
        )

        assertThat(result).isEqualTo(CatalogRefreshResult.Partial(2, 5, keptPreviousGeneration = false))
    }

    @Test
    fun `zero loaded shards is a failure not an empty catalog`() {
        val result = CatalogRefreshPolicy.evaluate(
            expectedShards = 5,
            loadedShards = 0,
            bookCount = 0,
            hasPreviousGeneration = true,
        )

        assertThat(result).isEqualTo(CatalogRefreshResult.Failed(CatalogFailure.NO_DATA))
    }

    @Test
    fun `no declared shards is a failure`() {
        val result = CatalogRefreshPolicy.evaluate(
            expectedShards = 0,
            loadedShards = 0,
            bookCount = 0,
            hasPreviousGeneration = false,
        )

        assertThat(result).isEqualTo(CatalogRefreshResult.Failed(CatalogFailure.NO_DATA))
    }

    @Test
    fun `all shards but zero books is a failure`() {
        val result = CatalogRefreshPolicy.evaluate(
            expectedShards = 3,
            loadedShards = 3,
            bookCount = 0,
            hasPreviousGeneration = true,
        )

        assertThat(result).isEqualTo(CatalogRefreshResult.Failed(CatalogFailure.NO_DATA))
    }
}
