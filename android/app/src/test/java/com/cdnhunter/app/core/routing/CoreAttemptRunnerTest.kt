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

class CoreAttemptRunnerTest {

    /** Shared by all fakes of one test: how many engines are alive right now, and the high-water mark. */
    private class World {
        var aliveNow = 0
        var maxAlive = 0
        val created = mutableListOf<CoreType>()
        val events = mutableListOf<CoreEvent>()
    }

    private class FakeCore(
        override val type: CoreType,
        private val w: World,
        private val refuseToDie: Boolean = false,
        private val behavior: suspend (FakeCore) -> CoreOutcome,
    ) : VpnCore {
        var alive = false
        var connectCalls = 0
        var stopCalls = 0
        override suspend fun connect(): CoreOutcome {
            connectCalls++
            alive = true; w.aliveNow++; w.maxAlive = maxOf(w.maxAlive, w.aliveNow)
            return behavior(this)
        }
        override suspend fun stop() {
            stopCalls++
            if (alive && !refuseToDie) { alive = false; w.aliveNow-- }
        }
        override fun isAlive() = alive
    }

    private val ok: suspend (FakeCore) -> CoreOutcome = { CoreOutcome.Connected }
    private fun fail(code: ErrorCode): suspend (FakeCore) -> CoreOutcome =
        { CoreOutcome.Failed(ConnectionError.CoreError(code, "boom")) }
    private val hang: suspend (FakeCore) -> CoreOutcome = { awaitCancellation() }

    private val both = CorePlan.Route(listOf(CoreType.CLASH_META, CoreType.SING_BOX), "test")
    private val singOnly = CorePlan.Route(listOf(CoreType.SING_BOX), "test")

    private fun runner(
        w: World, plan: CorePlan.Route, cores: Map<CoreType, FakeCore>,
        current: () -> Boolean = { true }, timeoutMs: Long = 5_000L,
    ) = CoreAttemptRunner(
        attemptId = 42, plan = plan,
        createCore = { t -> w.created += t; cores[t] ?: throw AssertionError("$t must not be created") },
        isCurrent = current, emit = { w.events += it }, perCoreTimeoutMs = timeoutMs,
    )

    // 1. Clash success -> sing-box never even created
    @Test fun clashSuccessNeverTouchesSingBox() = runBlocking {
        val w = World(); val clash = FakeCore(CoreType.CLASH_META, w, behavior = ok)
        val r = runner(w, both, mapOf(CoreType.CLASH_META to clash)).run()
        assertEquals(CoreType.CLASH_META, (r as RunResult.Connected).core)
        assertEquals(listOf(CoreType.CLASH_META), w.created)
    }

    // 2. Clash real failure -> clash fully stopped, then sing-box succeeds, exactly once each
    @Test fun clashFailureFallsBackToSingBoxExactlyOnce() = runBlocking {
        val w = World()
        val clash = FakeCore(CoreType.CLASH_META, w, behavior = fail(ErrorCode.CORE_START_FAILED))
        val sing = FakeCore(CoreType.SING_BOX, w, behavior = ok)
        val r = runner(w, both, mapOf(CoreType.CLASH_META to clash, CoreType.SING_BOX to sing)).run()
        assertEquals(CoreType.SING_BOX, (r as RunResult.Connected).core)
        assertEquals(listOf(CoreType.CLASH_META, CoreType.SING_BOX), w.created)
        assertEquals(1, clash.connectCalls); assertEquals(1, sing.connectCalls)
        assertFalse("the failed engine must be stopped", clash.alive)
        assertEquals("never two engines at once", 1, w.maxAlive)
    }

    // 3. both fail -> one error, nobody retried, no loop
    @Test fun bothFailingEndsInOneErrorWithoutALoop() = runBlocking {
        val w = World()
        val clash = FakeCore(CoreType.CLASH_META, w, behavior = fail(ErrorCode.CORE_START_FAILED))
        val sing = FakeCore(CoreType.SING_BOX, w, behavior = fail(ErrorCode.CORE_CRASHED))
        val r = runner(w, both, mapOf(CoreType.CLASH_META to clash, CoreType.SING_BOX to sing)).run() as RunResult.Failed
        assertEquals(ErrorCode.CORE_CRASHED, r.error.code)
        assertEquals(listOf(CoreType.CLASH_META, CoreType.SING_BOX), r.tried)
        assertEquals(1, clash.connectCalls); assertEquals(1, sing.connectCalls)
        assertTrue(w.events.last() is CoreEvent.Exhausted)
        assertEquals(0, w.aliveNow)
    }

    // 4. A hang is a timeout FAILURE of that engine -> fallback; it never becomes CONNECTED
    @Test fun clashTimeoutFallsBackAndNeverBecomesConnected() = runBlocking {
        val w = World()
        val clash = FakeCore(CoreType.CLASH_META, w, behavior = hang)
        val sing = FakeCore(CoreType.SING_BOX, w, behavior = ok)
        val r = runner(w, both, mapOf(CoreType.CLASH_META to clash, CoreType.SING_BOX to sing), timeoutMs = 50).run()
        assertEquals(CoreType.SING_BOX, (r as RunResult.Connected).core)
        val f = w.events.filterIsInstance<CoreEvent.Failed>().single()
        assertEquals(CoreType.CLASH_META, f.core); assertEquals(ErrorCode.CONNECTION_TIMEOUT, f.error.code)
        assertFalse(clash.alive)
    }

