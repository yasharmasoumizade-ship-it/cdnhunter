package com.cdnhunter.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
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

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
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
        getSharedPreferences("cdnhunter_vpn", MODE_PRIVATE).getBoolean("kill_switch_enabled", false)

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
                            stopVpnInternal()
                            return@launch
                        }
                        debugLog += "\nmihomo restarted OK with support-x25519mlkem768"
                    }
                }

                isRunning.set(true)
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
                lastError = e.message ?: "Unknown error"
                debugLog += "\nEXCEPTION: ${e.message}\n${android.util.Log.getStackTraceString(e)}"
                debugLog = debugLog.takeLast(8000)
                val holdKillSwitch = isRunning.get() && isKillSwitchEnabled()
                isRunning.set(false)
                if (holdKillSwitch) {
                    killSwitchBlocking.set(true)
                    updateNotification("Blocked - connection lost (Kill Switch on)")
                    debugLog += "\nKill switch: holding TUN up with traffic blocked after unexpected disconnect."
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

    private fun stopVpn() {
        job?.cancel()
        stopVpnInternal(keepTunAlive = false)
    }

    // Writes the tunnel-verified (accurate) country/city for one saved config
    // straight into the same SharedPreferences record AppScreen's
    // loadConfigs/saveConfigs read and write (key "saved_configs", one line
    // per config: "uri\u0001countryCode\u0001city\u0001pingMs\u0001geoResolved\u0001accurateGeoResolved").
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
                val pingMs = parts.getOrNull(3) ?: "-1"
                listOf(uri, cc, city, pingMs, "1", "1").joinToString(sep)
            }
            if (changed) {
                prefs.edit().putString("saved_configs", updated.joinToString("\n")).apply()
            }
        } catch (e: Exception) {
            android.util.Log.e("CdnVpnService", "persistAccurateGeo failed: ${e.message}")
        }
    }

    // Actual teardown, shared by the external-stop path (stopVpn) and the
    // internal error-recovery path in startVpn's catch block, which must not
    // cancel `job` since it IS the job currently running this code.
    private fun stopVpnInternal(keepTunAlive: Boolean) {
        isRunning.set(false)
        MihomoBridge.stop()
        exitCountryCode = ""
        exitCity = ""
        exitGeoConfigId = ""

        if (keepTunAlive) {
            return
        }
        // MihomoBridge.stop() already closes the tun fd internally (via
        // executor.Shutdown -> listener.Cleanup). We must NOT also close it
        // here on the Kotlin side -- that would double-close the same fd
        // number, which on Linux/Android can immediately reassign that same
        // integer to a totally unrelated file/socket and corrupt native
        // state. This was the actual cause of the app being killed right
        // after pressing disconnect.
        tunRawFd = null
        tunFd = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun establishTun(): ParcelFileDescriptor? {
        return try {
            val builder = Builder()
                .setSession("CDN Hunter VPN")
                .addAddress("10.10.10.10", 32)
                .addAddress("fd00:1:1:1::1", 128)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                // Set to 9000 per explicit request (previously 1500). Heads up:
                // 9000 (jumbo-frame territory) assumes every hop between here and
                // the real destination also supports frames that large — mobile
                // data and most Wi-Fi/ISP paths cap out at standard Ethernet 1500,
                // so packets mihomo builds for a 9000-byte interface can get
                // silently dropped once they reach the real physical interface —
                // this was the previous reason for lowering it to 1500. Revert
                // this back to 1500 if connections stop working again.
                // NOTE: must stay in sync with "mtu" in VpnConfigBuilder's mihomo
                // tun config — a mismatch there causes the same silent-drop issue.
                .setMtu(9000)
                .addDisallowedApplication(packageName)
                .setBlocking(false)
                .addRoute("0.0.0.0", 1)
                .addRoute("128.0.0.0", 1)
                .addRoute("::", 0)

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
    override fun onDestroy() { job?.cancel(); stopVpnInternal(keepTunAlive = false); scope.cancel(); instance = null; super.onDestroy() }
    override fun onRevoke() { job?.cancel(); stopVpnInternal(keepTunAlive = false); super.onRevoke() }
}
