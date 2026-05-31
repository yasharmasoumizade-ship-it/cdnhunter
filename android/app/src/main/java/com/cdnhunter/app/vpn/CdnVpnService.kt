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

/**
 * VPN Service: runs xray-core as SOCKS proxy on 127.0.0.1:10808
 * and establishes a TUN interface that routes traffic through it.
 *
 * Note: Without tun2socks native lib, we use xray's built-in tun support
 * by passing the TUN file descriptor to xray-core.
 */
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

    private var tunFd: ParcelFileDescriptor? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
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
                // 1. Build xray config
                val config = VpnConfigBuilder.buildConfig(this@CdnVpnService)
                val configFile = File(filesDir, "xray_config.json")
                configFile.writeText(config)

                // 2. Establish TUN interface
                val tun = establishTun()
                if (tun == null) {
                    lastError = "Failed to establish VPN tunnel"
                    withContext(Dispatchers.Main) { stopVpn() }
                    return@launch
                }
                tunFd = tun

                // 3. Init and start xray with TUN fd
                XrayBridge.init(filesDir.absolutePath)
                XrayBridge.start(config, tun.fd)

                isRunning.set(true)
                uploadBytes = 0L
                downloadBytes = 0L
                updateNotification("Connected")

                // 4. Monitor traffic
                while (isActive && isRunning.get()) {
                    uploadBytes += XrayBridge.queryUpload()
                    downloadBytes += XrayBridge.queryDownload()
                    delay(1000)
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Unknown error"
                updateNotification("Error: ${lastError.take(30)}")
                delay(2000)
                withContext(Dispatchers.Main) { stopVpn() }
            }
        }
    }

    private fun stopVpn() {
        isRunning.set(false)
        job?.cancel()
        XrayBridge.stop()
        tunFd?.close()
        tunFd = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun establishTun(): ParcelFileDescriptor? {
        return try {
            Builder()
                .setSession("CDN Hunter VPN")
                .addAddress("10.10.10.1", 30)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .setMtu(1500)
                .addDisallowedApplication(packageName)
                .setBlocking(false)
                .establish()
        } catch (e: Exception) {
            lastError = "TUN error: ${e.message}"
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "VPN Service", NotificationManager.IMPORTANCE_LOW)
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
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification(status))
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        stopVpn()
        scope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }
}
