package com.cdnhunter.app.core

import com.cdnhunter.app.core.ping.PingManager
import com.cdnhunter.app.core.ping.PingProber
import com.cdnhunter.app.core.ping.PingState
import com.cdnhunter.app.core.ping.PingTarget
import com.cdnhunter.app.core.ping.ProbeOutcome
import com.cdnhunter.app.core.ping.TcpPinger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class PingManagerTest {

    private fun targets(n: Int) = (1..n).map { PingTarget("s$it", "host$it.example.com", 443) }

    private class FakeProber(val outcome: (String) -> ProbeOutcome, val sleepMs: Long = 0) : PingProber {
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val calls = ConcurrentHashMap<String, AtomicInteger>()
        override fun probe(host: String, port: Int, timeoutMs: Int): ProbeOutcome {
            val now = active.incrementAndGet()
            maxActive.updateAndGet { maxOf(it, now) }
            calls.computeIfAbsent(host) { AtomicInteger() }.incrementAndGet()
            try { if (sleepMs > 0) Thread.sleep(sleepMs) } finally { active.decrementAndGet() }
            return outcome(host)
        }
    }

    @Test fun concurrencyIsBoundedNoMatterHowManyServers() = runBlocking {
        val prober = FakeProber({ ProbeOutcome.Success(10) }, sleepMs = 15)
        val pm = PingManager(prober, maxConcurrency = 4)
        pm.measure(targets(60))
        assertTrue("peak ${prober.maxActive.get()}", prober.maxActive.get() <= 4)
        assertTrue(prober.maxActive.get() >= 2) // and it really did run in parallel
        assertEquals(60, pm.snapshot().values.count { it.state == PingState.AVAILABLE })
    }

    @Test fun eachResultLandsOnTheServerItWasMeasuredFor() = runBlocking {
        val pm = PingManager(FakeProber({ host -> ProbeOutcome.Success(host.removePrefix("host").substringBefore('.').toInt() * 10) }))
        pm.measure(targets(20))
        for (i in 1..20) assertEquals(i * 10, pm.result("s$i").latencyMs)
    }

    @Test fun allFourOutcomesMapToTheirStates() = runBlocking {
        val pm = PingManager(FakeProber({ h ->
            when (h) {
                "host1.example.com" -> ProbeOutcome.Success(42)
                "host2.example.com" -> ProbeOutcome.Timeout
                else -> ProbeOutcome.Unreachable("refused")
            }
        }))
        pm.measure(targets(3))
        assertEquals(PingState.AVAILABLE, pm.result("s1").state); assertEquals(42, pm.result("s1").latencyMs)
        assertEquals(PingState.TIMEOUT, pm.result("s2").state); assertEquals(-1, pm.result("s2").latencyMs)
        assertEquals(PingState.UNREACHABLE, pm.result("s3").state)
        assertEquals(PingState.UNKNOWN, pm.result("never-seen").state)
    }

    @Test fun lastSuccessSurvivesALaterFailureAndFailuresCount() = runBlocking {
        var ok = true
        val pm = PingManager(FakeProber({ if (ok) ProbeOutcome.Success(77) else ProbeOutcome.Timeout }), clock = { 5_000L })
        pm.measure(targets(1))
        ok = false
        pm.measure(targets(1)); pm.measure(targets(1))
        val r = pm.result("s1")
        assertEquals(PingState.TIMEOUT, r.state)
        assertEquals(77, r.lastSuccessMs); assertEquals(5_000L, r.lastSuccessAtMs); assertEquals(2, r.consecutiveFailures)
    }

    @Test fun onlyTimeoutsAreRetried() = runBlocking {
        val timeouts = FakeProber({ ProbeOutcome.Timeout })
        PingManager(timeouts, retryDelayMs = 1).measure(targets(1), retries = 2)
        assertEquals(3, timeouts.calls["host1.example.com"]!!.get())
        val refused = FakeProber({ ProbeOutcome.Unreachable("x") })
        PingManager(refused, retryDelayMs = 1).measure(targets(1), retries = 2)
        assertEquals(1, refused.calls["host1.example.com"]!!.get())
    }

    @Test fun aRetrySucceedingYieldsAvailable() = runBlocking {
        val n = AtomicInteger()
        val pm = PingManager(FakeProber({ if (n.incrementAndGet() < 2) ProbeOutcome.Timeout else ProbeOutcome.Success(30) }), retryDelayMs = 1)
        pm.measure(targets(1), retries = 1)
        assertEquals(PingState.AVAILABLE, pm.result("s1").state)
    }

    @Test fun aProberThatThrowsBecomesUnreachableNotACrash() = runBlocking {
        val pm = PingManager(object : PingProber { override fun probe(host: String, port: Int, timeoutMs: Int): ProbeOutcome = throw IllegalStateException("boom") })
        pm.measure(targets(3))
        assertEquals(3, pm.snapshot().values.count { it.state == PingState.UNREACHABLE })
    }

    @Test fun overlappingSweepsDoNotProbeTheSameServerTwice() = runBlocking {
        val prober = FakeProber({ ProbeOutcome.Success(5) }, sleepMs = 80)
        val pm = PingManager(prober, maxConcurrency = 3)
        val a = async(Dispatchers.Default) { pm.measure(targets(3)) }
        delay(20)
        val b = async(Dispatchers.Default) { pm.measure(targets(3)) } // same servers, first sweep still running
        a.await(); b.await()
        assertEquals(3, prober.calls.values.sumOf { it.get() })
    }

    @Test fun testingStateIsVisibleWhileMeasuringAndListenersHearIt() = runBlocking {
        val gate = CountDownLatch(1)
        val seen = java.util.Collections.synchronizedList(ArrayList<PingState>())
        val pm = PingManager(object : PingProber {
            override fun probe(host: String, port: Int, timeoutMs: Int): ProbeOutcome { gate.await(); return ProbeOutcome.Success(9) }
        })
        pm.addListener { seen += it.state }
        val job = launch(Dispatchers.Default) { pm.measure(targets(1)) }
        delay(100)
        assertEquals(PingState.TESTING, pm.result("s1").state)
        gate.countDown(); job.join()
        assertEquals(listOf(PingState.TESTING, PingState.AVAILABLE), seen.toList())
    }

    @Test fun cancellingASweepDoesNotLeaveServersStuckInTesting() = runBlocking {
        val prober = FakeProber({ ProbeOutcome.Success(5) }, sleepMs = 50)
        val pm = PingManager(prober, maxConcurrency = 1)
        pm.seed("s9", 123)
        val job = launch(Dispatchers.Default) { pm.measure(targets(30) + PingTarget("s9", "h9", 1)) }
        delay(120)
        job.cancelAndJoin()
        delay(100)
        assertTrue(pm.snapshot().values.none { it.state == PingState.TESTING })
        assertEquals(123, pm.result("s9").lastSuccessMs)
    }

    @Test fun retainDropsRemovedServers() = runBlocking {
        val pm = PingManager(FakeProber({ ProbeOutcome.Success(1) }))
        pm.measure(targets(5)); pm.retain(setOf("s1", "s2"))
        assertEquals(setOf("s1", "s2"), pm.snapshot().keys)
    }

    @Test fun duplicateIdsInOneCallAreMeasuredOnce() = runBlocking {
        val prober = FakeProber({ ProbeOutcome.Success(1) })
        PingManager(prober).measure(listOf(PingTarget("a", "h", 1), PingTarget("a", "h", 1)))
        assertEquals(1, prober.calls["h"]!!.get())
    }

    // ── the real TCP prober, against real sockets on loopback ────────────────

    @Test fun tcpPingerReportsSuccessRefusedAndTimeoutDistinctly() {
        ServerSocket(0).use { server ->
            val ok = TcpPinger().probe("127.0.0.1", server.localPort, 2_000)
            assertTrue(ok is ProbeOutcome.Success && ok.latencyMs >= 1)
            val closedPort = ServerSocket(0).use { it.localPort } // freed immediately: nothing listens
            assertTrue(TcpPinger().probe("127.0.0.1", closedPort, 2_000) is ProbeOutcome.Unreachable)
        }
        assertTrue(TcpPinger().probe("no-such-host.invalid", 443, 2_000) is ProbeOutcome.Unreachable)
    }

    @Test fun tcpPingerCallsProtectBeforeConnecting() {
        var protectedCount = 0
        ServerSocket(0).use { s -> TcpPinger(protect = { protectedCount++ }).probe("127.0.0.1", s.localPort, 1_000) }
        assertEquals(1, protectedCount)
    }
}
