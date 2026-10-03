package com.cdnhunter.app.core

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URL

sealed class ProbeResult {
    data class Ok(val latencyMs: Int, val url: String) : ProbeResult()
    data class Failed(val reason: String, val timedOut: Boolean) : ProbeResult()
}

/**
 * End-to-end check that traffic really leaves through the proxy: one small HTTPS request
 * sent into the core's local mixed port, which the core forwards through the configured
 * server. Success proves the server is reachable, the credentials are accepted and the
 * path out works — which "the core started without an exception" never did.
 *
 * Several URLs are tried in turn so one blocked or slow endpoint does not fail a
 * connection that works. Redirects are not followed: a captive portal answering 302 must
 * not look like success. Nothing here depends on Android.
 */
class ProxyProbe(
    private val proxyHost: String = "127.0.0.1",
    private val proxyPort: Int = 10808,
    private val urls: List<String> = DEFAULT_URLS,
    private val perUrlTimeoutMs: Int = 4_000,
) {
    fun probeOnce(): ProbeResult {
        var last: ProbeResult.Failed = ProbeResult.Failed("no probe url configured", false)
        for (url in urls) {
            val started = System.nanoTime()
            var conn: HttpURLConnection? = null
            try {
                conn = URL(url).openConnection(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort))) as HttpURLConnection
                conn.connectTimeout = perUrlTimeoutMs
                conn.readTimeout = perUrlTimeoutMs
                conn.instanceFollowRedirects = false
                conn.useCaches = false
                conn.requestMethod = "GET"
                val code = conn.responseCode
                if (code == 204 || code == 200) {
                    val ms = ((System.nanoTime() - started) / 1_000_000L).toInt().coerceAtLeast(1)
                    return ProbeResult.Ok(ms, url)
                }
                last = ProbeResult.Failed("unexpected HTTP $code", false)
            } catch (_: SocketTimeoutException) {
                last = ProbeResult.Failed("timed out", true)
            } catch (e: Exception) {
                last = ProbeResult.Failed(e.javaClass.simpleName + (e.message?.let { ": ${it.take(80)}" } ?: ""), false)
            } finally {
                try {
                    conn?.disconnect()
                } catch (_: Exception) {
                }
            }
        }
        return last
    }

    companion object {
        /**
         * The first URL is an IP literal on purpose. A request by hostname makes the core resolve the
         * name first, and in this app DNS is DNS-over-HTTPS that itself travels through the proxy: a
         * proxy that is slow to come up, or a DoH endpoint that is unreachable, fails the check for
         * reasons that have nothing to do with whether traffic can leave through the server. An IP
         * literal tests exactly one thing — can a TLS connection be carried through the server.
         * The hostname URLs follow as fallbacks (Cloudflare's certificate covers 1.1.1.1).
         */
        val DEFAULT_URLS = listOf(
            "https://1.1.1.1/cdn-cgi/trace",
            "https://www.gstatic.com/generate_204",
            "https://cp.cloudflare.com/generate_204",
        )
    }
}
