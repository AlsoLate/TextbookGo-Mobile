package com.textbookgo.mobile.data.auth

import com.textbookgo.mobile.core.repository.AuthRepository
import com.textbookgo.mobile.core.repository.AuthResult
import com.textbookgo.mobile.core.session.SessionState
import com.textbookgo.mobile.core.session.SessionTokenParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 真实授权仓库：凭据来自应用内 WebView 登录或手工粘贴。
 *
 * 登录态以“本地存在可用凭据”为准；真正的可用性由目录/下载等受保护请求确认，
 * 收到 401 时调用 [expire] 清除凭据并要求重新登录。勾选“记住登录状态”时才落盘。
 */
class StoredAuthRepository(
    private val store: TokenStore,
    private val remember: () -> Boolean,
) : AuthRepository {

    private val _session = MutableStateFlow<SessionState>(SessionState.SignedOut)
    override val session: StateFlow<SessionState> = _session.asStateFlow()

    @Volatile
    private var token: String? = null

    val currentToken: String?
        get() = token

    init {
        if (remember()) {
            token = SessionTokenParser.sanitize(store.read())
            if (token != null) _session.value = SessionState.Validated
        }
    }

    override suspend fun validate(): AuthResult {
        if (token == null && remember()) {
            token = SessionTokenParser.sanitize(store.read())
        }
        return if (token != null) {
            _session.value = SessionState.Validated
            AuthResult.Success
        } else {
            _session.value = SessionState.SignedOut
            AuthResult.Failure("尚未登录，请先登录国家智慧教育平台")
        }
    }

    override suspend fun signIn(token: String): AuthResult {
        val clean = SessionTokenParser.sanitize(token)
            ?: return AuthResult.Failure("登录凭据格式不正确，请重新登录或检查粘贴内容").also {
                _session.value = SessionState.ValidationFailed("登录凭据格式不正确")
            }
        this.token = clean
        _session.value = SessionState.Validating
        if (remember()) store.write(clean) else store.clear()
        _session.value = SessionState.Validated
        return AuthResult.Success
    }

    override fun expire(reason: String) {
        token = null
        store.clear()
        _session.value = SessionState.Expired(reason)
    }

    override suspend fun setRememberSession(enabled: Boolean) {
        val current = token
        if (enabled && current != null) {
            store.write(current)
        } else if (!enabled) {
            store.clear()
        }
    }

    override suspend fun logout() {
        token = null
        store.clear()
        _session.value = SessionState.SignedOut
    }
}
