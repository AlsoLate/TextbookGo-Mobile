package com.textbookgo.mobile.core.session

/**
 * 会话状态。本地存在凭据字符串不等于已登录，只有 [Validated] 才能发起受保护请求。
 */
sealed interface SessionState {
    data object SignedOut : SessionState
    data object Authorizing : SessionState
    data object Validating : SessionState
    data object Validated : SessionState
    data class Expired(val reason: String? = null) : SessionState
    data class ValidationFailed(val reason: String) : SessionState
}

object SessionPolicy {
    fun canUseProtectedRequests(state: SessionState): Boolean = state == SessionState.Validated

    fun requiresUserAction(state: SessionState): Boolean = when (state) {
        SessionState.SignedOut,
        is SessionState.Expired,
        is SessionState.ValidationFailed,
        -> true

        SessionState.Authorizing,
        SessionState.Validating,
        SessionState.Validated,
        -> false
    }
}
