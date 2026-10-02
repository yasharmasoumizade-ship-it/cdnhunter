package com.cdnhunter.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import androidx.core.app.NotificationCompat
import com.cdnhunter.app.core.ConfigValidator
import com.cdnhunter.app.core.ConnLog
import com.cdnhunter.app.core.ConnectionError
import com.cdnhunter.app.core.ConnectionSettings
import com.cdnhunter.app.core.ConnectionState
import com.cdnhunter.app.core.ConnectionStore
import com.cdnhunter.app.core.ErrorCode
import com.cdnhunter.app.core.InternalConnectionConfig
import com.cdnhunter.app.core.ProbeResult
import com.cdnhunter.app.core.ProxyProbe
import com.cdnhunter.app.core.ReconnectPolicy
import com.cdnhunter.app.core.SettingsValidator
import com.cdnhunter.app.core.Stage
import com.cdnhunter.app.core.TunnelInfo
import com.cdnhunter.app.core.ValidatedConfig
import com.cdnhunter.app.core.ValidationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The VPN service: owns the tunnel interface and the embedded core, and is the ONLY
 * writer of connection state.
 *
 * ## Shape
 *
 *  - [store] is the single source of truth for state. The UI observes it; nothing in the UI
 *    decides whether the app is connected.
 *  - Every request — connect, disconnect, "network came back", "an attempt ended" — is a
 *    [Command] posted to one channel and handled one at a time by [commandLoop]. That
 *    serialisation is what removes the races (double connect, disconnect while connecting,
 *    switching server mid-connect): two commands can never run their bodies concurrently.
 *  - A connection attempt ([runAttempt]) is a cancellable coroutine that walks the stages
 *    PREPARING -> CONNECTING -> verification -> CONNECTED, then monitors health. It never
 *    reconnects by calling itself; on failure it reports back to the loop, which decides.
 *  - "Connected" is declared only after traffic has actually been carried through the
 *    server (see [ProxyProbe]) — the core starting without an exception proves nothing.
 *
 * ## File descriptor ownership
 *
 * The service holds the TUN's original descriptor ([tunPfd]) for the life of the interface
 * and gives the core a duplicate. The core closes its duplicate when it stops (so the app
 * must not close that one); the service closes its own exactly once, and only when the
 * interface should go away. This is what makes retries, the kill switch and teardown after
 * a failed start safe — previously the single descriptor was handed to the core, closed by
 * it on stop, and then reused or adopted again by Kotlin code.
 */
class CdnVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.cdnhunter.app.START_VPN"
        const val ACTION_STOP = "com.cdnhunter.app.STOP_VPN"
        const val CHANNEL_ID = "cdnhunter_vpn"
        const val NOTIFICATION_ID = 1

        /** The address the TUN interface is given; also how its link is recognised afterwards. */
        const val TUN_ADDRESS_V4 = "10.10.10.10"
        const val MIXED_PORT = 10808

        private const val VERIFY_TIMEOUT_MS = 15_000L
        private const val CORE_CHECK_INTERVAL_MS = 5_000L
        private const val PROBE_INTERVAL_MS = 30_000L
        private const val PROBE_INTERVAL_AFTER_FAILURE_MS = 5_000L
        private const val PROBE_FAILURES_BEFORE_RECONNECT = 3
        private const val ATTEMPT_JOIN_TIMEOUT_MS = 10_000L

        /** The single source of truth for connection state. The UI reads or observes this and nothing else. */
        val store = ConnectionStore()

        /**
         * Read-only views of [store] for code that still asks "is it connected?". They are
         * computed from the store on every call, never set — there is no second copy of the
         * state to fall out of sync.
         */
        class StateFlag(private val read: () -> Boolean) {
            fun get(): Boolean = read()
        }

        val isRunning = StateFlag { store.snapshot.isConnected }
        val isConnecting = StateFlag { store.snapshot.isConnecting }

        @Volatile var uploadBytes = 0L
        @Volatile var downloadBytes = 0L

        /** Last failure, redacted, for the Settings diagnostics row. */
        @Volatile var lastError = ""

        /** The connection log (already redacted), for the copyable diagnostics dump. */
        val debugLog: String get() = ConnLog.dump()

        // The server's REAL location, asked of a geo-IP service THROUGH the tunnel (see
        // GeoService.lookupCurrentExitGeoInfo) rather than inferred from the config's
        // hostname, which for CDN-fronted domains reports the CDN edge. Filled in once per
        // successful connection; blank until then. Prefer it over any pre-connect estimate
        // once non-blank and exitGeoConfigId matches the active config.
        @Volatile var exitCountryCode = ""
        @Volatile var exitCity = ""
        @Volatile var exitGeoConfigId = ""

        @Volatile var instance: CdnVpnService? = null

        fun start(context: Context) {
            val intent = Intent(context, CdnVpnService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, CdnVpnService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }

    // ── commands ─────────────────────────────────────────────────────────────

    private sealed class Command {
        class Connect(val configId: String, val reuseTun: Boolean = false, val fromIntent: Boolean = false) : Command()
        object Disconnect : Command()
        object NetworkRestored : Command()
        class AttemptFailed(
            val serial: Int,
            val connectionId: String,
            val error: ConnectionError,
            val wasConnected: Boolean,
        ) : Command()
    }

    /** Thrown inside an attempt to unwind with a typed error. */
    private class AttemptFailure(val error: ConnectionError) : Exception(error.technical)

    /** Everything one connection (including its reconnect attempts) is built from, captured once. */
    private class Plan(
        val connectionId: String,
        val configId: String,
        val uri: String,
        val settings: ConnectionSettings,
        val reuseTun: Boolean,
    ) {
        /** Set once a REALITY handshake has been seen to need support-x25519mlkem768. Sticks for the connection. */
        @Volatile var forceX25519 = false

        /**
         * Whether this connection has ever reached CONNECTED. The kill switch protects a tunnel that
         * was up; it must not blackhole the network because a first attempt failed.
         */
        @Volatile var everConnected = false
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val commands = Channel<Command>(Channel.UNLIMITED)

    // Touched only from the command loop (and onDestroy after the loop is cancelled).
    private var attemptJob: Job? = null
    private var plan: Plan? = null
    private val attemptSerial = AtomicInteger(0)

    @Volatile private var lastStartId = 0

    /**
     * ACTION_START intents posted but not yet handled by the loop. While any are pending the
     * service must not drop its foreground status: a disconnect immediately followed by a connect
     * (switching server) would otherwise remove the notification the connect still needs.
     */
    private val pendingStarts = AtomicInteger(0)

    // ── resources ────────────────────────────────────────────────────────────

    private val resLock = Any()
    /** The service's own handle on the TUN interface. See the class comment. */
    private var tunPfd: ParcelFileDescriptor? = null
    private var drainThread: Thread? = null
    private val killSwitchBlocking = AtomicBoolean(false)
    /** Set by the network callback; makes the monitor probe immediately instead of waiting for its interval. */
    private val probeNow = AtomicBoolean(false)

    // ── network callback ─────────────────────────────────────────────────────

    private var hadNetwork = true
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // ── lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        instance = this
        ConnLog.sink = { level, line ->
            when (level) {
                ConnLog.Level.ERROR -> android.util.Log.e("CdnVpn", line)
                ConnLog.Level.WARN -> android.util.Log.w("CdnVpn", line)
                else -> android.util.Log.i("CdnVpn", line)
            }
        }
        createNotificationChannel()
        registerNetworkCallback()
        scope.launch { commandLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            ACTION_START -> {
                // startForegroundService() obliges us to call startForeground() quickly, so do
                // it before anything asynchronous — even if this request ends up ignored.
                try {
                    startForeground(NOTIFICATION_ID, buildNotification("Connecting…"))
                } catch (e: Exception) {
                    ConnLog.w(null, Stage.CONNECTED, "startForeground refused: ${e.javaClass.simpleName}")
                }
                val configId = SecurePrefs.vpn(this).getString("active_config_id", "") ?: ""
                pendingStarts.incrementAndGet()
                commands.trySend(Command.Connect(configId, fromIntent = true))
            }
            ACTION_STOP -> commands.trySend(Command.Disconnect)
            else -> {
                // A restart with no intent (the process was killed): there is no tunnel any
                // more and nothing asked for one, so do not sit around as an idle service.
                if (!store.snapshot.isActive) stopSelfResult(startId)
            }
        }
        // Not sticky: a killed process takes the tunnel with it, and a null-intent restart
        // would only produce a service that does nothing.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // The process is being torn down by the OS: finish synchronously. Everything below
        // is bounded (no unbounded join), so this cannot hang the main thread.
        scope.cancel()
        commands.close()
        killSwitchBlocking.set(false)
        stopDrainBlocking()
        MihomoBridge.stop()
        closeTun()
        clearExit()
        val s = store.snapshot
        if (s.isActive || s.state == ConnectionState.ERROR) {
            store.transition(ConnectionState.DISCONNECTING)
            store.transition(ConnectionState.DISCONNECTED)
        }
        networkCallback?.let { try { connectivityManager?.unregisterNetworkCallback(it) } catch (_: Exception) {} }
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** The user (or another VPN app) revoked this app's VPN permission from system settings. */
    override fun onRevoke() {
        ConnLog.i(store.snapshot.connectionId, Stage.DISCONNECT_REQUESTED, "VPN permission revoked by the system")
        killSwitchBlocking.set(false)
        commands.trySend(Command.Disconnect)
        super.onRevoke()
    }

    // ── command loop ─────────────────────────────────────────────────────────

    private suspend fun commandLoop() {
        for (cmd in commands) {
            try {
                when (cmd) {
                    is Command.Connect -> {
                        if (cmd.fromIntent) pendingStarts.decrementAndGet()
                        onConnect(cmd)
                    }
                    Command.Disconnect -> onDisconnect()
                    Command.NetworkRestored -> onNetworkRestored()
                    is Command.AttemptFailed -> onAttemptFailed(cmd)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A bug in a handler must not kill the loop — later commands (above all Disconnect)
                // still have to be served.
                ConnLog.e(store.snapshot.connectionId, Stage.ERROR, "command handler failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    private suspend fun onConnect(cmd: Command.Connect) {
        val snap = store.snapshot
        val sameConfig = snap.configId == cmd.configId
        if (snap.isConnecting && sameConfig && attemptJob?.isActive == true) {
            ConnLog.i(snap.connectionId, Stage.CONNECTED, "connect ignored: already connecting to this config")
            return
        }
        if (snap.isConnected && sameConfig) {
            ConnLog.i(snap.connectionId, Stage.CONNECTED, "connect ignored: already connected to this config")
            return
        }
        if (cmd.configId.isBlank()) {
            fail(null, ConnectionError.ConfigError(ErrorCode.INVALID_CONFIG, "no server is selected", "active_config_id"))
            return
        }

        // Anything else is a new request that supersedes whatever is there — a different server,
        // a retry after an error, a connect while a previous attempt is winding down.
        val keepTun = cmd.reuseTun && snap.killSwitchHolding
        if (snap.isActive || attemptJob != null || (tunPfd != null && !keepTun)) {
            teardown(publish = snap.isActive || snap.state == ConnectionState.ERROR, keepTun = keepTun)
        }

        val settings = readSettings()
        val uri = SecurePrefs.vpn(this).getString("user_config", "") ?: ""
        val p = Plan(newConnectionId(), cmd.configId, uri, settings, keepTun)
        plan = p
        lastError = ""
        ConnLog.clear()
        val ok = store.transition(
            ConnectionState.PREPARING,
            connectionId = p.connectionId,
            configId = p.configId,
            maxReconnectAttempts = policyFor(settings).maxAttempts,
            stage = "preparing",
        )
        if (!ok) {
            ConnLog.w(p.connectionId, Stage.ERROR, "could not enter PREPARING from ${store.snapshot.state}")
            return
        }
        launchAttempt(p, attemptNo = 0, initialDelayMs = 0L)
    }

    private suspend fun onDisconnect() {
        val s = store.snapshot
        ConnLog.i(s.connectionId, Stage.DISCONNECT_REQUESTED, "state=${s.state}")
        killSwitchBlocking.set(false)
        val nothingToDo = !s.isActive && s.state != ConnectionState.ERROR && tunPfd == null && attemptJob == null
        if (!nothingToDo) teardown(publish = true, keepTun = false)
        finishService()
    }

    private suspend fun onNetworkRestored() {
        val s = store.snapshot
        when {
            s.state == ConnectionState.ERROR && s.killSwitchHolding && !s.configId.isNullOrBlank() &&
                readSettings().autoReconnect -> {
                ConnLog.i(s.connectionId, Stage.NETWORK, "network restored while the kill switch is holding — reconnecting")
                onConnect(Command.Connect(s.configId, reuseTun = true))
            }
            s.isConnected -> probeNow.set(true)
            else -> Unit
        }
    }

    private suspend fun onAttemptFailed(cmd: Command.AttemptFailed) {
        if (cmd.serial != attemptSerial.get() || store.snapshot.connectionId != cmd.connectionId) {
            ConnLog.d(cmd.connectionId, Stage.ERROR, "stale failure report ignored")
            return
        }
        val p = plan ?: return
        attemptJob = null
        lastError = cmd.error.technical
        ConnLog.error(cmd.connectionId, Stage.ERROR, cmd.error, coreOutput = recentCoreOutput())

        val policy = policyFor(p.settings)
        val used = store.snapshot.reconnectAttempt
        if (cmd.wasConnected) p.everConnected = true
        // Auto-reconnect exists for a connection that WAS up and dropped. A first connect that never
        // got traffic through (wrong config, blocked server, server rejecting the handshake) will fail
        // the same way again: retrying it only held the UI on "Connecting…" for minutes (3 retries x
        // the verification wait) before showing the error that was already known after the first try.
        val retry = p.everConnected && policy.shouldRetry(used, cmd.error, p.settings.autoReconnect)

        // The core is gone either way. The TUN stays only if the kill switch wants it held.
        val holdTun = p.settings.killSwitch && p.everConnected
        withContext(Dispatchers.IO) { stopCore() }

        if (retry) {
            val next = used + 1
            store.transition(ConnectionState.ERROR, error = cmd.error, expectedConnectionId = cmd.connectionId, willRetry = true)
            val wait = policy.delayMs(next)
            store.transition(
                ConnectionState.RECONNECTING, reconnectAttempt = next, maxReconnectAttempts = policy.maxAttempts,
                stage = "retrying in ${wait}ms", expectedConnectionId = cmd.connectionId,
            )
            ConnLog.i(cmd.connectionId, Stage.RECONNECT, "attempt $next/${policy.maxAttempts} in ${wait}ms")
            updateNotification("Reconnecting… ($next/${policy.maxAttempts})")
            if (!holdTun) closeTun()
            val replanned = Plan(p.connectionId, p.configId, p.uri, p.settings, reuseTun = holdTun).also {
                it.forceX25519 = p.forceX25519
                it.everConnected = p.everConnected
            }
            plan = replanned
            launchAttempt(replanned, attemptNo = next, initialDelayMs = wait)
            return
        }

        val finalError = if (used > 0 && cmd.error.retryable) {
            ConnectionError.ReconnectFailedError(used, "gave up after $used reconnect attempts; last: ${cmd.error.technical}")
        } else cmd.error

        if (p.settings.killSwitch && p.everConnected && tunPfd != null) {
            // Hold the interface up with traffic blocked rather than letting it fall back to the open network.
            killSwitchBlocking.set(true)
            startDrain()
            store.transition(ConnectionState.ERROR, error = finalError, expectedConnectionId = cmd.connectionId, killSwitchHolding = true)
            updateNotification("Blocked — connection lost (kill switch on)")
            ConnLog.w(cmd.connectionId, Stage.ERROR, "kill switch holding the tunnel with traffic blocked")
            return
        }

        store.transition(ConnectionState.ERROR, error = finalError, expectedConnectionId = cmd.connectionId)
        updateNotification("Error: ${finalError.userMessage(p.settings.language)}")
        closeTun()
        val id = cmd.connectionId
        scope.launch {
            // Leave the error visible in the notification briefly, then let go — unless a new request arrived.
            delay(2_000)
            if (store.snapshot.state == ConnectionState.ERROR && store.snapshot.connectionId == id) finishService()
        }
    }

    /** Ends the service if nothing is using it. */
    private fun finishService() {
        if (pendingStarts.get() > 0) return // a connect is queued behind this; it still needs the foreground notification
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelfResult(lastStartId)
    }

    // ── teardown ─────────────────────────────────────────────────────────────

    /**
     * Brings everything down in a fixed order, tolerating any of it already being down:
     * cancel the attempt (and wait for it, bounded), stop the core, then release the TUN
     * unless [keepTun]. [publish] walks the state through DISCONNECTING -> DISCONNECTED.
     */
    private suspend fun teardown(publish: Boolean, keepTun: Boolean) {
        val cid = store.snapshot.connectionId
        killSwitchBlocking.set(false)
        val job = attemptJob
        attemptJob = null
        if (job != null) {
            job.cancel()
            val finished = withTimeoutOrNull(ATTEMPT_JOIN_TIMEOUT_MS) { job.join(); true }
            if (finished == null) ConnLog.w(cid, Stage.ERROR, "attempt did not stop within ${ATTEMPT_JOIN_TIMEOUT_MS}ms; continuing teardown")
        }
        attemptSerial.incrementAndGet() // anything still running from the old attempt is now stale

        val before = store.snapshot.state
        val canPublish = publish && ConnectionTransitions_canDisconnect(before)
        if (canPublish) store.transition(ConnectionState.DISCONNECTING)

        withContext(Dispatchers.IO) {
            stopDrainBlocking()
            stopCore()
            if (!keepTun) closeTun()
        }
        clearExit()
        uploadBytes = 0L
        downloadBytes = 0L

        if (canPublish) {
            ConnLog.i(cid, Stage.DISCONNECTED, "teardown complete")
            store.transition(ConnectionState.DISCONNECTED)
        }
    }

    private fun ConnectionTransitions_canDisconnect(from: ConnectionState) =
        com.cdnhunter.app.core.ConnectionTransitions.canTransition(from, ConnectionState.DISCONNECTING)

    /** Stops the core (which closes its duplicate of the TUN descriptor). Idempotent. */
    private fun stopCore() {
        if (MihomoBridge.isRunning()) {
            MihomoBridge.stop()
            ConnLog.i(store.snapshot.connectionId, Stage.CORE_STOPPED, "core stopped")
        }
    }

    private fun closeTun() {
        synchronized(resLock) {
            try { tunPfd?.close() } catch (_: Exception) {}
            tunPfd = null
        }
    }

    private fun clearExit() {
        exitCountryCode = ""
        exitCity = ""
        exitGeoConfigId = ""
    }

    // ── one connection attempt ───────────────────────────────────────────────

    private fun launchAttempt(p: Plan, attemptNo: Int, initialDelayMs: Long) {
        val serial = attemptSerial.incrementAndGet()
        attemptJob = scope.launch { runAttempt(p, serial, attemptNo, initialDelayMs) }
    }

    private suspend fun runAttempt(p: Plan, serial: Int, attemptNo: Int, initialDelayMs: Long) {
        val cid = p.connectionId
        var connected = false
        try {
            if (initialDelayMs > 0) delay(initialDelayMs)

            // ── PREPARING ────────────────────────────────────────────────────
            if (store.snapshot.state != ConnectionState.PREPARING) {
                if (!store.transition(ConnectionState.PREPARING, expectedConnectionId = cid, stage = "preparing")) return
            }
            ConnLog.i(cid, Stage.CONFIG_LOADED, "attempt #$attemptNo, config ${p.configId}")
            val validated = prepareConfig(p)
            ConnLog.i(cid, Stage.CONFIG_VALIDATED, "${validated.config.protocol.wire} ${validated.config.server}:${validated.config.port}")
            // Publish where we are dialling as soon as it is known, so the UI can show the real
            // address while the attempt is still in flight. Off the attempt's path: it never delays
            // or fails a connect, and it is fenced to this attempt's connection id.
            scope.launch(Dispatchers.IO) { publishServerIp(cid, validated.config.server) }
            SettingsValidator.validate(p.settings)?.let { throw AttemptFailure(it) }
            if (prepare(this) != null) throw AttemptFailure(ConnectionError.PermissionError("VPN permission is not granted"))
            val homeDir = prepareGeoFiles()
            checkPrivateDnsStrictMode()?.let {
                ConnLog.w(cid, Stage.CONFIG_VALIDATED, "Android Private DNS is in strict mode ($it); it bypasses the tunnel's DNS handling")
            }
            yield()

            // ── CONNECTING ───────────────────────────────────────────────────
            if (!store.transition(ConnectionState.CONNECTING, expectedConnectionId = cid, stage = "tunnel")) return
            ConnLog.i(cid, Stage.TUNNEL_STARTING, "mtu=${p.settings.mtu} ipv6=${p.settings.ipv6}")
            val pfd = obtainTun(p)
            MihomoBridge.setProtector(this)
            val geoPresent = VpnConfigBuilder.geoDatabasesPresent(this)
            var disableGeo = false

            fun startCore(): Boolean {
                // The core gets its own duplicate; if it never takes ownership, closing it is on us.
                val coreFd = try {
                    pfd.dup().detachFd()
                } catch (e: Exception) {
                    throw AttemptFailure(ConnectionError.TunnelError("could not duplicate the tunnel descriptor", e))
                }
                val yaml = VpnConfigBuilder.buildFromValidated(
                    validated, coreFd, p.settings, forceX25519Mlkem768 = p.forceX25519, geoDbPresent = geoPresent && !disableGeo,
                )
                val started = MihomoBridge.start(yaml, homeDir.absolutePath)
                if (!started) closeRawFd(coreFd)
                return started
            }

            var started = startCore()
            if (!started && looksLikeGeoError(MihomoBridge.lastError)) {
                // The bundled geo data was rejected and the whole config with it. Losing the
                // offline Iran-direct layer is better than not connecting at all.
                ConnLog.w(cid, Stage.CORE_STARTED, "core rejected geo data; retrying without GEOSITE/GEOIP rules")
                disableGeo = true
                started = startCore()
            }
            if (!started) {
                throw AttemptFailure(ConnectionError.CoreError(ErrorCode.CORE_START_FAILED, MihomoBridge.lastError.ifBlank { "core did not start" }))
            }
            ConnLog.i(cid, Stage.CORE_STARTED, "geoRules=${geoPresent && !disableGeo}")

            // ── VERIFY ───────────────────────────────────────────────────────
            val probe = ProxyProbe("127.0.0.1", MIXED_PORT)
            val isReality = validated.config.proxy.containsKey("reality-opts")
            var realityRetryDone = p.forceX25519 || !isReality
            val deadline = System.currentTimeMillis() + VERIFY_TIMEOUT_MS
            var lastFailure: String
            while (true) {
                yield()
                if (!MihomoBridge.isCoreAlive()) {
                    throw AttemptFailure(ConnectionError.CoreError(ErrorCode.CORE_CRASHED, "core stopped during startup"))
                }
                store.update(cid) { it.copy(stage = "verifying") }
                ConnLog.d(cid, Stage.CONNECTIVITY_CHECK, "probing through the tunnel")
                val r = withContext(Dispatchers.IO) { probe.probeOnce() }
                if (r is ProbeResult.Ok) {
                    ConnLog.i(cid, Stage.CONNECTIVITY_CHECK, "ok in ${r.latencyMs} ms")
                    break
                }
                lastFailure = (r as ProbeResult.Failed).reason
                ConnLog.w(cid, Stage.CONNECTIVITY_CHECK, "failed: $lastFailure")

                // REALITY is a strict match: whether support-x25519mlkem768 must be set depends on the
                // server, and the only way to find out is a failed handshake. Retry once with it flipped.
                if (!realityRetryDone && MihomoBridge.coreLog().contains("REALITY authentication failed")) {
                    realityRetryDone = true
                    p.forceX25519 = true
                    ConnLog.w(cid, Stage.CORE_STARTED, "REALITY handshake failed; restarting the core with support-x25519mlkem768")
                    stopCore() // closes the core's duplicate; our own descriptor is untouched, so the TUN stays
                    if (!startCore()) {
                        throw AttemptFailure(ConnectionError.CoreError(ErrorCode.CORE_START_FAILED, MihomoBridge.lastError.ifBlank { "core restart failed" }))
                    }
                    continue
                }
                if (System.currentTimeMillis() > deadline) {
                    throw AttemptFailure(ConnectionError.TimeoutError("no traffic passed through the server within ${VERIFY_TIMEOUT_MS / 1000}s: $lastFailure"))
                }
                delay(700)
            }

            val link = TunnelInspector.vpnLink(this, TUN_ADDRESS_V4)
            if (link != null) {
                ConnLog.i(cid, Stage.TUNNEL_READY, "interface=${link.interfaceName} addresses=${link.addresses} osValidated=${link.osValidated}")
            } else {
                ConnLog.w(cid, Stage.TUNNEL_READY, "the OS did not report the VPN link (not treated as a failure)")
            }

            // ── CONNECTED ────────────────────────────────────────────────────
            if (!store.transition(ConnectionState.CONNECTED, expectedConnectionId = cid, reconnectAttempt = 0)) return
            connected = true
            p.everConnected = true
            uploadBytes = 0L
            downloadBytes = 0L
            lastError = ""
            ConnLog.i(cid, Stage.CONNECTED, "server ${validated.config.server}:${validated.config.port}")
            updateNotification("Connected")
            checkAndWarnAboutSystemDoH()

            coroutineScope {
                val aux = launch(Dispatchers.IO) { collectTunnelInfo(cid, validated.config, link) }
                try {
                    monitor(serial, cid, probe)
                } finally {
                    aux.cancel()
                }
            }
        } catch (e: CancellationException) {
            throw e // a deliberate stop: the loop that cancelled us does the bookkeeping
        } catch (e: AttemptFailure) {
            report(serial, cid, e.error, connected)
        } catch (e: Exception) {
            report(serial, cid, ConnectionError.UnknownError("${e.javaClass.simpleName}: ${e.message}", e), connected)
        }
    }

    private fun report(serial: Int, cid: String, error: ConnectionError, wasConnected: Boolean) {
        commands.trySend(Command.AttemptFailed(serial, cid, error, wasConnected))
    }

    /** Watches a live connection until it fails (reported to the loop) or is cancelled. */
    private suspend fun monitor(serial: Int, cid: String, probe: ProxyProbe) {
        var lastCoreCheck = System.currentTimeMillis()
        var lastProbe = System.currentTimeMillis()
        var probeFailures = 0
        while (coroutineContext.isActive) {
            delay(1_000)
            uploadBytes = MihomoBridge.queryUpload()
            downloadBytes = MihomoBridge.queryDownload()
            val now = System.currentTimeMillis()

            if (now - lastCoreCheck >= CORE_CHECK_INTERVAL_MS) {
                lastCoreCheck = now
                if (!MihomoBridge.isCoreAlive()) {
                    report(serial, cid, ConnectionError.CoreError(ErrorCode.CORE_CRASHED, "core is no longer running"), true)
                    return
                }
            }

            // No physical network: nothing to probe, and a probe failure would only be the network's. Wait
            // for the network callback to say it is back.
            if (!TunnelInspector.hasUnderlyingNetwork(this)) continue

            val interval = if (probeFailures > 0) PROBE_INTERVAL_AFTER_FAILURE_MS else PROBE_INTERVAL_MS
            val nudged = probeNow.getAndSet(false)
            if (nudged || now - lastProbe >= interval) {
                lastProbe = now
                val r = withContext(Dispatchers.IO) { probe.probeOnce() }
                if (r is ProbeResult.Ok) {
                    if (probeFailures > 0) ConnLog.i(cid, Stage.CONNECTIVITY_CHECK, "recovered")
                    probeFailures = 0
                } else {
                    probeFailures++
                    ConnLog.w(cid, Stage.CONNECTIVITY_CHECK, "health probe failed ($probeFailures/$PROBE_FAILURES_BEFORE_RECONNECT): ${(r as ProbeResult.Failed).reason}")
                    if (probeFailures >= PROBE_FAILURES_BEFORE_RECONNECT) {
                        report(serial, cid, ConnectionError.TimeoutError("server stopped carrying traffic: ${r.reason}"), true)
                        return
                    }
                }
            }
        }
    }

    // ── preparation helpers ──────────────────────────────────────────────────

    private fun prepareConfig(p: Plan): ValidatedConfig {
        if (p.uri.isBlank()) {
            throw AttemptFailure(ConnectionError.ConfigError(ErrorCode.INVALID_CONFIG, "no config is stored for the selected server", "user_config"))
        }
        val parsed = ConfigUriParser.parse(p.uri, forceX25519Mlkem768 = p.forceX25519)
        val proxy = when (parsed) {
            is ConfigUriParser.UriParseResult.Success -> parsed.proxy
            is ConfigUriParser.UriParseResult.Failure -> throw AttemptFailure(parsed.error)
        }
        val internal = InternalConnectionConfig.fromProxyMap(proxy)
            ?: throw AttemptFailure(ConnectionError.ConfigError(ErrorCode.INVALID_CONFIG, "config has no usable protocol, server or port"))
        return when (val v = ConfigValidator.validate(internal)) {
            is ValidationResult.Valid -> v.config
            is ValidationResult.Invalid -> throw AttemptFailure(v.error)
        }
    }

    /** Copies the bundled geo databases out of the APK on first run and whenever the app was upgraded. */
    private fun prepareGeoFiles(): File {
        val home = File(filesDir, "mihomo").apply { mkdirs() }
        val marker = File(home, ".geo-version")
        val installed = try {
            val pi = packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode
            else @Suppress("DEPRECATION") pi.versionCode.toLong()
        } catch (_: Exception) { -1L }
        val cached = try { marker.readText().trim().toLongOrNull() } catch (_: Exception) { null }
        val stale = cached == null || cached != installed
        listOf("geoip.metadb", "geosite.dat").forEach { name ->
            val target = File(home, name)
            if (stale || !target.exists()) {
                try {
                    assets.open(name).use { inp -> target.outputStream().use { out -> inp.copyTo(out) } }
                } catch (_: Exception) {}
            }
        }
        try { marker.writeText(installed.toString()) } catch (_: Exception) {}
        return home
    }

    private fun looksLikeGeoError(err: String) =
        err.contains("geodata", true) || err.contains("geosite", true) || err.contains("geoip", true)

    private fun recentCoreOutput(): String = try { MihomoBridge.coreLog().takeLast(800) } catch (_: Exception) { "" }

    private fun closeRawFd(fd: Int) {
        try { ParcelFileDescriptor.adoptFd(fd).close() } catch (_: Exception) {}
    }

    private fun readSettings() = ConnectionSettings(
        mtu = AppSettings.mtu(this),
        ipv6 = AppSettings.ipv6Enabled(this),
        allowLan = AppSettings.allowLan(this),
        useDoh = AppSettings.useDoh(this),
        customDnsEnabled = AppSettings.customDnsEnabled(this),
        customDnsServers = AppSettings.customDnsServers(this),
        splitTunnelMode = AppSettings.splitTunnelMode(this),
        adBlocker = AppSettings.adBlockerEnabled(this),
        blockAds = AppSettings.blockAds(this),
        blockTrackers = AppSettings.blockTrackers(this),
        blockMalware = AppSettings.malwareBlockerEnabled(this),
        splitTunnelApps = AppSettings.splitTunnelApps(this),
        killSwitch = AppSettings.killSwitchEnabled(this),
        autoReconnect = AppSettings.autoReconnectEnabled(this),
        maxReconnectAttempts = AppSettings.maxRetryAttempts(this),
        language = AppSettings.language(this),
    )

    private fun policyFor(s: ConnectionSettings) = ReconnectPolicy(maxAttempts = s.maxReconnectAttempts.coerceIn(0, 10))

    private fun newConnectionId() = UUID.randomUUID().toString().take(8)

    private fun fail(cid: String?, error: ConnectionError) {
        lastError = error.technical
        ConnLog.error(cid, Stage.ERROR, error)
        val s = store.snapshot
        if (s.state == ConnectionState.IDLE || s.state == ConnectionState.DISCONNECTED || s.state == ConnectionState.ERROR) {
            if (store.transition(ConnectionState.PREPARING, connectionId = newConnectionId(), configId = s.configId)) {
                store.transition(ConnectionState.ERROR, error = error)
            }
        }
        finishService()
    }

    // ── tunnel interface ─────────────────────────────────────────────────────

    /** Returns the service's TUN handle: the held one when [Plan.reuseTun], otherwise a fresh interface. */
    private fun obtainTun(p: Plan): ParcelFileDescriptor {
        synchronized(resLock) {
            val held = tunPfd
            if (p.reuseTun && held != null) return held
            try { held?.close() } catch (_: Exception) {}
            tunPfd = null
        }
        val pfd = establishTun(p.settings)
            ?: throw AttemptFailure(ConnectionError.TunnelError("the system refused to create the VPN interface"))
        synchronized(resLock) { tunPfd = pfd }
        return pfd
    }

    private fun establishTun(s: ConnectionSettings): ParcelFileDescriptor? {
        return try {
            // These must be the SAME servers the core is configured to use (see VpnConfigBuilder):
            // Android's Private DNS can opportunistically upgrade to DoT straight against whatever IPs
            // are declared here. addDnsServer() only takes a literal IP, so a hostname-only DoH URL has
            // nothing to extract and the Google default applies.
            val dnsServers = if (s.customDnsEnabled) {
                s.customDnsServers.mapNotNull { extractDnsIp(it) }.take(2).ifEmpty { listOf("8.8.8.8", "8.8.4.4") }
            } else listOf("8.8.8.8", "8.8.4.4")

            val builder = Builder()
                .setSession("CDN Hunter VPN")
                .addAddress(TUN_ADDRESS_V4, 32)
            dnsServers.forEach { builder.addDnsServer(it) }
            // IPv6 is always claimed, whatever the user's IPv6 setting: otherwise on a network with real
            // IPv6, IPv6 traffic (and DNS over it) would bypass the VPN and leak. With IPv6 off the core
            // drops those packets inside the tunnel — fail-closed rather than leaking.
            builder.addAddress("fd00:1:1:1::1", 128)
            builder
                .setMtu(s.mtu)
                .setBlocking(false)
                .addRoute("0.0.0.0", 1)
                .addRoute("128.0.0.0", 1)
                .addRoute("::", 0)

            // Android accepts EITHER allowed or disallowed applications on one Builder, never both.
            if (s.splitTunnelMode == "include" && s.splitTunnelApps.isNotEmpty()) {
                for (pkg in s.splitTunnelApps) {
                    try { builder.addAllowedApplication(pkg) } catch (_: Exception) { /* uninstalled since being listed */ }
                }
            } else {
                // This app itself must be excluded, or its own traffic to the proxy server would loop into its tunnel.
                builder.addDisallowedApplication(packageName)
                for (pkg in s.splitTunnelApps) {
                    try { builder.addDisallowedApplication(pkg) } catch (_: Exception) { /* uninstalled since being listed */ }
                }
            }
            builder.establish()
        } catch (e: Exception) {
            ConnLog.e(store.snapshot.connectionId, Stage.TUNNEL_STARTING, "establish failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    // ── kill switch ──────────────────────────────────────────────────────────

    /**
     * Holds the interface open and discards everything arriving on it, so traffic is blocked
     * explicitly and immediately instead of by a buffer eventually filling.
     *
     * The descriptor is non-blocking, so a plain read() returns EAGAIN the moment nothing is
     * queued — which ended the old loop at once and, by closing the stream, tore the
     * interface down: the kill switch failed open. This waits in poll() (woken at least every
     * 500 ms to notice a stop request) and only reads when there is something to read.
     */
    private fun startDrain() {
        val pfd = synchronized(resLock) { tunPfd } ?: return
        stopDrainBlocking()
        val t = Thread({
            val fd = pfd.fileDescriptor
            val buf = ByteArray(32 * 1024)
            val pollFd = StructPollfd().apply { this.fd = fd; events = OsConstants.POLLIN.toShort() }
            while (killSwitchBlocking.get()) {
                try {
                    pollFd.revents = 0
                    val ready = Os.poll(arrayOf(pollFd), 500)
                    if (ready > 0) {
                        val bad = OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL
                        if ((pollFd.revents.toInt() and bad) != 0) break
                        try {
                            Os.read(fd, buf, 0, buf.size)
                        } catch (e: ErrnoException) {
                            if (e.errno != OsConstants.EAGAIN && e.errno != OsConstants.EINTR) break
                        }
                    }
                } catch (e: ErrnoException) {
                    if (e.errno == OsConstants.EINTR) continue
                    break
                } catch (_: Exception) {
                    break
                }
            }
        }, "killswitch-drain")
        t.isDaemon = true
        drainThread = t
        t.start()
    }

    /** Signals the drain thread to stop and waits for it briefly. The thread never closes the descriptor; the service does. */
    private fun stopDrainBlocking() {
        killSwitchBlocking.set(false)
        val t = drainThread ?: return
        drainThread = null
        try { t.join(800) } catch (_: InterruptedException) {}
    }

    // ── post-connect information ─────────────────────────────────────────────

    /**
     * Resolves the dialled server's address and publishes it as `tunnel.serverIp`. An IP literal
     * needs no lookup; a hostname is resolved once (bounded) — the same lookup [collectTunnelInfo]
     * would make after connecting, made early and only once. Resolving BEFORE the tunnel exists
     * also means the answer is the server's real address, not one the tunnel's own DNS handed back.
     */
    private fun publishServerIp(cid: String, host: String) {
        val ip = TunnelInspector.resolveServerIp(host) ?: return
        store.update(cid) { snap ->
            val t = snap.tunnel ?: TunnelInfo(serverAddress = host)
            if (t.serverIp == ip) snap else snap.copy(tunnel = t.copy(serverIp = ip))
        }
    }

    /** Gathers what is known about the live tunnel and publishes it. Every field is optional. */
    private fun collectTunnelInfo(cid: String, config: InternalConnectionConfig, link: VpnLinkInfo?) {
        val base = TunnelInfo(
            serverAddress = config.server,
            localIp = TunnelInspector.localAddress(this),
            tunnelInterface = link?.interfaceName,
            tunnelAddress = link?.addresses?.firstOrNull { it == TUN_ADDRESS_V4 } ?: link?.addresses?.firstOrNull(),
            // Point-to-point TUN with on-link routes: there is no gateway address to report.
            vpnGateway = null,
            osValidated = link?.osValidated,
        )
        // Keep the address published while connecting rather than dropping it and looking it up again.
        store.update(cid) { it.copy(tunnel = base.copy(serverIp = it.tunnel?.serverIp)) }
        val serverIp = store.snapshot.tunnel?.serverIp ?: TunnelInspector.resolveServerIp(config.server)
        if (serverIp != null) store.update(cid) { it.copy(tunnel = (it.tunnel ?: base).copy(serverIp = serverIp)) }

        val geo = com.cdnhunter.app.engine.GeoService()
        val publicIp = try { geo.lookupCurrentIp(proxied = true) } catch (_: Exception) { "" }
        // Exactly one of the two lands: the exit address, or the fact that the lookup came back
        // empty. Both are fenced to this connection id, so neither can leak into the next attempt.
        store.update(cid) {
            val t = it.tunnel ?: base
            it.copy(tunnel = if (publicIp.isNotBlank()) t.copy(publicIp = publicIp) else t.copy(publicIpFailed = true))
        }

        try {
            val info = geo.lookupCurrentExitGeoInfo()
            if (info.cc.isNotBlank() && store.snapshot.connectionId == cid) {
                val configId = SecurePrefs.vpn(this).getString("active_config_id", "") ?: ""
                exitCountryCode = info.cc
                exitCity = info.city
                exitGeoConfigId = configId
                persistAccurateGeo(configId, info.cc, info.city)
            }
        } catch (_: Exception) {
            // Leave the exit location blank — the UI falls back to the pre-connect estimate.
        }
    }

    // Writes the tunnel-verified country/city for one saved config into the same record the
    // list reads and writes (key "saved_configs", one line per config:
    // "uri\u0001countryCode\u0001city\u0001pingMs\u0001geoResolved\u0001accurateGeoResolved
    //  \u0001isImported\u0001subscriptionId\u0001subscriptionName"). Without this the accurate
    // result lived only in memory and the next app start went back to the pre-connect estimate.
    private fun persistAccurateGeo(configId: String, cc: String, city: String) {
        try {
            val prefs = SecurePrefs.vpn(this)
            val sep = "\u0001"
            val raw = prefs.getString("saved_configs", "") ?: return
            if (raw.isBlank()) return
            var changed = false
            val updated = raw.split("\n").map { line ->
                val parts = line.split(sep)
                val uri = parts.getOrNull(0)?.trim().orEmpty()
                if (uri.isBlank() || uri.hashCode().toString() != configId) return@map line
                changed = true
                // Only geo is known here; every other field must come from the existing line.
                val originalPingMs = parts.getOrNull(3) ?: "-1"
                val isImported = parts.getOrNull(6) ?: "0"
                val subscriptionId = parts.getOrNull(7) ?: ""
                val subscriptionName = parts.getOrNull(8) ?: ""
                listOf(uri, cc, city, originalPingMs, "1", "1", isImported, subscriptionId, subscriptionName).joinToString(sep)
            }
            if (changed) prefs.edit().putString("saved_configs", updated.joinToString("\n")).apply()
        } catch (e: Exception) {
            ConnLog.w(null, Stage.CONNECTED, "persistAccurateGeo failed: ${e.javaClass.simpleName}")
        }
    }

    // ── network callback ─────────────────────────────────────────────────────

    private fun registerNetworkCallback() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        connectivityManager = cm
        // The default builder excludes VPN networks, so this is about the physical network only.
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Only the transition from "no usable network at all" to "a network" is an event; moving
                // between Wi-Fi access points fires this too and the core usually rides those out.
                if (!hadNetwork) {
                    hadNetwork = true
                    commands.trySend(Command.NetworkRestored)
                } else if (store.snapshot.isConnected) {
                    // A different network took over while connected: re-check the path soon.
                    probeNow.set(true)
                }
            }

            override fun onLost(network: Network) {
                if (cm.activeNetwork == null) hadNetwork = false
            }
        }
        networkCallback = callback
        try {
            cm.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            // Some OEM builds restrict this for background services; the health monitor still
            // detects a dead path, just without this extra trigger.
        }
    }

    // ── system DNS ───────────────────────────────────────────────────────────

    /** Returns the Private DNS hostname if Android's system-wide Private DNS is in strict (hostname) mode. */
    private fun checkPrivateDnsStrictMode(): String? = try {
        val mode = android.provider.Settings.Global.getString(contentResolver, "private_dns_mode")
        if (mode == "hostname") android.provider.Settings.Global.getString(contentResolver, "private_dns_specifier") else null
    } catch (_: Exception) { null }

    /**
     * System DoH (Private DNS) queries bypass the tunnel because they use HTTPS on port 443, which
     * cannot be intercepted at the TUN level — warn the user.
     */
    private fun checkAndWarnAboutSystemDoH() {
        try {
            val mode = android.provider.Settings.Global.getString(contentResolver, "private_dns_mode") ?: "off"
            if (mode != "off") {
                updateNotification("⚠️ Disable Private DNS in Settings to prevent DNS leaks")
                ConnLog.w(store.snapshot.connectionId, Stage.CONNECTED, "system Private DNS is on; its queries bypass the tunnel")
            }
        } catch (_: Exception) {}
    }

    // Pulls a literal IPv4/IPv6 address out of a custom DNS entry for VpnService.Builder.addDnsServer(),
    // which only accepts a literal IP: "https://1.1.1.1/dns-query" -> "1.1.1.1", "9.9.9.9:53" -> "9.9.9.9",
    // "[2606:4700:4700::1111]:53" -> "2606:4700:4700::1111". A hostname-only entry has nothing to extract
    // and yields null so the caller's fallback applies.
    private fun extractDnsIp(entry: String): String? {
        var s = entry.trim().removePrefix("https://").removePrefix("quic://").removePrefix("tls://").substringBefore("/")
        val ipv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
        if (s.startsWith("[")) return s.substringAfter("[").substringBefore("]").takeIf { it.contains(":") }
        if (s.count { it == ':' } > 1) return s
        s = s.substringBefore(":")
        return s.takeIf { ipv4.matches(it) }
    }

    // ── notification ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "VPN", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        // A Disconnect action in every state. It is the way out when the kill switch is blocking
        // traffic: the in-app button reconnects, and without this the only way to release the block
        // would be the system VPN settings.
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, CdnVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0),
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CDN Hunter VPN")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .addAction(0, "Disconnect", stop)
            .build()
    }

    private fun updateNotification(status: String) {
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(status))
        } catch (_: Exception) {}
    }
}
