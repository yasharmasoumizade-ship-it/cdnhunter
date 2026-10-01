package com.cdnhunter.app.core.ping

import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * TCP-connect latency: the time for the three-way handshake to the server's port.
 *
 * Name resolution is done first and is NOT counted in the latency, and it is bounded by
 * the same timeout — `InetSocketAddress(host, port)` resolves synchronously with no
 * timeout of its own, so a dead resolver would otherwise hang a worker indefinitely.
 *
 * [protect] is called on each socket before it connects; on Android it is
 * `VpnService.protect`, which keeps the probe off the tunnel while one is up.
 */
class TcpPinger(
    private val protect: (Socket) -> Unit = {},
    private val nanoClock: () -> Long = { System.nanoTime() },
    /** Replaces the default system lookup, e.g. to resolve on a specific network. Still bounded by the timeout. */
    private val resolver: ((String) -> InetAddress)? = null,
) : PingProber {

    override fun probe(host: String, port: Int, timeoutMs: Int): ProbeOutcome {
        val started = nanoClock()
        val address = try {
            resolve(host, timeoutMs)
        } catch (_: TimeoutException) {
            return ProbeOutcome.Timeout
        } catch (e: UnknownHostException) {
            return ProbeOutcome.Unreachable("unresolvable host")
        } catch (e: Exception) {
            return ProbeOutcome.Unreachable(e.javaClass.simpleName)
        }
        val remaining = timeoutMs - ((nanoClock() - started) / 1_000_000L).toInt()
        if (remaining <= 0) return ProbeOutcome.Timeout

        val socket = Socket()
        return try {
            try {
                protect(socket)
            } catch (_: Exception) {
                // A probe that cannot be protected still measures something useful; do not fail it for that.
            }
            val t0 = nanoClock()
            socket.connect(InetSocketAddress(address, port), remaining)
            val ms = ((nanoClock() - t0) / 1_000_000L).toInt().coerceAtLeast(1)
            ProbeOutcome.Success(ms)
        } catch (_: SocketTimeoutException) {
            ProbeOutcome.Timeout
        } catch (e: ConnectException) {
            ProbeOutcome.Unreachable("connection refused or network unreachable")
        } catch (e: NoRouteToHostException) {
            ProbeOutcome.Unreachable("no route to host")
        } catch (e: Exception) {
            ProbeOutcome.Unreachable(e.javaClass.simpleName)
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun resolve(host: String, timeoutMs: Int): InetAddress {
        val h = host.trim().removePrefix("[").removeSuffix("]")
        // Literals need no lookup (and must not go through the resolver thread).
        if (h.isNotEmpty() && (h.contains(':') || h.all { it.isDigit() || it == '.' })) return InetAddress.getByName(h)
        val lookup = resolver
        val future = RESOLVER.submit(Callable { if (lookup != null) lookup(h) else InetAddress.getByName(h) })
        try {
            return future.get(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.ExecutionException) {
            throw (e.cause as? Exception) ?: e
        } catch (e: TimeoutException) {
            future.cancel(true)
            throw e
        }
    }

    private companion object {
        val RESOLVER: ExecutorService = Executors.newCachedThreadPool(object : ThreadFactory {
            private var n = 0
            @Synchronized override fun newThread(r: Runnable): Thread =
                Thread(r, "ping-resolver-${n++}").apply { isDaemon = true }
        })
    }
}
