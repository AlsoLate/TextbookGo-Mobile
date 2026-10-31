package com.textbookgo.mobile.data.auth

import com.google.common.truth.Truth.assertThat
import com.textbookgo.mobile.core.session.SessionState
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoredAuthRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val token = "eyJhbGciOiJIUzI1NiJ9.${"x".repeat(40)}.sig"

    private fun store(): TokenStore = TokenStore(File(temp.root, "auth-token.json"))

    @Test
    fun `stored token restores a validated session when remember is on`() {
        val store = store()
        store.write(token)

        val repository = StoredAuthRepository(store) { true }

        assertThat(repository.session.value).isEqualTo(SessionState.Validated)
        assertThat(repository.currentToken).isEqualTo(token)
    }

    @Test
    fun `stored token is ignored when remember is off`() {
        val store = store()
        store.write(token)

        val repository = StoredAuthRepository(store) { false }

        assertThat(repository.session.value).isEqualTo(SessionState.SignedOut)
        assertThat(repository.currentToken).isNull()
    }

    @Test
    fun `sign in persists a plausible token only when remember is on`() = runBlocking {
        val rememberStore = store()
        val remembered = StoredAuthRepository(rememberStore) { true }
        assertThat(remembered.signIn(token)).isEqualTo(com.textbookgo.mobile.core.repository.AuthResult.Success)
        assertThat(rememberStore.read()).isEqualTo(token)

        val transientStore = TokenStore(File(temp.root, "transient.json"))
        val transient = StoredAuthRepository(transientStore) { false }
        transient.signIn(token)
        assertThat(transient.currentToken).isEqualTo(token)
        assertThat(transientStore.read()).isNull()
    }

    @Test
    fun `sign in rejects malformed tokens and keeps the user signed out`() = runBlocking {
        val repository = StoredAuthRepository(store()) { true }

        val result = repository.signIn("short")

        assertThat(result).isInstanceOf(com.textbookgo.mobile.core.repository.AuthResult.Failure::class.java)
        assertThat(repository.session.value).isInstanceOf(SessionState.ValidationFailed::class.java)
        assertThat(repository.currentToken).isNull()
    }

    @Test
    fun `expire clears the stored credential`() {
        val store = store()
        val repository = StoredAuthRepository(store) { true }
        runBlocking { repository.signIn(token) }

        repository.expire("登录状态已失效")

        assertThat(store.read()).isNull()
        assertThat(repository.currentToken).isNull()
        assertThat(repository.session.value).isInstanceOf(SessionState.Expired::class.java)
    }

    @Test
    fun `logout clears the stored credential`() = runBlocking {
        val store = store()
        val repository = StoredAuthRepository(store) { true }
        repository.signIn(token)

        repository.logout()

        assertThat(store.read()).isNull()
        assertThat(repository.session.value).isEqualTo(SessionState.SignedOut)
    }

    @Test
    fun `validate without credentials reports a failure`() = runBlocking {
        val repository = StoredAuthRepository(store()) { true }

        val result = repository.validate()

        assertThat(result).isInstanceOf(com.textbookgo.mobile.core.repository.AuthResult.Failure::class.java)
        assertThat(repository.session.value).isEqualTo(SessionState.SignedOut)
    }
}
