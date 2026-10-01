package com.cdnhunter.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {
    private val timeout = ConnectionError.TimeoutError("t")
    private val badConfig = ConnectionError.ConfigError(ErrorCode.INVALID_CONFIG, "x")

    @Test fun backoffDoublesAndIsCapped() {
        val p = ReconnectPolicy(maxAttempts = 10, baseMs = 1_000, capMs = 15_000)
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 15_000L), (1..6).map { p.delayMs(it) })
    }

    @Test fun stopsAfterMaxAttempts() {
        val p = ReconnectPolicy(maxAttempts = 3)
        assertTrue(p.shouldRetry(0, timeout, true))
        assertTrue(p.shouldRetry(2, timeout, true))
        assertFalse(p.shouldRetry(3, timeout, true))
    }

    @Test fun neverRetriesWhatRetryingCannotFix() {
        val p = ReconnectPolicy()
        assertFalse(p.shouldRetry(0, badConfig, true))
        assertFalse(p.shouldRetry(0, ConnectionError.PermissionError("no"), true))
        assertFalse(p.shouldRetry(0, ConnectionError.CoreError(ErrorCode.CORE_START_FAILED, "rejected config"), true))
        assertTrue(p.shouldRetry(0, ConnectionError.CoreError(ErrorCode.CORE_CRASHED, "died"), true))
    }

    @Test fun respectsTheUsersAutoReconnectSetting() {
        assertFalse(ReconnectPolicy().shouldRetry(0, timeout, autoReconnectEnabled = false))
    }

    @Test fun hugeAttemptNumbersDoNotOverflow() {
        assertEquals(15_000L, ReconnectPolicy(maxAttempts = 100).delayMs(100))
    }
}
