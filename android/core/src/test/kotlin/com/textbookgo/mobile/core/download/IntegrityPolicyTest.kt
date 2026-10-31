package com.textbookgo.mobile.core.download

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IntegrityPolicyTest {

    private val hex = "724c9f067bb55729b44f1147465f7bf0"

    @Test
    fun `plain 32 hex md5 is verifiable`() {
        assertThat(IntegrityPolicy.verifiableMd5(hex)).isEqualTo(hex)
        assertThat(IntegrityPolicy.verifiableMd5("  $hex  ")).isEqualTo(hex)
    }

    @Test
    fun `suffixed md5 from upstream is skipped instead of failing`() {
        assertThat(IntegrityPolicy.verifiableMd5("$hex-3")).isNull()
        assertThat(IntegrityPolicy.md5Matches("$hex-3", "ffffffffffffffffffffffffffffffff")).isTrue()
    }

    @Test
    fun `blank or malformed md5 is not enforced`() {
        assertThat(IntegrityPolicy.verifiableMd5(null)).isNull()
        assertThat(IntegrityPolicy.verifiableMd5("")).isNull()
        assertThat(IntegrityPolicy.verifiableMd5("not-a-md5")).isNull()
    }

    @Test
    fun `md5 comparison is case insensitive`() {
        assertThat(IntegrityPolicy.md5Matches(hex.uppercase(), hex)).isTrue()
        assertThat(IntegrityPolicy.md5Matches(hex, hex.reversed())).isFalse()
    }

    @Test
    fun `size is only enforced when known`() {
        assertThat(IntegrityPolicy.sizeMatches(null, 5)).isTrue()
        assertThat(IntegrityPolicy.sizeMatches(0, 5)).isTrue()
        assertThat(IntegrityPolicy.sizeMatches(5, 5)).isTrue()
        assertThat(IntegrityPolicy.sizeMatches(6, 5)).isFalse()
    }
}
