package com.cdnhunter.app.vpn.singbox

import com.cdnhunter.app.core.ConnectionError
import com.cdnhunter.app.core.ErrorCode
import com.cdnhunter.app.core.routing.ConnectivityVerifier
import com.cdnhunter.app.core.routing.CoreAttemptRunner
import com.cdnhunter.app.core.routing.CoreEvent
import com.cdnhunter.app.core.routing.CoreOutcome
import com.cdnhunter.app.core.routing.CorePlan
import com.cdnhunter.app.core.routing.CoreType
import com.cdnhunter.app.core.routing.RunResult
import com.cdnhunter.app.core.routing.SingBoxConfig
import com.cdnhunter.app.core.routing.SingBoxConfigSource
import com.cdnhunter.app.core.routing.SingBoxCore
import com.cdnhunter.app.core.routing.VpnCore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

/** The real LibboxEngine lifecycle (over a fake libbox) inside the real runner and SingBoxCore. */
class SingBoxLifecycleIntegrationTest {
    private val cfg = SingBoxConfigSource { SingBoxConfig.Ok("{}") }
    private val ok = ConnectivityVerifier { CoreOutcome.Connected }

    private class ScriptedClash(private val outcome: suspend () -> CoreOutcome) : VpnCore {
        override val type = CoreType.CLASH_META
        var alive = false
        override suspend fun connect(): CoreOutcome { alive = true; return outcome() }
        override suspend fun stop() { alive = false }
        override fun isAlive() = alive
    }

    private val clashFails = { ScriptedClash { CoreOutcome.Failed(ConnectionError.CoreError(ErrorCode.CORE_START_FAILED, "x")) } }
    private fun engine(b: FakeBackend) = LibboxEngine(b, ::testParams, io = Dispatchers.IO)

    private fun runner(clash: VpnCore, sing: VpnCore, events: MutableList<CoreEvent> = mutableListOf(), current: () -> Boolean = { true }, timeoutMs: Long = 5_000L) =
        CoreAttemptRunner(
            attemptId = 9, plan = CorePlan.Route(listOf(CoreType.CLASH_META, CoreType.SING_BOX), "t"),
            createCore = { if (it == CoreType.CLASH_META) clash else sing },
            isCurrent = current, emit = { events += it }, perCoreTimeoutMs = timeoutMs,
        )

    @Test fun fallbackOrderingClashDiesThenLibboxStartsInOrder() = runBlocking {
        val b = FakeBackend(); val clash = clashFails()
        val r = runner(clash, SingBoxCore(engine(b), cfg, ok)).run()
        assertEquals(CoreType.SING_BOX, (r as RunResult.Connected).core)
        assertFalse(clash.alive)
        assertEquals(listOf("setup", "newServer", "start", "startOrReload"), b.calls)
    }

    @Test fun libboxStartupFailureEndsInAnErrorAndClosesTheServer() = runBlocking {
        val b = FakeBackend().apply { failAt = "startOrReload" }
        val r = runner(clashFails(), SingBoxCore(engine(b), cfg, ok)).run() as RunResult.Failed
        assertEquals(ErrorCode.CORE_START_FAILED, r.error.code)
        assertEquals(1, b.servers.single().closeCalls)
    }

    @Test fun aCrashDuringVerificationIsCoreCrashedAndNothingIsLeftRunning() = runBlocking {
        val b = FakeBackend(); val e = engine(b)
        val v = ConnectivityVerifier { b.servers.single().events.onServiceStop(); awaitCancellation() }
        val r = runner(clashFails(), SingBoxCore(e, cfg, v)).run() as RunResult.Failed
        assertEquals(ErrorCode.CORE_CRASHED, r.error.code)
        assertFalse(e.isAlive()); assertEquals(1, b.servers.single().closeServiceCalls)
    }

    @Test fun aTimeoutStopsLibboxAndNeverReportsConnected() = runBlocking {
        val b = FakeBackend(); val e = engine(b); val events = mutableListOf<CoreEvent>()
        val r = runner(clashFails(), SingBoxCore(e, cfg, ConnectivityVerifier { awaitCancellation() }), events, timeoutMs = 80).run() as RunResult.Failed
        assertEquals(ErrorCode.CONNECTION_TIMEOUT, r.error.code)
        assertFalse(e.isAlive()); assertTrue(events.none { it is CoreEvent.Connected })
        assertEquals(1, b.servers.single().closeCalls)
    }

    @Test fun disconnectDuringLibboxVerificationClosesIt() = runBlocking {
        val b = FakeBackend(); val e = engine(b); val inVerify = CompletableDeferred<Unit>()
        val v = ConnectivityVerifier { inVerify.complete(Unit); awaitCancellation() }
        val job = launch { runner(clashFails(), SingBoxCore(e, cfg, v)).run() }
        inVerify.await(); job.cancel(); job.join()
        assertFalse(e.isAlive()); assertEquals(1, b.servers.single().closeServiceCalls)
    }

    @Test fun doubleDisconnectIsHarmless() = runBlocking {
        val b = FakeBackend(); val core = SingBoxCore(engine(b), cfg, ok)
        core.connect(); core.stop(); core.stop()
        assertEquals(1, b.servers.single().closeServiceCalls); assertFalse(core.isAlive())
    }

    @Test fun lateConnectedFromASupersededAttemptIsDiscardedAndLibboxClosed() = runBlocking {
        val b = FakeBackend(); val e = engine(b); val current = AtomicBoolean(true); val events = mutableListOf<CoreEvent>()
        val v = ConnectivityVerifier { current.set(false); CoreOutcome.Connected }
        val r = runner(clashFails(), SingBoxCore(e, cfg, v), events, current = { current.get() }).run()
        assertTrue(r is RunResult.Cancelled); assertTrue(events.none { it is CoreEvent.Connected })
        assertFalse(e.isAlive()); assertEquals(1, b.servers.single().closeServiceCalls)
    }

    @Test fun aStaleCallbackFromAnOldEngineCannotDisturbTheNewAttempt() = runBlocking {
        val b1 = FakeBackend(); val first = SingBoxCore(engine(b1), cfg, ok); first.connect(); first.stop()
        val b2 = FakeBackend(); val e2 = engine(b2); val second = SingBoxCore(e2, cfg, ok)
        assertTrue(second.connect() is CoreOutcome.Connected)
        b1.servers.single().events.onServiceStop()
        assertTrue(e2.isAlive())
    }
}
