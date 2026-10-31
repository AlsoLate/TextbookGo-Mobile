package com.textbookgo.mobile.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 工具链自检：确认 JVM 测试、Truth 断言与测试资源加载在无 Android SDK 时也可用。
 */
class ScaffoldSmokeTest {

    @Test
    fun `jvm test toolchain runs`() {
        assertThat(1 + 1).isEqualTo(2)
    }

    @Test
    fun `test fixture resources are readable`() {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/smoke.txt")) {
            "fixtures/smoke.txt missing"
        }
        val text = stream.bufferedReader().use { it.readText() }.trim()
        assertThat(text).isEqualTo("textbookgo")
    }
}
