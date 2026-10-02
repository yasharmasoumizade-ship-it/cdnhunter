package com.cdnhunter.app.core

import java.util.concurrent.CopyOnWriteArrayList

/**
 * The connection lifecycle.
 *
 *   IDLE -> PREPARING -> CONNECTING -> CONNECTED -> DISCONNECTING -> DISCONNECTED
 *   CONNECTING -> ERROR -> RECONNECTING -> ...
 *
 * [IDLE] only exists before the first connect of a process; after any disconnect the
 * state is [DISCONNECTED]. The legal moves are in [ConnectionTransitions] and enforced
 * by [ConnectionStore] — an illegal one is refused and logged rather than applied, so a
 * late or duplicated event can never put the app in a state the lifecycle cannot reach.
 */
enum class ConnectionState {
    IDLE,
    PREPARING,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    DISCONNECTED,
    ERROR,
    RECONNECTING,
}

object ConnectionTransitions {

    private val allowed: Map<ConnectionState, Set<ConnectionState>> = mapOf(
        ConnectionState.IDLE to setOf(
            ConnectionState.PREPARING,
            // A disconnect with nothing running still has to be answerable.
            ConnectionState.DISCONNECTED,
        ),
        ConnectionState.PREPARING to setOf(
            ConnectionState.CONNECTING, ConnectionState.ERROR, ConnectionState.DISCONNECTING,
        ),
        ConnectionState.CONNECTING to setOf(
            ConnectionState.CONNECTED, ConnectionState.ERROR, ConnectionState.DISCONNECTING,
        ),
        ConnectionState.CONNECTED to setOf(
            ConnectionState.DISCONNECTING, ConnectionState.ERROR, ConnectionState.RECONNECTING,
        ),
        ConnectionState.DISCONNECTING to setOf(ConnectionState.DISCONNECTED),
        ConnectionState.DISCONNECTED to setOf(ConnectionState.PREPARING),
        ConnectionState.ERROR to setOf(
            ConnectionState.RECONNECTING, ConnectionState.PREPARING,
            ConnectionState.DISCONNECTING, ConnectionState.DISCONNECTED,
        ),
        ConnectionState.RECONNECTING to setOf(
            ConnectionState.PREPARING, ConnectionState.CONNECTING,
            ConnectionState.ERROR, ConnectionState.DISCONNECTING,
        ),
    )

    fun canTransition(from: ConnectionState, to: ConnectionState): Boolean =
        allowed[from]?.contains(to) == true
}

/**
 * What is known about the live tunnel. Each field answers a different question and
 * none of them stands in for another:
 *
 *  - [serverAddress]      the proxy server the config dials (host as written in the config)
 *  - [serverIp]           that server's resolved address, if it could be resolved
 *  - [localIp]            this device's address on the physical network (Wi-Fi / cellular)
 *  - [tunnelInterface]    the VPN interface's name, e.g. `tun0`
 *  - [tunnelAddress]      the address assigned to that interface
 *  - [vpnGateway]         the next hop for tunnelled traffic — null for this app's point-to-point TUN
 *  - [publicIp]           the address the outside world sees, asked through the tunnel
 *
 * Null means "not known (yet)". The UI must treat every one as optional.
 */
data class TunnelInfo(
    val serverAddress: String? = null,
    val serverIp: String? = null,
    val localIp: String? = null,
    val tunnelInterface: String? = null,
    val tunnelAddress: String? = null,
    val vpnGateway: String? = null,
    val publicIp: String? = null,
    /**
     * The lookup that fills [publicIp] has finished without an address. Separate from [publicIp]
     * being null (which also means "still asking") so the UI can tell waiting from gave-up; the
     * tunnel itself is unaffected -- a missing exit IP is never a connection failure.
     */
    val publicIpFailed: Boolean = false,
    val osValidated: Boolean? = null,
)

data class ConnectionSnapshot(
    val state: ConnectionState = ConnectionState.IDLE,
    val connectionId: String? = null,
    val configId: String? = null,
    val error: ConnectionError? = null,
    val reconnectAttempt: Int = 0,
    val maxReconnectAttempts: Int = 0,
    val stage: String = "",
    val stateSinceMs: Long = 0L,
    val connectedAtMs: Long = 0L,
    val tunnel: TunnelInfo? = null,
    /** True while the kill switch is holding the tunnel with traffic blocked after the core went away. */
    val killSwitchHolding: Boolean = false,
    /**
     * Only meaningful in [ConnectionState.ERROR]: true when the failure is about to be
     * followed by an automatic reconnect attempt, so the UI can treat it as a transient
     * hiccup (keep "connecting…", no error toast) rather than as the final outcome.
     */
    val willRetry: Boolean = false,
) {
    val isConnected: Boolean get() = state == ConnectionState.CONNECTED

    /**
     * An attempt to get a tunnel up is in flight, including the wait before a retry. An ERROR
     * that is about to be retried counts too: it lasts microseconds, and an observer that
     * happened to sample it would otherwise show "failed" in the middle of a reconnect.
     */
    val isConnecting: Boolean
        get() = state == ConnectionState.PREPARING ||
            state == ConnectionState.CONNECTING ||
            state == ConnectionState.RECONNECTING ||
            (state == ConnectionState.ERROR && willRetry)

    /** Anything other than a settled "not connected" state. */
    val isActive: Boolean
        get() = isConnecting || state == ConnectionState.CONNECTED || state == ConnectionState.DISCONNECTING
}

