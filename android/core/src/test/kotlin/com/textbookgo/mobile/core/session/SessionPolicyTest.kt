package com.textbookgo.mobile.core.session

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SessionPolicyTest {

    @Test
    fun `only a validated session can call protected endpoints`() {
        assertThat(SessionPolicy.canUseProtectedRequests(SessionState.Validated)).isTrue()

        listOf(
            SessionState.SignedOut,
            SessionState.Authorizing,
            SessionState.Validating,
            SessionState.Expired(),
            SessionState.ValidationFailed("bad token"),
        ).forEach {
            assertThat(SessionPolicy.canUseProtectedRequests(it)).isFalse()
        }
    }

    @Test
    fun `expired and failed sessions need user action`() {
        listOf(
            SessionState.SignedOut,
            SessionState.Expired(),
            SessionState.ValidationFailed("bad token"),
        ).forEach {
            assertThat(SessionPolicy.requiresUserAction(it)).isTrue()
        }
    }

    @Test
    fun `authorizing validating and validated do not prompt again`() {
        listOf(
            SessionState.Authorizing,
            SessionState.Validating,
            SessionState.Validated,
        ).forEach {
            assertThat(SessionPolicy.requiresUserAction(it)).isFalse()
        }
    }
}
