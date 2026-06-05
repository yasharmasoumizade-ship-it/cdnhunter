package com.cdnhunter.app.vpn

import android.content.Context
import android.util.Log
import com.cdnhunter.app.data.*
import com.cdnhunter.app.engine.ScanEngine
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

object AutoIpManager {

    private const val TAG = "AutoIP"
    private const val POOL_SIZE = 10
    private const val CHECK_INTERVAL_MS = 15000L
    private const val SLOW_THRESHOLD_BYTES = 10240L
    private const val MAX_SLOW_STRIKES = 2

    var enabled = AtomicBoolean(false)
    var currentIp = ""
    var status = "Idle"
    var ipPool = mutableListOf<String>()

    // Set this from ViewModel before calling start()
    var scanResultProvider: (suspend () -> List<ScanResult>)? = null

    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(context: Context) {
        if (enabled.get()) return
        enabled.set(true)
        status = "Scanning for clean IPs..."
        Log.i(TAG, "Auto-IP started")

        monitorJob = scope.launch {
            try {
                val pool = if (ipPool.isNotEmpty()) {
                    status = "Using ${ipPool.size} provided IPs"
                    ipPool
                } else {
                    val scanned = scanForPool(context)
                    if (scanned.isEmpty()) {
                        status = "No clean IPs found"
                        enabled.set(false)
                        return@launch
                    }
                    ipPool = scanned
                    status = "Found ${scanned.size} IPs"
                    scanned
                }

                var poolIndex = 0
                applyIp(context, pool[poolIndex])

                var slowStrikes = 0
                var lastDownload = CdnVpnService.downloadBytes

                while (isActive && enabled.get() && CdnVpnService.isRunning.get()) {
                    delay(CHECK_INTERVAL_MS)
                    if (!CdnVpnService.isRunning.get()) break

                    val currentDown = CdnVpnService.downloadBytes
                    val bytesPerSec = (currentDown - lastDownload) * 1000 / CHECK_INTERVAL_MS
                    lastDownload = currentDown

                    if (bytesPerSec < SLOW_THRESHOLD_BYTES && currentDown > 0) {
                        slowStrikes++
                    } else {
                        slowStrikes = 0
                    }

                    if (slowStrikes >= MAX_SLOW_STRIKES) {
                        slowStrikes = 0
                        poolIndex++
                        if (poolIndex >= ipPool.size) {
                            status = "Re-scanning..."
                            val newPool = scanForPool(context)
                            if (newPool.isEmpty()) { status = "Re-scan failed"; break }
                            ipPool = newPool
                            poolIndex = 0
                        }
                        applyIp(context, ipPool[poolIndex])
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}")
                status = "Error: ${e.message?.take(30)}"
            }
        }
    }

    fun stop() {
        enabled.set(false)
        monitorJob?.cancel()
        monitorJob = null
        status = "Stopped"
        currentIp = ""
    }

    private suspend fun scanForPool(context: Context): List<String> {
        // Use ViewModel's scan results if available (same scan as app)
        val provider = scanResultProvider
        if (provider != null) {
            status = "Using app scanner..."
            val results = provider()
            val best = results.filter { it.ok && it.ms < 500 }
                .sortedBy { it.ms }
                .take(POOL_SIZE)
                .map { it.ip }
            if (best.isNotEmpty()) return best
        }

        // Fallback: run own scan with same config as app
        status = "Running quick scan..."
        val engine = ScanEngine()
        val prefs = context.getSharedPreferences("cdnhunter_scan", 0)
        val savedProvider = prefs.getString("cdn_provider", "smart") ?: "smart"
        val provider2 = CdnProvider.entries.find { it.key == savedProvider } ?: CdnProvider.SMART
        val scanConfig = ScanConfig(
            cdnProvider = provider2,
            maxIps = 300,
            concurrency = 80,
            timeout = 3f,
            retries = 1
        )
        val results = engine.scan(scanConfig)
        return results.filter { it.ok && it.ms < 400 }
            .sortedBy { it.ms }
            .take(POOL_SIZE)
            .map { it.ip }
    }

    private suspend fun applyIp(context: Context, ip: String) {
        if (!CdnVpnService.isRunning.get()) return
        currentIp = ip
        status = "Using: $ip"
        Log.i(TAG, "Switching to IP: $ip")

        val prefs = context.getSharedPreferences("cdnhunter_vpn", Context.MODE_PRIVATE)
        val originalUri = prefs.getString("user_config", "") ?: ""
        if (originalUri.isBlank()) return

        val newUri = replaceIpInUri(originalUri, ip)
        prefs.edit().putString("user_config_active", newUri).apply()

        withContext(Dispatchers.IO) {
            try {
                XrayBridge.stop()
                delay(300)
                val config = VpnConfigBuilder.buildConfigFromActiveUri(context)
                XrayBridge.init(context.filesDir.absolutePath)
                XrayBridge.start(config, 0)
                Log.i(TAG, "Xray restarted with IP $ip")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart xray: ${e.message}")
            }
        }
    }

    private fun replaceIpInUri(uri: String, newIp: String): String {
        return try {
            val atIdx = uri.indexOf('@')
            if (atIdx < 0) return uri
            val afterAt = uri.substring(atIdx + 1)
            val endIdx = afterAt.indexOfFirst { it == ':' || it == '?' || it == '#' }
            if (endIdx < 0) return uri
            uri.substring(0, atIdx + 1) + newIp + afterAt.substring(endIdx)
        } catch (_: Exception) { uri }
    }
}