/**
 * The single source of truth for connection state.
 *
 * Exactly one writer is expected — the service's command loop — but the store does not
 * rely on it: every write is serialised, validated against [ConnectionTransitions], and
 * can be fenced to one connection id ([expectedConnectionId]) so that a coroutine from a
 * superseded attempt that is still winding down cannot overwrite the state of the attempt
 * that replaced it.
 *
 * The UI only ever reads [snapshot] or registers a listener. Listeners are invoked
 * outside the lock, on whichever thread made the change.
 */
class ConnectionStore(private val clock: () -> Long = { System.currentTimeMillis() }) {

    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<(ConnectionSnapshot) -> Unit>()

    @Volatile
    var snapshot: ConnectionSnapshot = ConnectionSnapshot()
        private set

    /** Registers [listener], immediately delivers the current snapshot, returns a function that unregisters. */
    fun addListener(listener: (ConnectionSnapshot) -> Unit): () -> Unit {
        listeners.add(listener)
        try {
            listener(snapshot)
        } catch (_: Exception) {
        }
        return { listeners.remove(listener) }
    }

    /**
     * Moves to [to]. Returns false (and changes nothing) if the move is illegal or the
     * write was fenced out by [expectedConnectionId].
     *
     * Leaving a connection behind ([ConnectionState.DISCONNECTED], [ConnectionState.ERROR])
     * keeps the last connection id so the UI can still say which attempt failed.
     */
    fun transition(
        to: ConnectionState,
        connectionId: String? = null,
        configId: String? = null,
        error: ConnectionError? = null,
        reconnectAttempt: Int? = null,
        maxReconnectAttempts: Int? = null,
        stage: String? = null,
        expectedConnectionId: String? = null,
        killSwitchHolding: Boolean? = null,
        willRetry: Boolean = false,
    ): Boolean {
        val applied: ConnectionSnapshot
        synchronized(lock) {
            val cur = snapshot
            if (expectedConnectionId != null && cur.connectionId != expectedConnectionId) {
                ConnLog.d(expectedConnectionId, Stage.NETWORK, "stale transition to $to ignored (current=${cur.connectionId})")
                return false
            }
            if (!ConnectionTransitions.canTransition(cur.state, to)) {
                ConnLog.w(cur.connectionId, Stage.ERROR, "illegal transition ${cur.state} -> $to refused")
                return false
            }
            val now = clock()
            val fresh = to == ConnectionState.PREPARING && connectionId != null
            applied = cur.copy(
                state = to,
                connectionId = connectionId ?: cur.connectionId,
                configId = configId ?: cur.configId,
                // An error belongs to the transition into ERROR only; any other state clears it.
                error = if (to == ConnectionState.ERROR) error else null,
                reconnectAttempt = when {
                    reconnectAttempt != null -> reconnectAttempt
                    fresh -> 0
                    else -> cur.reconnectAttempt
                },
                maxReconnectAttempts = maxReconnectAttempts ?: cur.maxReconnectAttempts,
                stage = stage ?: "",
                stateSinceMs = now,
                connectedAtMs = when (to) {
                    ConnectionState.CONNECTED -> now
                    ConnectionState.DISCONNECTED, ConnectionState.ERROR, ConnectionState.IDLE -> 0L
                    else -> cur.connectedAtMs
                },
                tunnel = when (to) {
                    ConnectionState.CONNECTED, ConnectionState.DISCONNECTING -> cur.tunnel
                    else -> null
                },
                killSwitchHolding = killSwitchHolding ?: (to == ConnectionState.ERROR && cur.killSwitchHolding),
                willRetry = to == ConnectionState.ERROR && willRetry,
            )
            snapshot = applied
        }
        notify(applied)
        return true
    }

    /**
     * Changes details of the current state without changing the state itself — the
     * tunnel info arriving after CONNECTED, the stage label moving inside CONNECTING.
     * Fenced to [expectedConnectionId] like [transition].
     */
    fun update(expectedConnectionId: String, block: (ConnectionSnapshot) -> ConnectionSnapshot): Boolean {
        val applied: ConnectionSnapshot
        synchronized(lock) {
            val cur = snapshot
            if (cur.connectionId != expectedConnectionId) return false
            val next = block(cur)
            // The state is not this method's to change.
            applied = next.copy(state = cur.state, connectionId = cur.connectionId)
            snapshot = applied
        }
        notify(applied)
        return true
    }

    /** Test/diagnostic hook: back to a pristine IDLE snapshot. */
    fun reset() {
        val applied: ConnectionSnapshot
        synchronized(lock) {
            applied = ConnectionSnapshot()
            snapshot = applied
        }
        notify(applied)
    }

    private fun notify(s: ConnectionSnapshot) {
        for (l in listeners) {
            try {
                l(s)
            } catch (_: Exception) {
                // One broken observer must not stop the others from seeing the change.
            }
        }
    }
}
