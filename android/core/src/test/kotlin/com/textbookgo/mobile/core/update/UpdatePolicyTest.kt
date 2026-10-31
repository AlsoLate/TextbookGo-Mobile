package com.textbookgo.mobile.core.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UpdatePolicyTest {

    private val manifest = """
        {
          "versionCode": 12,
          "versionName": "1.0.1",
          "minSupportedVersionCode": 8,
          "apkUrl": "https://gitee.com/yizhixixi/TextbookGo-Mobile/releases/download/v1.0.1/TextbookGo-Mobile-1.0.1.apk",
          "sha256": "abc",
          "size": 8000000,
          "changelog": "修复问题",
          "publishedAt": "2026-11-01T10:00:00+08:00"
        }
    """.trimIndent()

    @Test
    fun `newer manifest is available and fields are parsed`() {
        val result = UpdatePolicy.check(manifest, currentVersionCode = 10)

        assertThat(result).isInstanceOf(UpdateCheck.Available::class.java)
        val available = result as UpdateCheck.Available
        assertThat(available.mandatory).isFalse()
        assertThat(available.manifest.versionName).isEqualTo("1.0.1")
        assertThat(available.manifest.size).isEqualTo(8_000_000)
    }

    @Test
    fun `current or older manifest is up to date`() {
        assertThat(UpdatePolicy.check(manifest, currentVersionCode = 12))
            .isEqualTo(UpdateCheck.UpToDate)
        assertThat(UpdatePolicy.check(manifest, currentVersionCode = 13))
            .isEqualTo(UpdateCheck.UpToDate)
    }

    @Test
    fun `below minimum supported version is mandatory`() {
        val result = UpdatePolicy.check(manifest, currentVersionCode = 7)

        assertThat(result).isInstanceOf(UpdateCheck.Available::class.java)
        assertThat((result as UpdateCheck.Available).mandatory).isTrue()
    }

    @Test
    fun `non https or foreign apk url is rejected`() {
        val http = manifest.replace("https://gitee.com", "http://gitee.com")
        val foreign = manifest.replace("https://gitee.com", "https://example.com")

        assertThat(UpdatePolicy.check(http, currentVersionCode = 1))
            .isInstanceOf(UpdateCheck.Invalid::class.java)
        assertThat(UpdatePolicy.check(foreign, currentVersionCode = 1))
            .isInstanceOf(UpdateCheck.Invalid::class.java)
    }

    @Test
    fun `malformed json is invalid`() {
        assertThat(UpdatePolicy.check("{", currentVersionCode = 1))
            .isInstanceOf(UpdateCheck.Invalid::class.java)
        assertThat(UpdatePolicy.check("""{"versionCode":0}""", currentVersionCode = 1))
            .isInstanceOf(UpdateCheck.Invalid::class.java)
    }

    @Test
    fun `allowed host requires exact or subdomain match`() {
        assertThat(UpdatePolicy.isAllowedUrl("https://gitee.com/a/b")).isTrue()
        assertThat(UpdatePolicy.isAllowedUrl("https://raw.gitee.com/a/b")).isTrue()
        assertThat(UpdatePolicy.isAllowedUrl("https://api.github.com/repos/a/b")).isTrue()
        assertThat(UpdatePolicy.isAllowedUrl("https://github.com/a/b/releases/download/v1/x.apk")).isTrue()
        assertThat(UpdatePolicy.isAllowedUrl("https://evil-gitee.com/a")).isFalse()
    }
}
