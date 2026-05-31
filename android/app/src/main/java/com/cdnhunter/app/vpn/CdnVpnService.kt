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
 * VPN Service architecture:
 *   Android TUN <-> hev-socks5-tunnel (.so) <-> xray SOCKS5 (127.0.0.1:10808) <-> Server
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
                // 1. Build xray config (SOCKS inbound on 10808)
                val config = VpnConfigBuilder.buildConfig(this@CdnVpnService)
                val configFile = File(filesDir, "xray_config.json")
                configFile.writeText(config)

                // 2. Init and start xray as SOCKS proxy (no TUN fd needed here)
                XrayBridge.init(filesDir.absolutePath)
                XrayBridge.start(config, 0)  // 0 = no TUN, just SOCKS proxy

                // 3. Small delay for xray to bind port
                delay(500)

                // 4. Establish TUN interface
                val tun = establishTun()
                if (tun == null) {
                    lastError = "Failed to create VPN tunnel"
                    XrayBridge.stop()
                    withContext(Dispatchers.Main) { stopSelf() }
                    return@launch
                }
                tunFd = tun

                // 5. Start tun2socks: route TUN traffic -> SOCKS proxy
                val tun2socksConfig = File(filesDir, "tun2socks.yml")
                Tun2SocksBridge.start(tun.fd, "127.0.0.1", 10808, tun2socksConfig)

                isRunning.set(true)
                uploadBytes = 0L
                downloadBytes = 0L
                updateNotification("Connected")

                // 6. Monitor traffic stats
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
        Tun2SocksBridge.stop()
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
                .setMtu(8500)
                .addDisallowedApplication(packageName)
                .setBlocking(false)
                .establish()
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

    override fun onDestroy() { stopVpn(); scope.cancel(); super.onDestroy() }
    override fun onRevoke() { stopVpn(); super.onRevoke() }
}
