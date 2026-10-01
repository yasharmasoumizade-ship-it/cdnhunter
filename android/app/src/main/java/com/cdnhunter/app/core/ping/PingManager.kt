package com.cdnhunter.app.core.ping

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Measures server reachability, independently of the VPN connection.
 *
 *  - Work runs on [dispatcher] (IO by default), never the caller's thread.
 *  - At most [maxConcurrency] probes are in flight, however many servers there are:
 *    a 50-server list is a queue worked by a few workers, not 50 simultaneous sockets.
 *  - A server that is already being measured is not measured twice at once.
 *  - Every result is stored under the server id it was measured for.
 *  - If the caller is cancelled mid-sweep, servers left in TESTING are put back to
 *    what they were instead of showing "testing…" forever.
 *
 * Only a timeout is retried (a lost SYN is plausible); a refusal or an unresolvable name
 * will not get better in 300 ms.
 */
class PingManager(
    private val prober: PingProber,
    val maxConcurrency: Int = 6,
    val timeoutMs: Int = 3_000,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val retryDelayMs: Long = 300L,
) {
    init {
        require(maxConcurrency >= 1) { "maxConcurrency must be >= 1" }
        require(timeoutMs >= 1) { "timeoutMs must be >= 1" }
    }

    private val results = ConcurrentHashMap<String, PingResult>()
    private val before = ConcurrentHashMap<String, PingResult>()
    private val inFlight: MutableSet<String> = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val listeners = CopyOnWriteArrayList<(PingResult) -> Unit>()

    fun result(serverId: String): PingResult = results[serverId] ?: PingResult(serverId)

    fun snapshot(): Map<String, PingResult> = HashMap(results)

    /** Seeds a result from an earlier session (e.g. a persisted last-known latency) without announcing it as a measurement. */
    fun seed(serverId: String, latencyMs: Int) {
        if (latencyMs < 0 || results.containsKey(serverId)) return
        results[serverId] = PingResult(
            serverId, PingState.UNKNOWN, -1, 0L, lastSuccessMs = latencyMs, lastSuccessAtMs = 0L,
        )
    }

    /** Registers [listener] for every result change; returns a function that unregisters it. */
    fun addListener(listener: (PingResult) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    /** Drops stored results for servers that are no longer in the list. */
    fun retain(serverIds: Set<String>) {
        results.keys.filter { it !in serverIds }.forEach { results.remove(it); before.remove(it) }
    }

    /**
     * Measures [targets] with bounded concurrency and returns when all are done.
     * [retries] extra attempts are made for a server that timed out.
     */
    suspend fun measure(targets: List<PingTarget>, retries: Int = 0) {
        val todo = targets.distinctBy { it.serverId }.filter { inFlight.add(it.serverId) }
        if (todo.isEmpty()) return
        try {
            for (t in todo) markTesting(t.serverId)
            val queue = Channel<PingTarget>(Channel.UNLIMITED)
            for (t in todo) queue.send(t)
            queue.close()
            coroutineScope {
                repeat(minOf(maxConcurrency, todo.size)) {
                    launch(dispatcher) {
                        for (t in queue) {
                            // receive() on a buffered channel does not suspend, so it never
                            // notices cancellation by itself: without this check a cancelled
                            // sweep would keep draining the whole queue.
                            if (!isActive) break
                            measureOne(t, retries)
                        }
                    }
                }
            }
        } finally {
            for (t in todo) {
                // Cancelled before this one was reached: undo the TESTING marker.
                if (results[t.serverId]?.state == PingState.TESTING) {
                    val prev = before.remove(t.serverId) ?: PingResult(t.serverId)
                    publish(prev)
                }
                before.remove(t.serverId)
                inFlight.remove(t.serverId)
            }
        }
    }

    private fun markTesting(id: String) {
        val cur = results[id] ?: PingResult(id)
        before[id] = cur
        publish(cur.copy(state = PingState.TESTING))
    }

    private suspend fun measureOne(t: PingTarget, retries: Int) {
        var outcome: ProbeOutcome
        var attempt = 0
        while (true) {
            outcome = try {
                prober.probe(t.host, t.port, timeoutMs)
            } catch (e: Exception) {
                ProbeOutcome.Unreachable(e.javaClass.simpleName)
            }
            if (outcome is ProbeOutcome.Timeout && attempt < retries) {
                attempt++
                delay(retryDelayMs)
                continue
            }
            break
        }
        val prev = before[t.serverId] ?: results[t.serverId] ?: PingResult(t.serverId)
        val now = clock()
        val next = when (val o = outcome) {
            is ProbeOutcome.Success -> prev.copy(
                state = PingState.AVAILABLE, latencyMs = o.latencyMs, measuredAtMs = now,
                lastSuccessMs = o.latencyMs, lastSuccessAtMs = now, consecutiveFailures = 0,
            )
            is ProbeOutcome.Timeout -> prev.copy(
                state = PingState.TIMEOUT, latencyMs = -1, measuredAtMs = now,
                consecutiveFailures = prev.consecutiveFailures + 1,
            )
            is ProbeOutcome.Unreachable -> prev.copy(
                state = PingState.UNREACHABLE, latencyMs = -1, measuredAtMs = now,
                consecutiveFailures = prev.consecutiveFailures + 1,
            )
        }
        before.remove(t.serverId)
        publish(next)
    }

    private fun publish(r: PingResult) {
        results[r.serverId] = r
        for (l in listeners) {
            try {
                l(r)
            } catch (_: Exception) {
            }
        }
    }
}
