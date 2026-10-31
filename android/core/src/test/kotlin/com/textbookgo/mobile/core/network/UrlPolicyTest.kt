package com.textbookgo.mobile.core.network

import com.google.common.truth.Truth.assertThat
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertThrows
import org.junit.Test

class UrlPolicyTest {

    @Test
    fun `https public cdn hosts are allowed`() {
        listOf(
            "https://s-file-1.ykt.cbern.com.cn/a.pdf",
            "https://s-file-2.ykt.cbern.com.cn/b.pdf",
            "https://s-file-3.ykt.cbern.com.cn/c.pdf",
            "https://c1.ykt.cbern.com.cn/cover.png",
        ).forEach { assertThat(UrlPolicy.isAllowed(it.toHttpUrl())).isTrue() }
    }

    @Test
    fun `https authenticated host is allowed`() {
        assertThat(UrlPolicy.isAllowed("https://r1-ndr-private.ykt.cbern.com.cn/a.pdf".toHttpUrl())).isTrue()
    }

    @Test
    fun `plain http is rejected`() {
        assertThat(UrlPolicy.isAllowed("http://s-file-1.ykt.cbern.com.cn/a.pdf".toHttpUrl())).isFalse()
    }

    @Test
    fun `unknown hosts are rejected`() {
        assertThat(UrlPolicy.isAllowed("https://example.com/a.pdf".toHttpUrl())).isFalse()
    }

    @Test
    fun `suffix spoofing is rejected`() {
        assertThat(
            UrlPolicy.isAllowed("https://s-file-1.ykt.cbern.com.cn.evil.com/a.pdf".toHttpUrl()),
        ).isFalse()
    }

    @Test
    fun `userinfo cannot disguise the real host`() {
        assertThat(
            UrlPolicy.isAllowed("https://s-file-1.ykt.cbern.com.cn@evil.com/a.pdf".toHttpUrl()),
        ).isFalse()
    }

    @Test
    fun `token is allowed for platform data hosts and the private cdn`() {
        listOf(
            "https://r1-ndr-private.ykt.cbern.com.cn/a.pdf",
            "https://s-file-1.ykt.cbern.com.cn/a.json",
            "https://s-file-2.ykt.cbern.com.cn/a.json",
            "https://s-file-3.ykt.cbern.com.cn/a.json",
        ).forEach { assertThat(UrlPolicy.allowsToken(it.toHttpUrl())).isTrue() }
        assertThat(UrlPolicy.allowsToken("https://c1.ykt.cbern.com.cn/a.pdf".toHttpUrl()))
            .isFalse()
        assertThat(UrlPolicy.allowsToken("https://example.com/a".toHttpUrl())).isFalse()
    }

    @Test
    fun `require allowed throws for untrusted urls`() {
        assertThrows(IllegalArgumentException::class.java) {
            UrlPolicy.requireAllowed("https://evil.com/a.pdf")
        }
    }

    @Test
    fun `cdn template resolves to the private host when authenticated`() {
        assertThat(
            UrlPolicy.resolveCdnTemplate(
                "cs_path:${'$'}{ref-path}/edu_product/assets/a.pdf",
                hasToken = true,
            ),
        ).isEqualTo("https://r1-ndr-private.ykt.cbern.com.cn/edu_product/assets/a.pdf")
    }

    @Test
    fun `cdn template resolves to the public host without a token`() {
        assertThat(
            UrlPolicy.resolveCdnTemplate(
                "cs_path:${'$'}{ref-path}/edu_product/assets/a.pdf",
                hasToken = false,
            ),
        ).isEqualTo("https://c1.ykt.cbern.com.cn/edu_product/assets/a.pdf")
    }

    @Test
    fun `ref path without cs_path prefix is also resolved`() {
        assertThat(
            UrlPolicy.resolveCdnTemplate("${'$'}{ref-path}/x.pdf", hasToken = false),
        ).isEqualTo("https://c1.ykt.cbern.com.cn/x.pdf")
    }
}
