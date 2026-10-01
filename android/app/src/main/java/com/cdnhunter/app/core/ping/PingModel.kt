package com.cdnhunter.app.core.ping

/** What is known about one server's reachability. */
enum class PingState {
    /** Never measured. */
    UNKNOWN,
    /** A measurement is in flight right now. */
    TESTING,
    /** The last measurement connected; [PingResult.latencyMs] is valid. */
    AVAILABLE,
    /** The server accepted no connection within the timeout. */
    TIMEOUT,
    /** The connection was refused, or the host could not be resolved or routed to. */
    UNREACHABLE,
}

/**
 * The latest measurement for one server, keyed by [serverId].
 *
 * Results are only ever stored and published under the id of the server they were
 * measured for, so one server's number cannot appear on another's row.
 * [lastSuccessMs] / [lastSuccessAtMs] survive later failures: "unreachable now, was
 * 140 ms five minutes ago" is information worth keeping.
 */
data class PingResult(
    val serverId: String,
    val state: PingState = PingState.UNKNOWN,
    /** Valid only when [state] is [PingState.AVAILABLE]; -1 otherwise. */
    val latencyMs: Int = -1,
    val measuredAtMs: Long = 0L,
    val lastSuccessMs: Int = -1,
    val lastSuccessAtMs: Long = 0L,
    val consecutiveFailures: Int = 0,
)

data class PingTarget(val serverId: String, val host: String, val port: Int)

sealed class ProbeOutcome {
    data class Success(val latencyMs: Int) : ProbeOutcome()
    object Timeout : ProbeOutcome()
    data class Unreachable(val reason: String) : ProbeOutcome()
}

/** Performs one blocking reachability probe. Called on a worker thread, never on the UI thread. */
interface PingProber {
    fun probe(host: String, port: Int, timeoutMs: Int): ProbeOutcome
}
