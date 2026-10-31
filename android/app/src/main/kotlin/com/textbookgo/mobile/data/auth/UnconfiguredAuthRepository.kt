package com.textbookgo.mobile.data.auth

import com.textbookgo.mobile.core.repository.AuthRepository
import com.textbookgo.mobile.core.repository.AuthResult
import com.textbookgo.mobile.core.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Release 默认实现：授权提供方尚未接入。UI 会明确显示该限制，不假装登录成功。
 */
class UnconfiguredAuthRepository : AuthRepository {

    private val _session = MutableStateFlow<SessionState>(SessionState.SignedOut)
    override val session: StateFlow<SessionState> = _session.asStateFlow()

    override suspend fun validate(): AuthResult {
        val message = "授权提供方尚未接入，当前版本无法登录平台"
        _session.value = SessionState.ValidationFailed(message)
        return AuthResult.Failure(message)
    }

    override suspend fun setRememberSession(enabled: Boolean) = Unit

    override suspend fun logout() {
        _session.value = SessionState.SignedOut
    }
}