    @Test fun bothTimingOutIsAnErrorNotAConnection() = runBlocking {
        val w = World()
        val r = runner(w, both, mapOf(
            CoreType.CLASH_META to FakeCore(CoreType.CLASH_META, w, behavior = hang),
            CoreType.SING_BOX to FakeCore(CoreType.SING_BOX, w, behavior = hang),
        ), timeoutMs = 50).run()
        assertTrue(r is RunResult.Failed)
        assertEquals(ErrorCode.CONNECTION_TIMEOUT, (r as RunResult.Failed).error.code)
        assertEquals(0, w.aliveNow)
    }

    // failures the other engine would meet too must NOT trigger a fallback
    @Test fun networkAndConfigFailuresDoNotFallBack() = runBlocking {
        for (err in listOf<ConnectionError>(ConnectionError.NetworkError("down"), ConnectionError.ConfigError(ErrorCode.INVALID_CONFIG, "bad"))) {
            val w = World()
            val clash = FakeCore(CoreType.CLASH_META, w, behavior = { CoreOutcome.Failed(err) })
            val r = runner(w, both, mapOf(CoreType.CLASH_META to clash)).run()
            assertTrue(r is RunResult.Failed)
            assertEquals(listOf(CoreType.CLASH_META), w.created)
        }
    }

    // K. REALITY plan: Clash Meta must not even be constructed; no fallback after a sing-box failure
    @Test fun singBoxOnlyPlanNeverConstructsClashAndNeverFallsBack() = runBlocking {
        val w = World(); val sing = FakeCore(CoreType.SING_BOX, w, behavior = fail(ErrorCode.CORE_START_FAILED))
        val r = runner(w, singOnly, mapOf(CoreType.SING_BOX to sing)).run()
        assertTrue(r is RunResult.Failed)
        assertEquals(listOf(CoreType.SING_BOX), w.created)
        assertFalse(sing.alive)
    }

    // a previous engine that will not die blocks the next one from starting
    @Test fun secondEngineIsNotStartedWhileTheFirstIsStillAlive() = runBlocking {
        val w = World()
        val clash = FakeCore(CoreType.CLASH_META, w, refuseToDie = true, behavior = fail(ErrorCode.CORE_START_FAILED))
        val r = runner(w, both, mapOf(CoreType.CLASH_META to clash)).run()
        assertTrue(r is RunResult.Failed)
        assertEquals(listOf(CoreType.CLASH_META), w.created)
    }

    // L. a late "connected" from an attempt that was already superseded publishes nothing
    @Test fun staleConnectedFromASupersededAttemptIsIgnored() = runBlocking {
        val w = World(); val current = AtomicBoolean(true)
        val clash = FakeCore(CoreType.CLASH_META, w, behavior = { current.set(false); CoreOutcome.Connected })
        val r = runner(w, both, mapOf(CoreType.CLASH_META to clash), current = { current.get() }).run()
        assertTrue(r is RunResult.Cancelled)
        assertTrue(w.events.none { it is CoreEvent.Connected })
        assertFalse(clash.alive)
        assertEquals(listOf(CoreType.CLASH_META), w.created)
    }

    // N. disconnect (cancel) while the fallback engine is starting -> both engines are dead
    @Test fun cancelDuringFallbackLeavesNothingRunning() = runBlocking {
        val w = World(); val started = CompletableDeferred<Unit>()
        val clash = FakeCore(CoreType.CLASH_META, w, behavior = fail(ErrorCode.CORE_START_FAILED))
        val sing = FakeCore(CoreType.SING_BOX, w, behavior = { started.complete(Unit); awaitCancellation() })
        val job = launch { runner(w, both, mapOf(CoreType.CLASH_META to clash, CoreType.SING_BOX to sing)).run() }
        started.await()
        job.cancel(); job.join()
        assertFalse(clash.alive); assertFalse(sing.alive); assertEquals(0, w.aliveNow)
    }

    // every event belongs to the attempt that produced it
    @Test fun allEventsCarryTheAttemptId() = runBlocking {
        val w = World()
        runner(w, both, mapOf(
            CoreType.CLASH_META to FakeCore(CoreType.CLASH_META, w, behavior = fail(ErrorCode.CORE_START_FAILED)),
            CoreType.SING_BOX to FakeCore(CoreType.SING_BOX, w, behavior = ok),
        )).run()
        assertTrue(w.events.isNotEmpty())
        assertTrue(w.events.all { it.attemptId == 42L })
        assertTrue(w.events.any { it is CoreEvent.Switching })
    }

    // an engine that throws instead of returning is a failure of that engine, not a crash of the attempt
    @Test fun anExceptionFromAnEngineIsAFailureAndFallsBack() = runBlocking {
        val w = World()
        val clash = FakeCore(CoreType.CLASH_META, w, behavior = { throw IllegalStateException("native crash") })
        val sing = FakeCore(CoreType.SING_BOX, w, behavior = ok)
        val r = runner(w, both, mapOf(CoreType.CLASH_META to clash, CoreType.SING_BOX to sing)).run()
        assertEquals(CoreType.SING_BOX, (r as RunResult.Connected).core)
    }
}
