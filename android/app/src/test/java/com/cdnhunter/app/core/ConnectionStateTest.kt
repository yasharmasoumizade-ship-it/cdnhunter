package com.cdnhunter.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionStateTest {

    private fun store() = ConnectionStore { 1_000L }

    @Test fun happyPathWalksTheDocumentedLifecycle() {
        val s = store()
        assertEquals(ConnectionState.IDLE, s.snapshot.state)
        assertTrue(s.transition(ConnectionState.PREPARING, connectionId = "c1", configId = "cfg"))
        assertTrue(s.transition(ConnectionState.CONNECTING, expectedConnectionId = "c1"))
        assertTrue(s.transition(ConnectionState.CONNECTED, expectedConnectionId = "c1"))
        assertTrue(s.snapshot.isConnected)
        assertEquals(1_000L, s.snapshot.connectedAtMs)
        assertTrue(s.transition(ConnectionState.DISCONNECTING, expectedConnectionId = "c1"))
        assertTrue(s.transition(ConnectionState.DISCONNECTED, expectedConnectionId = "c1"))
        assertFalse(s.snapshot.isActive)
    }

    @Test fun illegalTransitionsAreRefusedAndChangeNothing() {
        val s = store()
        assertFalse(s.transition(ConnectionState.CONNECTED))            // IDLE -> CONNECTED
        assertEquals(ConnectionState.IDLE, s.snapshot.state)
        s.transition(ConnectionState.PREPARING, connectionId = "c1")
        assertFalse(s.transition(ConnectionState.CONNECTED))            // PREPARING -> CONNECTED skips CONNECTING
        assertFalse(s.transition(ConnectionState.DISCONNECTED))         // must pass through DISCONNECTING
        assertEquals(ConnectionState.PREPARING, s.snapshot.state)
    }

    @Test fun staleAttemptCannotOverwriteANewerOne() {
        val s = store()
        s.transition(ConnectionState.PREPARING, connectionId = "old")
        s.transition(ConnectionState.DISCONNECTING)
        s.transition(ConnectionState.DISCONNECTED)
        s.transition(ConnectionState.PREPARING, connectionId = "new")
        // A coroutine from the superseded attempt wakes up late and tries to report progress.
        assertFalse(s.transition(ConnectionState.CONNECTING, expectedConnectionId = "old"))
        assertFalse(s.update("old") { it.copy(stage = "x") })
        assertEquals(ConnectionState.PREPARING, s.snapshot.state)
        assertEquals("new", s.snapshot.connectionId)
    }

    @Test fun errorCarriesItsErrorAndAnyOtherStateClearsIt() {
        val s = store()
        val err = ConnectionError.TimeoutError("no answer")
        s.transition(ConnectionState.PREPARING, connectionId = "c1")
        s.transition(ConnectionState.CONNECTING)
        assertTrue(s.transition(ConnectionState.ERROR, error = err))
        assertEquals(ErrorCode.CONNECTION_TIMEOUT, s.snapshot.error?.code)
        assertTrue(s.transition(ConnectionState.RECONNECTING, reconnectAttempt = 1))
        assertNull(s.snapshot.error)
        assertEquals(1, s.snapshot.reconnectAttempt)
    }

    @Test fun errorThenUserCanConnectAgain() {
        val s = store()
        s.transition(ConnectionState.PREPARING, connectionId = "c1")
        s.transition(ConnectionState.ERROR, error = ConnectionError.ConfigError(ErrorCode.INVALID_CONFIG, "bad"))
        assertTrue(s.transition(ConnectionState.PREPARING, connectionId = "c2"))
        assertEquals(0, s.snapshot.reconnectAttempt)
    }

    @Test fun tunnelInfoIsDroppedWhenTheConnectionIsNotUp() {
        val s = store()
        s.transition(ConnectionState.PREPARING, connectionId = "c1")
        s.transition(ConnectionState.CONNECTING)
        s.transition(ConnectionState.CONNECTED)
        assertTrue(s.update("c1") { it.copy(tunnel = TunnelInfo(publicIp = "203.0.113.9")) })
        assertEquals("203.0.113.9", s.snapshot.tunnel?.publicIp)
        s.transition(ConnectionState.DISCONNECTING)
        s.transition(ConnectionState.DISCONNECTED)
        assertNull(s.snapshot.tunnel)
    }

    @Test fun updateCannotChangeTheState() {
        val s = store()
        s.transition(ConnectionState.PREPARING, connectionId = "c1")
        s.update("c1") { it.copy(state = ConnectionState.CONNECTED, stage = "probing") }
        assertEquals(ConnectionState.PREPARING, s.snapshot.state)
        assertEquals("probing", s.snapshot.stage)
    }

    @Test fun listenersGetCurrentValueThenEveryChangeUntilRemoved() {
        val s = store()
        val seen = ArrayList<ConnectionState>()
        val remove = s.addListener { seen += it.state }
        s.transition(ConnectionState.PREPARING, connectionId = "c1")
        s.transition(ConnectionState.CONNECTING)
        remove()
        s.transition(ConnectionState.CONNECTED)
        assertEquals(listOf(ConnectionState.IDLE, ConnectionState.PREPARING, ConnectionState.CONNECTING), seen)
    }

    @Test fun aThrowingListenerDoesNotBlockOthers() {
        val s = store()
        var got = 0
        s.addListener { if (it.state != ConnectionState.IDLE) throw IllegalStateException("boom") }
        s.addListener { got++ }
        s.transition(ConnectionState.PREPARING, connectionId = "c1")
        assertEquals(2, got) // initial + one change
    }

    @Test fun killSwitchHoldingSurvivesIntoErrorOnlyWhenSet() {
        val s = store()
        s.transition(ConnectionState.PREPARING, connectionId = "c1")
        s.transition(ConnectionState.CONNECTING)
        s.transition(ConnectionState.CONNECTED)
        s.transition(ConnectionState.ERROR, error = ConnectionError.CoreError(ErrorCode.CORE_CRASHED, "died"), killSwitchHolding = true)
        assertTrue(s.snapshot.killSwitchHolding)
        s.transition(ConnectionState.PREPARING, connectionId = "c2")
        assertFalse(s.snapshot.killSwitchHolding)
    }

    @Test fun concurrentWritersNeverProduceAnIllegalState() {
        val s = store()
        s.transition(ConnectionState.PREPARING, connectionId = "c1")
        val threads = (1..8).map {
            Thread {
                repeat(500) {
                    s.transition(ConnectionState.CONNECTING)
                    s.transition(ConnectionState.CONNECTED)
                    s.transition(ConnectionState.DISCONNECTING)
                    s.transition(ConnectionState.DISCONNECTED)
                    s.transition(ConnectionState.PREPARING, connectionId = "c1")
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertTrue(s.snapshot.state in ConnectionState.values())
    }
}
