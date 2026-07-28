package com.cdnhunter.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class CdnVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.cdnhunter.app.START_VPN"
        const val ACTION_STOP = "com.cdnhunter.app.STOP_VPN"
        const val CHANNEL_ID = "cdnhunter_vpn"
        const val NOTIFICATION_ID = 1

        var isRunning = AtomicBoolean(false)
        var uploadBytes = 0L
        var downloadBytes = 0L
        var lastError = ""
        var debugLog = ""

        // The server's REAL location, resolved by asking a geo-IP service
        // "what IP am I connecting from" THROUGH the active tunnel (see
        // GeoService.lookupCurrentExitGeoInfo), not by resolving the config's
        // hostname directly. Populated once per successful connection by
        // startVpn(); empty/blank until that lookup completes. The UI should
        // prefer this over any pre-connect estimate once it's non-blank and
        // exitGeoConfigId matches the currently active config, since domains
        // behind a CDN report the CDN edge's location from a direct DNS
        // lookup, not the real backend server's.
        var exitCountryCode = ""
        var exitCity = ""
        var exitGeoConfigId = ""

        var killSwitchBlocking = AtomicBoolean(false)
        // The currently-running kill-switch drain coroutine, if any -- see
        // stopVpnInternal(keepTunAlive = true) and startVpn(). Only ever one
        // at a time; startVpn() joins this (with a timeout) before touching
        // tunRawFd, so the drain loop is always the sole owner that closes
        // its own fd and there's no window for both sides to close the same
        // descriptor.
        var killSwitchDrainJob: Job? = null

        // Auto-reconnect: on an unexpected drop, retry this many times with
        // exponential backoff (1s, 2s, 4s, capped at 15s) before giving up
        // and falling back to the kill switch (if enabled) or a full
        // disconnect (if not). Kept small and bounded rather than infinite —
        // if the server/network is genuinely down, retrying forever just
        // drains battery and delays the kill switch actually protecting the
        // user, which is the more important guarantee once retries have
        // clearly stopped helping.
        const val MAX_RECONNECT_ATTEMPTS = 3

        fun start(context: Context) {
            val intent = Intent(context, CdnVpnService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        var instance: CdnVpnService? = null

        fun stop(context: Context) {
            val intent = Intent(context, CdnVpnService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }

    private var tunFd: ParcelFileDescriptor? = null
    // The raw fd number after tunFd.detachFd() — this is what actually owns
    // the descriptor now and must be closed directly (see stopVpnInternal()),
    // since tunFd itself no longer holds anything to close once detached.
    private var tunRawFd: Int? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // How many consecutive auto-reconnect attempts have happened for the
    // current connection lifecycle. Reset to 0 on any deliberate stopVpn()
    // (user-initiated disconnect) or once a connection actually succeeds
    // (isRunning.set(true) in startVpn()) -- so a later, unrelated drop
    // always gets its own fresh MAX_RECONNECT_ATTEMPTS budget rather than
    // inheriting an exhausted count from a previous, already-recovered
    // outage.
    private var reconnectAttempt = 0

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        registerNetworkCallback()
    }

    // Tracks whether the underlying network (not the VPN interface itself)
    // was available the last time we checked -- used to detect "network came
    // back after being fully down" specifically, as opposed to every minor
    // network change (switching Wi-Fi access points, etc.), which mihomo/the
    // OS usually ride out on their own without our help.
    private var hadNetwork = true
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private fun registerNetworkCallback() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        connectivityManager = cm
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Only reconnect on the transition from "had no usable
                // network at all" to "network is back" -- everyday changes
                // (moving between Wi-Fi networks, a second network appearing
                // alongside an existing one) fire onAvailable too, but
                // mihomo's existing connection usually survives those fine
                // on its own; forcing a reconnect on every single one would
                // be disruptive for no benefit.
                if (!hadNetwork) {
                    hadNetwork = true
                    if (isAutoReconnectEnabled() && !isRunning.get() && !killSwitchBlocking.get()
                        && getSharedPreferences("cdnhunter_vpn", MODE_PRIVATE).getString("active_config_id", "").isNullOrBlank().not()
                    ) {
                        debugLog += "\nNetwork restored after being fully down — auto-reconnecting."
                        reconnectAttempt = 0
                        startVpn()
                    }
                }
            }
            override fun onLost(network: Network) {
                // Only mark "no network" once NO network with internet
                // capability remains at all (ConnectivityManager keeps
                // calling this per-network; onAvailable above is what
                // actually confirms one exists again).
                if (cm.activeNetwork == null) hadNetwork = false
            }
        }
        networkCallback = callback
        try {
            cm.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            // Some OEM/Android versions restrict this for background
            // services -- auto-reconnect still works via the normal
            // mihomo-error retry path in startVpn(), just without this
            // additional "network came back" trigger.
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            // stopVpn() makes a blocking JNI call into mihomo (MihomoBridge.stop()
            // -> executor.Shutdown()) to tear down listeners/goroutines. Running
            // that synchronously here on the main thread (onStartCommand always
            // runs on main) could block the UI for as long as that call takes,
            // which looked like "disconnect does nothing" rather than an actual
            // failure — it just hadn't finished yet. Run it on the same IO scope
            // startVpn() uses instead.
            ACTION_STOP -> scope.launch { killSwitchBlocking.set(false); stopVpn() }
        }
        return START_STICKY
    }

    private fun isKillSwitchEnabled(): Boolean =
        AppSettings.killSwitchEnabled(this)

    private fun isAutoReconnectEnabled(): Boolean =
        AppSettings.autoReconnectEnabled(this)

    private fun startVpn() {
        if (isRunning.get()) return
        killSwitchBlocking.set(false)
        startForeground(NOTIFICATION_ID, buildNotification("Connecting..."))
        lastError = ""
        // Reset per-attempt, not appended forever — otherwise repeated connect/
        // disconnect cycles grow this string without bound for the life of the process.
        debugLog = ""

        job = scope.launch {
            try {
                // If the kill switch was holding a previous tun fd open, it's
                // being drained by killSwitchDrainLoop on a coroutine tracked
                // by killSwitchDrainJob, which is the SOLE owner responsible
                // for closing that fd once it exits (see killSwitchDrainLoop).
                // killSwitchBlocking.set(false) above wakes it on its next
                // while-check, but that's not instant -- it may be blocked
                // inside stream.read() at this exact moment. Actually join()
                // it here (suspending, not busy-waiting) so we're guaranteed
                // it has fully exited and closed its own fd before we touch
                // tunRawFd/tunFd at all. Without this wait, closing the same
                // fd from both this coroutine and the drain loop around the
                // same moment would risk the exact double-close bug that
                // previously crashed the app on disconnect. Bounded by a
                // timeout as a safety net in case that coroutine is ever
                // stuck for some unrelated reason -- we still proceed after
                // it (a stale fd left open a bit longer is far less bad than
                // startVpn() hanging forever).
                killSwitchDrainJob?.let { withTimeoutOrNull(2000) { it.join() } }
                tunRawFd = null
                tunFd = null

                val mihomoHomeDir = File(filesDir, "mihomo").apply { mkdirs() }

                listOf("geoip.metadb", "geosite.dat").forEach { name ->
                    val target = File(mihomoHomeDir, name)
                    if (!target.exists()) {
                        try {
                            assets.open(name).use { inp -> target.outputStream().use { out -> inp.copyTo(out) } }
                        } catch (_: Exception) {}
                    }
                }

                // TUN must be established BEFORE building the config: mihomo needs
                // the live file descriptor embedded directly in its YAML (tun.file-
                // descriptor) to read/write packets. Building config first (the old
                // order) meant there was no fd to give it, so mihomo only opened a
                // local proxy port with nothing ever feeding it TUN traffic.
                val tun = establishTun()
                if (tun == null) {
                    lastError = "Failed to create VPN tunnel"
                    debugLog += "\nFAILED: could not establish TUN interface (lastError set above)"
                    withContext(Dispatchers.Main) { stopSelf() }
                    return@launch
                }
                tunFd = tun
                // detachFd() transfers ownership of the underlying descriptor to us
                // as a plain int — NOT tun.fd, which leaves the ParcelFileDescriptor
                // object owning it. With .fd, Android's GC can finalize/close the
                // ParcelFileDescriptor at any point while mihomo is still reading/
                // writing it from native code — sometimes immediately, sometimes
                // after a GC pause — since nothing forces the object to stay alive
                // just because Go holds the raw number. That produced exactly this
                // symptom: TUN "establishes", mihomo reports healthy and even logs
                // proxied connections for traffic that happens to loop through
                // userspace sockets, but the actual OS-level tun device never
                // reliably passes packets, taking the whole device's connectivity
                // down with it once Android sees a VPN is "active" but nothing
                // flows through it. After detachFd() we own the raw fd directly and
                // are responsible for closing it ourselves (see stopVpnInternal()).
                val rawFd = tun.detachFd()
                tunRawFd = rawFd
                protect(rawFd)

                // Register the socket protector BEFORE mihomo starts dialing
                // anything: it exempts mihomo's own outbound connection to the
                // real proxy server from being captured by the TUN mihomo is
                // about to feed. Without this, only local (non-TUN) traffic —
                // e.g. an app pointed directly at 127.0.0.1:10808 — ever
                // reaches the internet; everything routed through the TUN
                // loops back into mihomo and goes nowhere.
                MihomoBridge.setProtector(this@CdnVpnService)

                // Android's system-wide "Private DNS" (Settings > Network > Private
                // DNS), when set to a specific hostname (strict mode), bypasses the
                // VPN's captured port 53 entirely — apps' DNS queries go straight out
                // over DoT to that hostname, never touching mihomo's dns-hijack. This
                // produces exactly the "connects, no error, no traffic" symptom: the
                // tun comes up and mihomo reports healthy, but nothing ever gets a
                // domain to route because DNS never passed through it. Surface this
                // in the debug log since there's no way to force it off from here.
                checkPrivateDnsStrictMode()?.let { hostname ->
                    debugLog += "\nWARNING: Android Private DNS is set to strict mode ($hostname). " +
                        "This bypasses the VPN's DNS hijacking — traffic may not route correctly. " +
                        "Disable it or set it to \"Automatic\" in Settings > Network > Private DNS."
                }

                var forceX25519 = false
                val config = VpnConfigBuilder.buildConfig(this@CdnVpnService, rawFd, forceX25519)

                debugLog = "── connect attempt @ ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())} ──\n" +
                    "config length: ${config.length} chars\n" +
                    "tun fd: $rawFd\n" +
                    // 600 was too short — it happened to cut off exactly after
                    // "uuid: ", making the proxy's UUID look empty in the debug
                    // log when it wasn't (the real config always had it; only the
                    // truncated debug view didn't show it). 2000 comfortably
                    // covers the whole proxies: entry for any of our supported
                    // proxy types.
                    "config head:\n${config.take(2000)}\n"

                android.util.Log.i("CdnVpn", "Config length: ${config.length}")
                android.util.Log.i("CdnVpn", "Config first 200: ${config.take(200)}")
                android.util.Log.d("CdnVpn", "Full mihomo config: $config")

                val started = MihomoBridge.start(config, mihomoHomeDir.absolutePath)
                if (!started) {
                    lastError = "mihomo failed to start: ${MihomoBridge.lastError}"
                    debugLog += "\nFAILED: mihomo rejected the config.\nmihomo error:\n${MihomoBridge.lastError}"
                    stopVpnInternal(keepTunAlive = false)
                    return@launch
                }

                debugLog += "\nmihomo started OK"

                // REALITY's handshake is a strict binary match, not a soft TLS
                // negotiation — support-x25519mlkem768 must exactly match what the
                // server expects (some Xray-core versions require it, others break
                // if it's forced on) and there's no way to know which ahead of time
                // from the share link alone. mihomo only discovers this by actually
                // attempting a handshake, which happens asynchronously after
                // start() already returned success — so check coreLog a moment
                // later and, if it shows the specific auth failure, restart once
                // with the flag flipped. This only ever fires for reality configs
                // (that's the only error text this check matches), so it's a
                // no-op for every other proxy type.
                if (!forceX25519 && config.contains("reality-opts")) {
                    delay(2500)
                    if (MihomoBridge.coreLog().contains("REALITY authentication failed")) {
                        debugLog += "\nREALITY handshake failed without support-x25519mlkem768 — retrying with it enabled."
                        forceX25519 = true
                        MihomoBridge.stop()
                        val retryConfig = VpnConfigBuilder.buildConfig(this@CdnVpnService, rawFd, forceX25519)
                        val retryStarted = MihomoBridge.start(retryConfig, mihomoHomeDir.absolutePath)
                        if (!retryStarted) {
                            lastError = "mihomo failed to start (retry): ${MihomoBridge.lastError}"
                            debugLog += "\nFAILED on retry: ${MihomoBridge.lastError}"
                            stopVpnInternal(keepTunAlive = false)
                            return@launch
                        }
                        debugLog += "\nmihomo restarted OK with support-x25519mlkem768"
                    }
                }

                isRunning.set(true)
                reconnectAttempt = 0
                uploadBytes = 0L
                downloadBytes = 0L
                updateNotification("Connected")

                // Resolve the server's REAL location by asking a geo-IP service
                // through the tunnel itself, not by resolving the config's
                // hostname directly (which for CDN-fronted domains reports the
                // CDN edge's location, not the actual backend server's — see
                // GeoService.lookupCurrentExitGeoInfo). Runs on its own coroutine,
                // separate from the traffic-polling loop below, so a slow/failed
                // geo-IP provider can never delay traffic stats or the connection
                // itself.
                scope.launch(Dispatchers.IO) {
                    try {
                        val configId = getSharedPreferences("cdnhunter_vpn", MODE_PRIVATE)
                            .getString("active_config_id", "") ?: ""
                        val info = com.cdnhunter.app.engine.GeoService().lookupCurrentExitGeoInfo()
                        if (info.cc.isNotBlank()) {
                            exitCountryCode = info.cc
                            exitCity = info.city
                            exitGeoConfigId = configId
                            persistAccurateGeo(configId, info.cc, info.city)
                        }
                    } catch (_: Exception) {
                        // Leave exitCountryCode blank — UI falls back to the
                        // pre-connect estimate if this never resolves.
                    }
                }

                while (isActive && isRunning.get()) {
                    uploadBytes = MihomoBridge.queryUpload()
                    downloadBytes = MihomoBridge.queryDownload()
                    delay(1000)
                }
            } catch (e: CancellationException) {
                // Normal path when the user hits disconnect: stopVpn() calls
                // job?.cancel(), which throws this inside the coroutine. It's
                // not a failure — don't set lastError/debugLog as if it were,
                // that only makes real errors harder to spot in the log.
                throw e
            } catch (e: Exception) {
                val wasRunning = isRunning.get()
                lastError = e.message ?: "Unknown error"
                debugLog += "\nEXCEPTION: ${e.message}\n${android.util.Log.getStackTraceString(e)}"
                debugLog = debugLog.takeLast(8000)
                isRunning.set(false)

                // Auto-reconnect: try a bounded number of times with backoff
                // before giving up. Kill switch is the backstop AFTER these
                // retries are exhausted, not competing with them — if both
                // are on, we retry first and only fall back to holding the
                // kill switch once every retry attempt has failed. A retry
                // "succeeding" here just means startVpn() ran again without
                // throwing before the retry budget ran out; if it also fails
                // it re-enters this same catch block recursively, so the
                // retry count must be tracked outside this single catch
                // invocation (see reconnectAttempt below).
                if (isAutoReconnectEnabled() && reconnectAttempt < MAX_RECONNECT_ATTEMPTS) {
                    reconnectAttempt++
                    val backoffMs = (1000L * (1 shl (reconnectAttempt - 1))).coerceAtMost(15000L)
                    debugLog += "\nAuto-reconnect: attempt $reconnectAttempt/$MAX_RECONNECT_ATTEMPTS in ${backoffMs}ms"
                    updateNotification("Reconnecting… ($reconnectAttempt/$MAX_RECONNECT_ATTEMPTS)")
                    // Must fully close the fd/mihomo before retrying — startVpn()
                    // establishes a brand new tun, and the old one has to be gone
                    // first or we'd leak it (same double-close hazard the kill
                    // switch join logic exists to avoid). stopService = false:
                    // this is a retry, not a real disconnect -- calling
                    // stopSelf() here would race the startVpn() call right
                    // below, potentially tearing down this service instance
                    // mid-reconnect.
                    stopVpnInternal(keepTunAlive = false, stopService = false)
                    delay(backoffMs)
                    if (isRunning.get()) return@launch // a newer connect attempt already took over
                    startVpn()
                    return@launch
                }

                val holdKillSwitch = wasRunning && isKillSwitchEnabled()
                reconnectAttempt = 0
                if (holdKillSwitch) {
                    killSwitchBlocking.set(true)
                    updateNotification("Blocked - connection lost (Kill Switch on)")
                    debugLog += "\nAuto-reconnect gave up after $MAX_RECONNECT_ATTEMPTS attempts — kill switch holding TUN up with traffic blocked."
                    stopVpnInternal(keepTunAlive = true)
                    return@launch
                }
                updateNotification("Error: ${lastError.take(30)}")
                delay(2000)
                // Already running on the IO scope's job — no need to hop to Main
                // (that would re-block the UI thread on MihomoBridge.stop()'s JNI
                // call) or to cancel `job`, since this coroutine IS `job` and is
                // already on its way out via this catch block.
                stopVpnInternal(keepTunAlive = false)
            }
        }
    }

    private suspend fun stopVpn() {
        job?.cancel()
        reconnectAttempt = 0
        stopVpnInternal(keepTunAlive = false)
    }

    // Writes the tunnel-verified (accurate) country/city for one saved config
    // straight into the same SharedPreferences record AppScreen's
    // loadConfigs/saveConfigs read and write (key "saved_configs", one line
    // per config:
    // "uri\u0001countryCode\u0001city\u0001pingMs\u0001geoResolved\u0001accurateGeoResolved
    //  \u0001isImported\u0001subscriptionId\u0001subscriptionName").
    // Without this, the accurate result only ever lived in the in-memory
    // exitCountryCode/exitCity vars above — gone the moment the app restarts,
    // so the next app-open flag went right back to the pre-connect (on-device,
    // sometimes CDN-edge-instead-of-real-server) estimate. This makes the
    // accurate one stick, so future app opens show it immediately without
    // waiting to reconnect.
    private fun persistAccurateGeo(configId: String, cc: String, city: String) {
        try {
            val prefs = getSharedPreferences("cdnhunter_vpn", MODE_PRIVATE)
            val sep = "\u0001"
            val raw = prefs.getString("saved_configs", "") ?: return
            if (raw.isBlank()) return
            var changed = false
            val updated = raw.split("\n").map { line ->
                val parts = line.split(sep)
                val uri = parts.getOrNull(0)?.trim().orEmpty()
                if (uri.isBlank() || uri.hashCode().toString() != configId) return@map line
                changed = true

                // Preserve original ping and the isImported/subscription fields —
                // this function only ever knows about geo, so any field beyond that
                // must come straight from the existing line, not get silently
                // dropped (which used to un-mark subscription-imported configs).
                val originalPingMs = parts.getOrNull(3) ?: "-1"
                val isImported = parts.getOrNull(6) ?: "0"
                val subscriptionId = parts.getOrNull(7) ?: ""
                val subscriptionName = parts.getOrNull(8) ?: ""
                listOf(uri, cc, city, originalPingMs, "1", "1", isImported, subscriptionId, subscriptionName)
                    .joinToString(sep)
            }
            if (changed) {
                prefs.edit().putString("saved_configs", updated.joinToString("\n")).apply()
            }
        } catch (e: Exception) {
            android.util.Log.e("CdnVpnService", "persistAccurateGeo failed: ${e.message}")
        }
    }

    // Actual teardown, shared by the external-stop path (stopVpn), the
    // internal error-recovery path in startVpn's catch block (which must not
    // cancel `job` since it IS the job currently running this code), and the
    // auto-reconnect retry path (which needs mihomo/fd torn down but the
    // Android service itself kept alive for the immediately-following
    // startVpn() call -- see stopService below).
    private suspend fun stopVpnInternal(keepTunAlive: Boolean, stopService: Boolean = true) {
        isRunning.set(false)
        MihomoBridge.stop()
        exitCountryCode = ""
        exitCity = ""
        exitGeoConfigId = ""

        if (keepTunAlive) {
            // A real kill switch, matching how Mullvad/NordVPN/etc. implement
            // it: Android's routes are still committed to this VPN interface
            // (addRoute("0.0.0.0", 1) + addRoute("128.0.0.0", 1) in
            // establishTun() are untouched — Android won't fall back to
            // direct routing just because mihomo stopped reading), so all
            // traffic is still forced through the tun fd. mihomo itself is
            // now stopped and isn't reading it anymore, so simply leaving the
            // fd open and doing nothing would only block traffic informally,
            // by letting the kernel-side tun buffer fill up and start
            // dropping packets on its own — not immediate, not guaranteed,
            // and not how a real kill switch works. Instead, actively read
            // and discard every packet ourselves on its own coroutine, so
            // blocking is explicit and instant regardless of buffer
            // behavior. Runs until reconnect (see startVpn(), which joins
            // killSwitchDrainJob) or the user hits disconnect (below, in the
            // !keepTunAlive branch, which also joins it).
            val fd = tunRawFd
            if (fd != null) {
                killSwitchDrainJob = scope.launch(Dispatchers.IO) { killSwitchDrainLoop(fd) }
            }
            return
        }

        // If the kill switch was active, mihomo was already stopped earlier
        // (when the kill switch first triggered) and does NOT own tunRawFd
        // anymore -- killSwitchDrainLoop does, exclusively. Join it (it
        // notices killSwitchBlocking=false, set below, on its next
        // while-check) so it finishes and closes its own fd, rather than
        // this coroutine racing to close the same fd independently -- that
        // double-close was the actual original cause of the app being killed
        // right after pressing disconnect. Bounded by a timeout as a safety
        // net in case it's ever stuck.
        val hadKillSwitchJob = killSwitchDrainJob != null
        killSwitchBlocking.set(false)
        killSwitchDrainJob?.let { withTimeoutOrNull(2000) { it.join() } }
        killSwitchDrainJob = null

        if (!hadKillSwitchJob) {
            // Normal path: MihomoBridge.stop() above already closed the tun
            // fd internally (via executor.Shutdown -> listener.Cleanup). We
            // must NOT also close it here on the Kotlin side -- that would
            // double-close the same fd number, which on Linux/Android can
            // immediately reassign that same integer to a totally unrelated
            // file/socket and corrupt native state. This was the actual
            // cause of the app being killed right after pressing disconnect.
        }
        // Either way (kill switch was active, or normal disconnect), the fd
        // has now been closed by whichever side actually owned it -- safe to
        // drop our references.
        tunRawFd = null
        tunFd = null
        if (stopService) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // Reads and discards packets from the tun fd for as long as the kill
    // switch is holding it open. Exits cleanly (no crash, no leak) once the
    // fd is closed out from under it -- either by a real reconnect
    // (startVpn() closes any stale kill-switch fd before establishing a new
    // one) or the user disconnecting (stopVpnInternal(keepTunAlive = false)
    // closes it).
    private fun killSwitchDrainLoop(fd: Int) {
        // ParcelFileDescriptor.adoptFd() + AutoCloseInputStream are both
        // fully public, documented APIs -- deliberately avoiding any
        // reflection into FileDescriptor's internal int constructor (a
        // non-SDK interface Android's hidden-API restrictions can silently
        // break depending on target SDK/OS version), since this path is
        // security-critical and must not be allowed to quietly stop working.
        val pfd = try { ParcelFileDescriptor.adoptFd(fd) } catch (_: Exception) { return }
        val stream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
        val buf = ByteArray(32767)
        try {
            while (killSwitchBlocking.get()) {
                val n = stream.read(buf)
                if (n < 0) break // fd closed elsewhere (reconnect or real disconnect) -- exit quietly
                // Read and discard -- do not forward, do not respond. This is
                // the actual block: nothing this app does with these bytes
                // ever reaches a real network socket.
            }
        } catch (_: Exception) {
            // fd closed/invalidated elsewhere -- exit quietly, this is expected
        } finally {
            // AutoCloseInputStream closes pfd (and therefore fd) when the
            // stream itself is closed -- do that here so the fd doesn't stay
            // open forever if the loop exits because killSwitchBlocking went
            // false (a real reconnect) rather than because the fd was
            // already closed by someone else.
            try { stream.close() } catch (_: Exception) {}
        }
    }

    private fun establishTun(): ParcelFileDescriptor? {
        return try {
            val ipv6Enabled = AppSettings.ipv6Enabled(this)
            val builder = Builder()
                .setSession("CDN Hunter VPN")
                .addAddress("10.10.10.10", 32)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
            // IPv6 address + route only added when the user has IPv6 enabled
            // in Settings — matches the mihomo "ipv6" config flag set in
            // VpnConfigBuilder so both sides agree on whether v6 traffic is
            // routed through the tunnel at all.
            if (ipv6Enabled) {
                builder.addAddress("fd00:1:1:1::1", 128)
            }
            builder
                // MTU is now user-adjustable via Settings UI (AppSettings.mtu()).
                // Default is 1500 (standard Ethernet) — works on all mobile/ISP
                // paths. Users who know their network supports jumbo frames can
                // raise it up to 9000 via Custom in Settings.
                // NOTE: must stay in sync with "mtu" in VpnConfigBuilder's mihomo
                // tun config — both now read from AppSettings.mtu().
                .setMtu(AppSettings.mtu(this))
                .setBlocking(false)
                .addRoute("0.0.0.0", 1)
                .addRoute("128.0.0.0", 1)
            if (ipv6Enabled) {
                builder.addRoute("::", 0)
            }

            // Split tunneling. Android only allows EITHER
            // addAllowedApplication calls OR addDisallowedApplication calls
            // on a single Builder, never a mix of both -- it throws
            // UnsupportedOperationException if you try. So the two modes
            // have to be mutually exclusive branches, not just "add this
            // app to whichever list."
            val splitApps = AppSettings.splitTunnelApps(this)
            if (AppSettings.splitTunnelMode(this) == "include" && splitApps.isNotEmpty()) {
                // Only the selected apps use the VPN; everything else
                // (including this app itself, deliberately left out) goes
                // direct.
                for (pkg in splitApps) {
                    try { builder.addAllowedApplication(pkg) } catch (_: Exception) {
                        // App was uninstalled since being added to the list, or
                        // some other lookup failure -- skip it rather than
                        // aborting the whole VPN setup over one stale entry.
                    }
                }
            } else {
                // Default / "exclude" mode: everything uses the VPN except
                // this app itself (required -- otherwise its own traffic to
                // the proxy server would loop back into its own tunnel) plus
                // whatever the user explicitly excluded.
                builder.addDisallowedApplication(packageName)
                for (pkg in splitApps) {
                    try { builder.addDisallowedApplication(pkg) } catch (_: Exception) {
                        // Same as above -- stale/uninstalled package, skip it.
                    }
                }
            }

            // Allow LAN: user preference to keep local network traffic accessible
            // Note: Android's VPN API handles this automatically by default - 
            // private networks (192.168.0.0/16, 10.0.0.0/8, 172.16.0.0/12) are 
            // NOT routed through the VPN unless explicitly added with addRoute().
            // So "Allow LAN" is effectively always on unless we block it, which we don't.
            // The AppSettings toggle is kept for future use or UI indication.

            builder.establish()
        } catch (e: Exception) {
            lastError = "TUN: ${e.message}"
            null
        }
    }

    /** Returns the configured Private DNS hostname if Android's system-wide
     *  Private DNS is set to strict/hostname mode, or null if it's off/opportunistic. */
    private fun checkPrivateDnsStrictMode(): String? {
        return try {
            val mode = android.provider.Settings.Global.getString(contentResolver, "private_dns_mode")
            if (mode == "hostname") {
                android.provider.Settings.Global.getString(contentResolver, "private_dns_specifier")
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "VPN", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CDN Hunter VPN")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: String) {
        try {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(status))
        } catch (_: Exception) {}
    }

    // onDestroy/onRevoke run synchronously and unconditionally: unlike the
    // user-initiated stop button (where blocking the main thread on
    // MihomoBridge.stop()'s JNI call would freeze the UI), here the process
    // is already being torn down by the OS, so blocking briefly to actually
    // finish mihomo's shutdown and release the tun fd is correct — cancelling
    // `scope` first would abandon that teardown mid-flight and leak the fd.
    override fun onDestroy() {
        job?.cancel()
        kotlinx.coroutines.runBlocking { stopVpnInternal(keepTunAlive = false) }
        scope.cancel()
        networkCallback?.let { try { connectivityManager?.unregisterNetworkCallback(it) } catch (_: Exception) {} }
        instance = null
        super.onDestroy()
    }
    override fun onRevoke() { job?.cancel(); kotlinx.coroutines.runBlocking { stopVpnInternal(keepTunAlive = false) }; super.onRevoke() }
}
