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
            ACTION_STOP -> scope.launch { stopVpn() }
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning.get()) return
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
                protect(tun.fd)

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

                val config = VpnConfigBuilder.buildConfig(this@CdnVpnService, tun.fd)

                debugLog = "── connect attempt @ ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())} ──\n" +
                    "config length: ${config.length} chars\n" +
                    "tun fd: ${tun.fd}\n" +
                    "config head:\n${config.take(600)}\n"

                android.util.Log.i("CdnVpn", "Config length: ${config.length}")
                android.util.Log.i("CdnVpn", "Config first 200: ${config.take(200)}")
                android.util.Log.d("CdnVpn", "Full mihomo config: $config")

                val started = MihomoBridge.start(config, mihomoHomeDir.absolutePath)
                if (!started) {
                    lastError = "mihomo failed to start: ${MihomoBridge.lastError}"
                    debugLog += "\nFAILED: mihomo rejected the config.\nmihomo error:\n${MihomoBridge.lastError}"
                    stopVpnInternal()
                    return@launch
                }

                debugLog += "\nmihomo started OK"
                isRunning.set(true)
                uploadBytes = 0L
                downloadBytes = 0L
                updateNotification("Connected")

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
                updateNotification("Error: ${lastError.take(30)}")
                delay(2000)
                // Already running on the IO scope's job — no need to hop to Main
                // (that would re-block the UI thread on MihomoBridge.stop()'s JNI
                // call) or to cancel `job`, since this coroutine IS `job` and is
                // already on its way out via this catch block.
                stopVpnInternal()
            }
        }
    }

    private fun stopVpn() {
        job?.cancel()
        stopVpnInternal()
    }

    // Actual teardown, shared by the external-stop path (stopVpn) and the
    // internal error-recovery path in startVpn's catch block, which must not
    // cancel `job` since it IS the job currently running this code.
    private fun stopVpnInternal() {
        isRunning.set(false)
        MihomoBridge.stop()
        tunFd?.close()
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
    override fun onDestroy() { job?.cancel(); stopVpnInternal(); scope.cancel(); instance = null; super.onDestroy() }
    override fun onRevoke() { job?.cancel(); stopVpnInternal(); super.onRevoke() }
}
