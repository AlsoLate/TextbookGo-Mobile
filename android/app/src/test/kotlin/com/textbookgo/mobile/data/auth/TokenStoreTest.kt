package com.textbookgo.mobile.data.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TokenStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store() = TokenStore(File(temp.root, "auth-token.json"))

    @Test
    fun `writes and reads a token`() {
        val store = store()
        store.write("token-value-1")

        assertThat(store.read()).isEqualTo("token-value-1")
    }

    @Test
    fun `overwrites previous token`() {
        val store = store()
        store.write("first")
        store.write("second")

        assertThat(store.read()).isEqualTo("second")
    }

    @Test
    fun `clear removes the file`() {
        val store = store()
        store.write("token")
        store.clear()

        assertThat(store.read()).isNull()
        assertThat(File(temp.root, "auth-token.json").exists()).isFalse()
    }

    @Test
    fun `missing or corrupted file reads as null`() {
        val store = store()
        assertThat(store.read()).isNull()

        File(temp.root, "auth-token.json").writeText("not json")
        assertThat(store.read()).isNull()
    }

    @Test
    fun `writing creates parent directories`() {
        val file = File(temp.root, "nested/dir/auth-token.json")
        val store = TokenStore(file)
        store.write("token")

        assertThat(file.exists()).isTrue()
        assertThat(store.read()).isEqualTo("token")
    }
}
