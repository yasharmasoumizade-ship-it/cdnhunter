package com.cdnhunter.app.core

/**
 * Bounded exponential backoff for reconnecting: attempt 1 waits [baseMs], attempt 2
 * twice that, and so on, capped at [capMs]. After [maxAttempts] the caller gives up with
 * [ErrorCode.RECONNECT_FAILED] rather than retrying forever — on a genuinely dead
 * network, endless retries only drain the battery and delay the kill switch taking over.
 */
class ReconnectPolicy(
    val maxAttempts: Int = 3,
    private val baseMs: Long = 1_000L,
    private val capMs: Long = 15_000L,
) {
    init {
        require(maxAttempts >= 0) { "maxAttempts must be >= 0" }
    }

    /** Whether another attempt is allowed after [attemptsSoFar] attempts and the given failure. */
    fun shouldRetry(attemptsSoFar: Int, error: ConnectionError, autoReconnectEnabled: Boolean): Boolean =
        autoReconnectEnabled && error.retryable && attemptsSoFar < maxAttempts

    /** Delay before attempt number [attempt] (1-based). */
    fun delayMs(attempt: Int): Long {
        require(attempt >= 1) { "attempt is 1-based" }
        val shift = (attempt - 1).coerceAtMost(20)
        return (baseMs shl shift).coerceAtMost(capMs)
    }
}
