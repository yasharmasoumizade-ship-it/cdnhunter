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
import com.cdnhunter.app.R
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * VPN Service that runs xray-core as a local SOCKS proxy
 * and routes traffic through a TUN interface.
 *
 * This is the bridge between Android VPN and xray-core library.
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
    private var xrayJob: Job? = null
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

        xrayJob = scope.launch {
            try {
                // 1. Write xray config
                val configFile = File(filesDir, "xray_config.json")
                val config = VpnConfigBuilder.buildConfig(this@CdnVpnService)
                configFile.writeText(config)

                // 2. Start xray as SOCKS proxy
                XrayBridge.init(filesDir.absolutePath)
                XrayBridge.start(config)

                // 3. Establish TUN
                val tun = establishTun() ?: run {
                    stopVpn(); return@launch
                }
                tunFd = tun

                // 4. Start tun2socks (route TUN → SOCKS)
                Tun2SocksBridge.start(tun.fd, "127.0.0.1", 10808)

                isRunning.set(true)
                uploadBytes = 0; downloadBytes = 0
                updateNotification("Connected")

                // 5. Monitor traffic stats
                while (isActive && isRunning.get()) {
                    uploadBytes += XrayBridge.queryUpload()
                    downloadBytes += XrayBridge.queryDownload()
                    delay(1000)
                }
            } catch (e: Exception) {
                updateNotification("Error: ${e.message?.take(30)}")
                delay(2000)
                stopVpn()
            }
        }
    }

    private fun stopVpn() {
        isRunning.set(false)
        xrayJob?.cancel()
        Tun2SocksBridge.stop()
        XrayBridge.stop()
        tunFd?.close(); tunFd = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun establishTun(): ParcelFileDescriptor? {
        return Builder()
            .setSession("CDN Hunter")
            .addAddress("10.10.10.1", 30)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .setMtu(1500)
            .setBlocking(false)
            .establish()
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
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(status))
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
