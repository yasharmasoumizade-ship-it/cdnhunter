package com.cdnhunter.app.vpn.singbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LibboxEngineTest {
    private fun engine(b: FakeBackend) = LibboxEngine(b, ::testParams, io = Dispatchers.IO)

    @Test fun startupFollowsLibboxsRequiredOrder() = runBlocking {
        val b = FakeBackend(); val e = engine(b)
        e.start("{}")
        assertEquals(listOf("setup", "newServer", "start", "startOrReload"), b.calls)
        assertTrue(e.isAlive())
    }

    @Test fun theCommandServerIsSetUpOnUnixSocketWithTheGivenSecretOnly() = runBlocking {
        val b = FakeBackend(); engine(b).start("{}")
        assertEquals(0, b.setupParams!!.listenPort)
        assertEquals(testParams().secret, b.setupParams!!.secret)
    }

    @Test fun aFailedStartClosesWhatWasCreatedAndRethrows() = runBlocking {
        val b = FakeBackend().apply { failAt = "startOrReload" }; val e = engine(b)
        try { e.start("{}"); throw AssertionError("must throw") } catch (_: IllegalStateException) { }
        assertFalse(e.isAlive())
        assertEquals(1, b.servers.single().closeServiceCalls); assertEquals(1, b.servers.single().closeCalls)
    }

    @Test fun aFailureBeforeTheServerExistsLeavesNothingToClose() = runBlocking {
        val b = FakeBackend().apply { failAt = "setup" }
        try { engine(b).start("{}"); throw AssertionError("must throw") } catch (_: IllegalStateException) { }
        assertTrue(b.servers.isEmpty())
    }

    @Test fun serviceStopWhileRunningIsReportedAsATermination() = runBlocking {
        val b = FakeBackend(); val e = engine(b); e.start("{}")
        b.servers.single().events.onServiceStop()
        assertEquals("libbox reported serviceStop", withTimeout(2000) { e.awaitTermination() })
        assertFalse(e.isAlive())
    }

    @Test fun aServiceStopCausedByOurOwnStopIsNotACrash() = runBlocking {
        val b = FakeBackend().apply { onCloseService = { servers.single().events.onServiceStop() } }
        val e = engine(b); e.start("{}")
        e.stop()
        assertEquals("stopped", withTimeout(2000) { e.awaitTermination() })
    }

    @Test fun stopIsIdempotentAndClosesEachThingOnce() = runBlocking {
        val b = FakeBackend(); val e = engine(b); e.start("{}")
        e.stop(); e.stop(); e.stop()
        val s = b.servers.single()
        assertEquals(1, s.closeServiceCalls); assertEquals(1, s.closeCalls); assertFalse(e.isAlive())
    }

    @Test fun stopOnAnEngineThatNeverStartedIsSafe() = runBlocking {
        val e = engine(FakeBackend()); e.stop(); e.stop()
        try { e.start("{}"); throw AssertionError("a stopped engine must not start") } catch (_: IllegalStateException) { }
    }

    @Test fun anEngineInstanceCannotBeStartedTwice() = runBlocking {
        val e = engine(FakeBackend()); e.start("{}")
        try { e.start("{}"); throw AssertionError("must throw") } catch (_: IllegalStateException) { }
    }

    // stop() must not wait for a start() that is blocked inside libbox
    @Test fun stopDuringABlockedStartDoesNotDeadlock() = runBlocking {
        val latch = CountDownLatch(1)
        val b = FakeBackend().apply { onStartOrReload = { latch.await(10, TimeUnit.SECONDS) }; onCloseService = { latch.countDown() } }
        val e = engine(b)
        val starting = async { runCatching { e.start("{}") } }
        val deadline = System.currentTimeMillis() + 3000
        while ("startOrReload" !in b.calls && System.currentTimeMillis() < deadline) Thread.sleep(10)
        withTimeout(4000) { e.stop() }
        assertTrue("the aborted start must fail", withTimeout(4000) { starting.await() }.isFailure)
        assertFalse(e.isAlive())
        assertEquals(1, b.servers.single().closeServiceCalls)
    }

    // stale callback: an old attempt's engine can never touch a newer attempt's engine
    @Test fun aLateCallbackFromAStoppedEngineDoesNotAffectTheNextAttempt() = runBlocking {
        val b1 = FakeBackend(); val old = engine(b1); old.start("{}")
        old.stop()
        val b2 = FakeBackend(); val fresh = engine(b2); fresh.start("{}")
        b1.servers.single().events.onServiceStop() // arrives late, from the previous attempt
        assertTrue(fresh.isAlive())
        assertFalse(fresh.awaitTerminationDone())
    }

    private fun LibboxEngine.awaitTerminationDone(): Boolean = runBlocking {
        try { withTimeout(150) { awaitTermination() }; true } catch (_: Exception) { false }
    }
}
