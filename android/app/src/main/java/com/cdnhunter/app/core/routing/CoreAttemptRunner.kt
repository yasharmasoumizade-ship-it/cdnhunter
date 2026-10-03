package com.cdnhunter.app.core.routing

import com.cdnhunter.app.core.ConnectionError
import com.cdnhunter.app.core.ErrorCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One engine behind the shared connection lifecycle. The UI and the service never learn which
 * engine this is, and neither engine knows about the other.
 */
interface VpnCore {
    val type: CoreType

    /**
     * Returns only when the engine has REALLY connected (up, and the proxy path verified) or REALLY
     * failed. Having created the tun interface or having started the process is not "connected".
     */
    suspend fun connect(): CoreOutcome

    /** Fully stops the engine and releases everything it holds. Idempotent. Returns once it is stopped. */
    suspend fun stop()

    fun isAlive(): Boolean
}

sealed class CoreOutcome {
    object Connected : CoreOutcome()
    class Failed(val error: ConnectionError) : CoreOutcome()
}

/** Every event carries its attempt id, so a receiver can drop whatever is not from the live attempt. */
sealed class CoreEvent {
    abstract val attemptId: Long

    class Starting(override val attemptId: Long, val core: CoreType) : CoreEvent()
    class Failed(override val attemptId: Long, val core: CoreType, val error: ConnectionError) : CoreEvent()
    class Switching(override val attemptId: Long, val from: CoreType, val to: CoreType) : CoreEvent()
    class Connected(override val attemptId: Long, val core: CoreType) : CoreEvent()
    class Exhausted(override val attemptId: Long, val error: ConnectionError) : CoreEvent()
}

sealed class RunResult {
    class Connected(val core: CoreType) : RunResult()
    class Failed(val error: ConnectionError, val tried: List<CoreType>) : RunResult()

    /** The attempt was superseded or cancelled. No state may be published for it. */
    object Cancelled : RunResult()
}

/**
 * Which real failures justify trying the other engine. It is about the ENGINE, so anything the other
 * engine would meet too is excluded: a bad config, a missing permission, a failed tun interface, no
 * network. A UI or IP-lookup slowness is not an input here at all.
 */
object FallbackPolicy {
    fun eligible(error: ConnectionError): Boolean = when (error.code) {
        ErrorCode.CORE_START_FAILED,
        ErrorCode.CORE_CRASHED,
        ErrorCode.CONNECTION_TIMEOUT -> true
        ErrorCode.INVALID_JSON, ErrorCode.INVALID_CONFIG, ErrorCode.INVALID_SERVER, ErrorCode.INVALID_PORT,
        ErrorCode.INVALID_CREDENTIALS, ErrorCode.UNSUPPORTED_PROTOCOL, ErrorCode.PERMISSION_DENIED,
        ErrorCode.TUNNEL_FAILED, ErrorCode.NETWORK_UNAVAILABLE, ErrorCode.RECONNECT_FAILED,
        ErrorCode.UNKNOWN -> false
    }
}

/**
 * Runs ONE connection attempt through its [CorePlan.Route]: first engine; on a real, fallback-eligible
 * failure the first engine is fully stopped and verified dead, and only then the second one starts.
 * Never two engines at once, never a third try, never back to an engine already used.
 *
 * Time only appears as a per-engine safety net: a timeout is a FAILURE of that engine, it can never
 * turn into CONNECTED.
 */
class CoreAttemptRunner(
    private val attemptId: Long,
    private val plan: CorePlan.Route,
    private val createCore: (CoreType) -> VpnCore,
    private val isCurrent: () -> Boolean,
    private val emit: (CoreEvent) -> Unit,
    private val perCoreTimeoutMs: Long = 45_000L,
) {
    suspend fun run(): RunResult {
        val tried = mutableListOf<CoreType>()
        var previous: CoreType? = null
        var next: CoreType? = plan.first

        while (next != null) {
            val type: CoreType = next
            if (!isCurrent()) return RunResult.Cancelled
            previous?.let { emit(CoreEvent.Switching(attemptId, it, type)) }
            tried += type
            val core = createCore(type)
            emit(CoreEvent.Starting(attemptId, type))

            val outcome: CoreOutcome = try {
                withTimeoutOrNull(perCoreTimeoutMs) { core.connect() }
                    ?: CoreOutcome.Failed(ConnectionError.TimeoutError("${type.name} did not report success or failure in ${perCoreTimeoutMs}ms"))
            } catch (e: CancellationException) {
                stopQuietly(core) // a disconnect / a new attempt: nothing may be left running
                throw e
            } catch (e: Exception) {
                CoreOutcome.Failed(ConnectionError.CoreError(ErrorCode.CORE_START_FAILED, "${type.name} threw ${e.javaClass.simpleName}", e))
            }

            if (outcome is CoreOutcome.Connected) {
                if (isCurrent()) {
                    emit(CoreEvent.Connected(attemptId, type))
                    return RunResult.Connected(type)
                }
                stopQuietly(core) // a late "connected" from a superseded attempt must not publish anything
                return RunResult.Cancelled
            }

            val error = (outcome as CoreOutcome.Failed).error
            val dead = stopAndVerify(core) // BEFORE anything else, whatever happens next
            if (!isCurrent()) return RunResult.Cancelled
            emit(CoreEvent.Failed(attemptId, type, error))

            val following = plan.after(type)
            if (following == null || !FallbackPolicy.eligible(error)) return exhausted(error, tried)
            if (!dead) {
                return exhausted(
                    ConnectionError.CoreError(ErrorCode.CORE_START_FAILED, "${type.name} did not stop; ${following.name} was not started"),
                    tried,
                )
            }
            previous = type
            next = following
        }
        // plan.first is never null, so the loop always returns above
        return exhausted(ConnectionError.UnknownError("empty plan"), tried)
    }

    private fun exhausted(error: ConnectionError, tried: List<CoreType>): RunResult {
        emit(CoreEvent.Exhausted(attemptId, error))
        return RunResult.Failed(error, tried)
    }

    private suspend fun stopQuietly(core: VpnCore) {
        withContext(NonCancellable) { try { core.stop() } catch (_: Exception) { /* stop is best effort here; the caller reports the real failure */ } }
    }

    /** True only if the engine reports itself no longer alive after stop(). */
    private suspend fun stopAndVerify(core: VpnCore): Boolean = withContext(NonCancellable) {
        try { core.stop() } catch (_: Exception) { }
        !core.isAlive()
    }
}
