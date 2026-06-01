package com.cdnhunter.app.vpn

import android.content.Context
import android.util.Log
import com.cdnhunter.app.data.*
import com.cdnhunter.app.engine.ScanEngine
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Auto-IP Manager: scans for clean IPs, picks the best, monitors speed,
 * and seamlessly switches to a new IP if the current one degrades.
 *
 * Flow:
 * 1. Scan 10 clean IPs (fast scan)
 * 2. Pick the lowest-latency one, apply it to the VPN config
 * 3. Monitor download speed every 5s
 * 4. If speed drops below threshold for 3 consecutive checks:
 *    a. Try next IP from the pool
 *    b. If all IPs exhausted, re-scan
 * 5. IP switch is seamless: restart xray with new config (hev-socks5-tunnel stays running)
 */
object AutoIpManager {

    private const val TAG = "AutoIP"
    private const val POOL_SIZE = 10
    private const val CHECK_INTERVAL_MS = 5000L
    private const val SLOW_THRESHOLD_BYTES = 1024L // 1 KB/s minimum
    private const val MAX_SLOW_STRIKES = 3

    var enabled = AtomicBoolean(false)
    var currentIp = ""
    var status = "Idle"
    var ipPool = listOf<String>()

    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(context: Context) {
        if (enabled.get()) return
        enabled.set(true)
        status = "Scanning for clean IPs..."
        Log.i(TAG, "Auto-IP started")

        monitorJob = scope.launch {
            try {
                // 1. Scan for clean IPs
                val pool = scanForPool(context)
                if (pool.isEmpty()) {
                    status = "No clean IPs found"
                    enabled.set(false)
                    return@launch
                }
                ipPool = pool
                status = "Found ${pool.size} clean IPs"
                Log.i(TAG, "Pool: $pool")

                // 2. Apply best IP
                var poolIndex = 0
                applyIp(context, pool[poolIndex])

                // 3. Monitor loop
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
                        Log.d(TAG, "Slow: ${bytesPerSec}B/s (strike $slowStrikes/$MAX_SLOW_STRIKES)")
                    } else {
                        slowStrikes = 0
                    }

                    if (slowStrikes >= MAX_SLOW_STRIKES) {
                        slowStrikes = 0
                        poolIndex++

                        if (poolIndex >= pool.size) {
                            // All IPs exhausted, re-scan
                            status = "All IPs slow, re-scanning..."
                            Log.i(TAG, "Pool exhausted, re-scanning")
                            val newPool = scanForPool(context)
                            if (newPool.isEmpty()) {
                                status = "Re-scan failed"
                                break
                            }
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
        val engine = ScanEngine()
        val scanConfig = ScanConfig(
            cdnProvider = CdnProvider.CLOUDFLARE, // Auto-IP only scans Cloudflare
            maxIps = 200,
            concurrency = 80,
            timeout = 3f,
            retries = 1
        )
        val results = engine.scan(scanConfig)
        return results.filter { it.ok }.sortedBy { it.ms }.take(POOL_SIZE).map { it.ip }
    }

    /**
     * Applies a new IP to the VPN config and restarts xray seamlessly.
     * The TUN + tun2socks stay running — only xray restarts, so the switch
     * is nearly invisible to the user (< 1 second gap).
     */
    private suspend fun applyIp(context: Context, ip: String) {
        currentIp = ip
        status = "Using: $ip"
        Log.i(TAG, "Switching to IP: $ip")

        val prefs = context.getSharedPreferences("cdnhunter_vpn", Context.MODE_PRIVATE)
        val originalUri = prefs.getString("user_config", "") ?: ""
        if (originalUri.isBlank()) return

        // Replace the address in the URI with our clean IP
        val newUri = replaceIpInUri(originalUri, ip)
        prefs.edit().putString("user_config_active", newUri).apply()

        // Restart xray with the new config (seamless — TUN stays up)
        withContext(Dispatchers.IO) {
            try {
                XrayBridge.stop()
                delay(200)
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
        // URI format: protocol://user@ADDRESS:PORT?params#remark
        // Replace the address between @ and : (or ? if no port explicit)
        return try {
            val atIdx = uri.indexOf('@')
            if (atIdx < 0) return uri
            val afterAt = uri.substring(atIdx + 1)
            // Find end of address (could be : for port, or ? for params, or # for remark)
            val endIdx = afterAt.indexOfFirst { it == ':' || it == '?' || it == '#' }
            if (endIdx < 0) return uri
            uri.substring(0, atIdx + 1) + newIp + afterAt.substring(endIdx)
        } catch (_: Exception) { uri }
    }
}
