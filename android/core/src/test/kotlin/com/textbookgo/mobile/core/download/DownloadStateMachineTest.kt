package com.textbookgo.mobile.core.download

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DownloadStateMachineTest {

    @Test
    fun `happy path transitions are allowed`() {
        val path = listOf(
            DownloadState.QUEUED to DownloadState.RESOLVING,
            DownloadState.RESOLVING to DownloadState.DOWNLOADING,
            DownloadState.DOWNLOADING to DownloadState.VERIFYING,
            DownloadState.VERIFYING to DownloadState.COMMITTING,
            DownloadState.COMMITTING to DownloadState.SUCCEEDED,
        )

        path.forEach { (from, to) ->
            assertThat(DownloadStateMachine.canTransition(from, to)).isTrue()
        }
    }

    @Test
    fun `skipping verification or committing is rejected`() {
        assertThat(DownloadStateMachine.canTransition(DownloadState.QUEUED, DownloadState.VERIFYING)).isFalse()
        assertThat(DownloadStateMachine.canTransition(DownloadState.DOWNLOADING, DownloadState.COMMITTING)).isFalse()
        assertThat(DownloadStateMachine.canTransition(DownloadState.VERIFYING, DownloadState.SUCCEEDED)).isFalse()
    }

    @Test
    fun `terminal states are only succeeded skipped and cancelled`() {
        assertThat(DownloadStateMachine.isTerminal(DownloadState.SUCCEEDED)).isTrue()
        assertThat(DownloadStateMachine.isTerminal(DownloadState.SKIPPED)).isTrue()
        assertThat(DownloadStateMachine.isTerminal(DownloadState.CANCELLED)).isTrue()

        assertThat(DownloadStateMachine.isTerminal(DownloadState.FAILED)).isFalse()
        assertThat(DownloadStateMachine.isTerminal(DownloadState.PAUSED)).isFalse()
        assertThat(DownloadStateMachine.isTerminal(DownloadState.DOWNLOADING)).isFalse()
    }

    @Test
    fun `active states are transfer and commit phases`() {
        listOf(
            DownloadState.RESOLVING,
            DownloadState.DOWNLOADING,
            DownloadState.VERIFYING,
            DownloadState.COMMITTING,
        ).forEach { assertThat(DownloadStateMachine.isActive(it)).isTrue() }

        listOf(
            DownloadState.QUEUED,
            DownloadState.PAUSED,
            DownloadState.SUCCEEDED,
            DownloadState.FAILED,
        ).forEach { assertThat(DownloadStateMachine.isActive(it)).isFalse() }
    }

    @Test
    fun `pause is allowed before and during transfer but not in critical sections`() {
        listOf(
            DownloadState.QUEUED,
            DownloadState.RESOLVING,
            DownloadState.DOWNLOADING,
        ).forEach { assertThat(DownloadStateMachine.canPause(it)).isTrue() }

        listOf(
            DownloadState.VERIFYING,
            DownloadState.COMMITTING,
            DownloadState.SUCCEEDED,
            DownloadState.FAILED,
            DownloadState.CANCELLED,
        ).forEach { assertThat(DownloadStateMachine.canPause(it)).isFalse() }
    }

    @Test
    fun `only paused tasks can resume`() {
        assertThat(DownloadStateMachine.canResume(DownloadState.PAUSED)).isTrue()
        assertThat(DownloadStateMachine.canResume(DownloadState.QUEUED)).isFalse()
        assertThat(DownloadStateMachine.canResume(DownloadState.DOWNLOADING)).isFalse()
    }

    @Test
    fun `pause and resume transitions are explicit`() {
        assertThat(DownloadStateMachine.canTransition(DownloadState.DOWNLOADING, DownloadState.PAUSED)).isTrue()
        assertThat(DownloadStateMachine.canTransition(DownloadState.PAUSED, DownloadState.QUEUED)).isTrue()
        assertThat(DownloadStateMachine.canTransition(DownloadState.PAUSED, DownloadState.DOWNLOADING)).isFalse()
    }

    @Test
    fun `failed needs auth and cancelled can be retried explicitly`() {
        listOf(
            DownloadState.FAILED,
            DownloadState.CANCELLED,
            DownloadState.NEEDS_AUTH,
            DownloadState.RETRY_WAITING,
            DownloadState.WAITING_NETWORK,
        ).forEach {
            assertThat(DownloadStateMachine.canTransition(it, DownloadState.QUEUED)).isTrue()
        }
    }

    @Test
    fun `successful task cannot move back to transfer states`() {
        assertThat(DownloadStateMachine.canTransition(DownloadState.SUCCEEDED, DownloadState.DOWNLOADING)).isFalse()
        assertThat(DownloadStateMachine.canTransition(DownloadState.SKIPPED, DownloadState.QUEUED)).isFalse()
    }

    @Test
    fun `any non terminal task can be cancelled`() {
        listOf(
            DownloadState.QUEUED,
            DownloadState.RESOLVING,
            DownloadState.DOWNLOADING,
            DownloadState.PAUSED,
            DownloadState.VERIFYING,
            DownloadState.COMMITTING,
            DownloadState.NEEDS_AUTH,
        ).forEach {
            assertThat(DownloadStateMachine.canTransition(it, DownloadState.CANCELLED)).isTrue()
        }

        assertThat(DownloadStateMachine.canTransition(DownloadState.SUCCEEDED, DownloadState.CANCELLED)).isFalse()
    }
}
