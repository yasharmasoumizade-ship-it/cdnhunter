package com.cdnhunter.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

class ProxyProbeTest {

    /** A one-shot-per-connection fake HTTP proxy that answers every request with [status]. */
    private fun fakeProxy(status: Int, hang: Boolean = false): ServerSocket {
        val server = ServerSocket(0)
        thread(isDaemon = true) {
            while (!server.isClosed) {
                try {
                    val s = server.accept()
                    thread(isDaemon = true) {
                        try {
                            val r = s.getInputStream().bufferedReader()
                            while (true) { val l = r.readLine() ?: break; if (l.isEmpty()) break }
                            if (hang) Thread.sleep(10_000)
                            else s.getOutputStream().write("HTTP/1.1 $status X\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        } catch (_: Exception) {} finally { try { s.close() } catch (_: Exception) {} }
                    }
                } catch (_: Exception) { }
            }
        }
        return server
    }

    private fun probe(port: Int, timeout: Int = 1_500) =
        ProxyProbe("127.0.0.1", port, listOf("http://probe.invalid/generate_204"), timeout)

    @Test fun a204ThroughTheProxyIsSuccess() = fakeProxy(204).use { p ->
        val r = probe(p.localPort).probeOnce()
        assertTrue(r.toString(), r is ProbeResult.Ok && r.latencyMs >= 1)
    }

    @Test fun nothingListeningOnTheProxyPortFailsFast() {
        val port = ServerSocket(0).use { it.localPort }
        val t0 = System.currentTimeMillis()
        val r = probe(port).probeOnce()
        assertTrue(r is ProbeResult.Failed && !r.timedOut)
        assertTrue("took too long", System.currentTimeMillis() - t0 < 1_400)
    }

    @Test fun aProxyThatNeverAnswersTimesOut() = fakeProxy(204, hang = true).use { p ->
        val r = probe(p.localPort, timeout = 400).probeOnce()
        assertTrue(r is ProbeResult.Failed && r.timedOut)
    }

    @Test fun errorStatusesAndRedirectsAreNotSuccess() {
        for (status in listOf(502, 403, 302, 500)) fakeProxy(status).use { p ->
            assertTrue("status $status", probe(p.localPort).probeOnce() is ProbeResult.Failed)
        }
    }

    @Test fun secondUrlIsTriedWhenTheFirstFails() = fakeProxy(204).use { p ->
        // Both URLs reach the same fake proxy, so make the first one fail by being a bad scheme.
        val r = ProxyProbe("127.0.0.1", p.localPort, listOf("ftp://nope/", "http://probe.invalid/x"), 1_500).probeOnce()
        assertTrue(r is ProbeResult.Ok && r.url.startsWith("http://"))
    }

    @Test fun noUrlsIsAFailureNotACrash() {
        assertEquals(ProbeResult.Failed("no probe url configured", false), ProxyProbe(urls = emptyList()).probeOnce())
    }
}
