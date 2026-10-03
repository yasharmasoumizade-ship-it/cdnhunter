package com.cdnhunter.app.core.routing

import com.cdnhunter.app.core.ConnectionError
import com.cdnhunter.app.core.ErrorCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class SingBoxCoreTest {

    private class FakeEngine(private val onStart: suspend () -> Unit = {}) : SingBoxEngine {
        val terminated = CompletableDeferred<String>()
        var alive = false
        var startCalls = 0
        var stopCalls = 0
        override suspend fun start(configJson: String) { startCalls++; onStart(); alive = true }
        override suspend fun awaitTermination(): String = terminated.await()
        override suspend fun stop() { stopCalls++; alive = false; terminated.complete("stopped") }
        override fun isAlive() = alive
    }

    private val cfg = SingBoxConfigSource { SingBoxConfig.Ok("{}") }
    private val verified = ConnectivityVerifier { CoreOutcome.Connected }
    private fun core(e: FakeEngine, v: ConnectivityVerifier = verified, c: SingBoxConfigSource = cfg) = SingBoxCore(e, c, v)

    // --- SingBoxCore on its own ---------------------------------------------------------------

    @Test fun connectedOnlyAfterTheProxyPathIsVerified() = runBlocking {
        val e = FakeEngine()
        assertTrue(core(e).connect() is CoreOutcome.Connected)
        assertTrue(e.alive)
    }

    @Test fun aConfigErrorNeverStartsTheEngine() = runBlocking {
        val e = FakeEngine()
        val bad = SingBoxConfigSource { SingBoxConfig.Err(ConnectionError.ConfigError(ErrorCode.INVALID_CONFIG, "REALITY public key is missing")) }
        val r = core(e, c = bad).connect() as CoreOutcome.Failed
        assertEquals(ErrorCode.INVALID_CONFIG, r.error.code)
        assertEquals(0, e.startCalls)
    }

    @Test fun startupFailureIsCoreStartFailed() = runBlocking {
        val e = FakeEngine(onStart = { throw IllegalStateException("libbox: start service: bad config") })
        val r = core(e).connect() as CoreOutcome.Failed
        assertEquals(ErrorCode.CORE_START_FAILED, r.error.code)
        assertFalse(e.alive)
    }

    @Test fun aCrashDuringVerificationIsReportedAtOnceAsCoreCrashed() = runBlocking {
        val e = FakeEngine()
        val hang = ConnectivityVerifier { e.terminated.complete("signal 11"); awaitCancellation() }
        val r = core(e, hang).connect() as CoreOutcome.Failed
        assertEquals(ErrorCode.CORE_CRASHED, r.error.code)
    }

    @Test fun aVerifierTimeoutIsAFailureNotAConnection() = runBlocking {
        val e = FakeEngine()
        val timeout = ConnectivityVerifier { CoreOutcome.Failed(ConnectionError.TimeoutError("probe: no response")) }
        val r = core(e, timeout).connect() as CoreOutcome.Failed
        assertEquals(ErrorCode.CONNECTION_TIMEOUT, r.error.code)
    }

    @Test fun aVerifierThatThrowsIsAFailure() = runBlocking {
        val r = core(FakeEngine(), ConnectivityVerifier { throw RuntimeException("boom") }).connect()
        assertTrue(r is CoreOutcome.Failed)
    }

    @Test fun stopIsIdempotent() = runBlocking {
        val e = FakeEngine(); val c = core(e)
        c.connect(); c.stop(); c.stop()
        assertFalse(c.isAlive())
    }

    // --- through the runner: the lifecycle cases that matter ---------------------------------

    private class World { val events = mutableListOf<CoreEvent>() }

    private fun runner(w: World, clash: VpnCore, sing: VpnCore, current: () -> Boolean = { true }, timeoutMs: Long = 5_000L) =
        CoreAttemptRunner(
            attemptId = 7, plan = CorePlan.Route(listOf(CoreType.CLASH_META, CoreType.SING_BOX), "t"),
            createCore = { if (it == CoreType.CLASH_META) clash else sing },
            isCurrent = current, emit = { w.events += it }, perCoreTimeoutMs = timeoutMs,
        )

    private class ScriptedClash(private val outcome: suspend () -> CoreOutcome) : VpnCore {
        override val type = CoreType.CLASH_META
        var alive = false
        override suspend fun connect(): CoreOutcome { alive = true; return outcome() }
        override suspend fun stop() { alive = false }
        override fun isAlive() = alive
    }

    // timeout: the runner's safety net fails the hung engine, stops it, never reports connected
    @Test fun connectionTimeoutStopsSingBoxAndEndsInAnError() = runBlocking {
        val w = World(); val e = FakeEngine()
        val hang = ConnectivityVerifier { awaitCancellation() }
        val clash = ScriptedClash { CoreOutcome.Failed(ConnectionError.CoreError(ErrorCode.CORE_START_FAILED, "x")) }
        val r = runner(w, clash, core(e, hang), timeoutMs = 60).run() as RunResult.Failed
        assertEquals(ErrorCode.CONNECTION_TIMEOUT, r.error.code)
        assertFalse("sing-box must be stopped after a timeout", e.alive)
        assertTrue(w.events.none { it is CoreEvent.Connected })
    }

    // disconnect: cancelling the attempt while sing-box is verifying leaves nothing running
    @Test fun disconnectWhileSingBoxIsConnectingStopsEverything() = runBlocking {
        val w = World(); val e = FakeEngine(); val inVerify = CompletableDeferred<Unit>()
        val v = ConnectivityVerifier { inVerify.complete(Unit); awaitCancellation() }
        val clash = ScriptedClash { CoreOutcome.Failed(ConnectionError.CoreError(ErrorCode.CORE_START_FAILED, "x")) }
        val job = launch { runner(w, clash, core(e, v)).run() }
        inVerify.await(); job.cancel(); job.join()
        assertFalse(e.alive); assertFalse(clash.alive)
    }

    // late CONNECTED from an attempt that has been superseded must publish nothing and must not stay up
    @Test fun lateConnectedFromASupersededAttemptIsDiscardedAndStopped() = runBlocking {
        val w = World(); val e = FakeEngine(); val current = AtomicBoolean(true)
        val clash = ScriptedClash { CoreOutcome.Failed(ConnectionError.CoreError(ErrorCode.CORE_START_FAILED, "x")) }
        val v = ConnectivityVerifier { current.set(false); CoreOutcome.Connected } // the user already moved on
        val r = runner(w, clash, core(e, v), current = { current.get() }).run()
        assertTrue(r is RunResult.Cancelled)
        assertTrue(w.events.none { it is CoreEvent.Connected })
        assertFalse(e.alive)
    }

    // fallback happens for the real engine failures only — for every error code, not just the ones we thought of
    @Test fun fallbackHappensExactlyForTheAllowedFailures() = runBlocking {
        val allowed = setOf(ErrorCode.CORE_START_FAILED, ErrorCode.CORE_CRASHED, ErrorCode.CONNECTION_TIMEOUT)
        for (code in ErrorCode.values()) {
            val w = World(); val e = FakeEngine()
            val clash = ScriptedClash { CoreOutcome.Failed(ConnectionError.CoreError(code, "fail")) }
            runner(w, clash, core(e)).run()
            assertEquals("fallback for $code", code in allowed, e.startCalls == 1)
        }
    }

    @Test fun configNetworkAndTunErrorsDoNotReachSingBox() = runBlocking {
        val errors = listOf<ConnectionError>(
            ConnectionError.ConfigError(ErrorCode.INVALID_CONFIG, "c"),
            ConnectionError.NetworkError("n"),
            ConnectionError.TunnelError("t"),
            ConnectionError.PermissionError("p"),
        )
        for (err in errors) {
            val w = World(); val e = FakeEngine()
            val r = runner(w, ScriptedClash { CoreOutcome.Failed(err) }, core(e)).run()
            assertTrue(r is RunResult.Failed); assertEquals(0, e.startCalls)
        }
    }

    // a real crash of the first engine falls back; the fallback engine then really connects
    @Test fun clashCrashThenSingBoxConnects() = runBlocking {
        val w = World(); val e = FakeEngine()
        val clash = ScriptedClash { CoreOutcome.Failed(ConnectionError.CoreError(ErrorCode.CORE_CRASHED, "native crash")) }
        val r = runner(w, clash, core(e)).run()
        assertEquals(CoreType.SING_BOX, (r as RunResult.Connected).core)
        assertFalse(clash.alive); assertTrue(e.alive)
    }
}
