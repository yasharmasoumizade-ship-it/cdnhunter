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
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning.get()) return
        startForeground(NOTIFICATION_ID, buildNotification("Connecting..."))
        lastError = ""

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

                val config = VpnConfigBuilder.buildConfig(this@CdnVpnService)

                debugLog = "── connect attempt @ ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())} ──\n" +
                    "config length: ${config.length} chars\n" +
                    "config head:\n${config.take(600)}\n"

                android.util.Log.i("CdnVpn", "Config length: ${config.length}")
                android.util.Log.i("CdnVpn", "Config first 200: ${config.take(200)}")
                android.util.Log.d("CdnVpn", "Full mihomo config: $config")

                val tun = establishTun()
                if (tun == null) {
                    lastError = "Failed to create VPN tunnel"
                    debugLog += "\nFAILED: could not establish TUN interface (lastError set above)"
                    withContext(Dispatchers.Main) { stopSelf() }
                    return@launch
                }
                tunFd = tun

                protect(tun.fd)

                val started = MihomoBridge.start(config, mihomoHomeDir.absolutePath)
                if (!started) {
                    lastError = "mihomo failed to start: ${MihomoBridge.lastError}"
                    debugLog += "\nFAILED: mihomo rejected the config.\nmihomo error:\n${MihomoBridge.lastError}"
                    withContext(Dispatchers.Main) { stopVpn() }
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
            } catch (e: Exception) {
                lastError = e.message ?: "Unknown error"
                debugLog += "\nEXCEPTION: ${e.message}\n${android.util.Log.getStackTraceString(e)}"
                updateNotification("Error: ${lastError.take(30)}")
                delay(2000)
                withContext(Dispatchers.Main) { stopVpn() }
            }
        }
    }

    private fun stopVpn() {
        isRunning.set(false)
        job?.cancel()
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

    override fun onDestroy() { stopVpn(); scope.cancel(); instance = null; super.onDestroy() }
    override fun onRevoke() { stopVpn(); super.onRevoke() }
}
