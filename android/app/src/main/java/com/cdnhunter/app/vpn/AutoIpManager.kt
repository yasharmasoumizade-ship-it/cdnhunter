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
    private const val CHECK_INTERVAL_MS = 10000L
    private const val SLOW_THRESHOLD_BYTES = 5120L
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
                    ipPool = scanned.toMutableList()
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
                    val hadTraffic = (currentDown - lastDownload) > 0
                    lastDownload = currentDown

                    if (hadTraffic && bytesPerSec < SLOW_THRESHOLD_BYTES) {
                        slowStrikes++
                        Log.d(TAG, "Slow: ${bytesPerSec}B/s strike=$slowStrikes")
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
                            ipPool = newPool.toMutableList()
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
            cdnProvider = CdnProvider.CLOUDFLARE,
            maxIps = 500,
            concurrency = 60,
            timeout = 5f,
            retries = 1
        )
        val results = engine.scan(scanConfig)
        val candidates = results.filter { it.ok && it.ms < 800 }.sortedBy { it.ms }.take(30)
        
        // Verify with fronting check against Cloudflare-backed domains
        val verified = mutableListOf<String>()
        val checkDomains = listOf("chatgpt.com", "hcaptcha.com", "cdn.openai.com")
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .hostnameVerifier { _, _ -> true }
            .build()
        
        for (result in candidates) {
            if (verified.size >= POOL_SIZE) break
            try {
                val domain = checkDomains.random()
                val req = okhttp3.Request.Builder()
                    .url("https://${result.ip}/")
                    .header("Host", domain)
                    .header("User-Agent", "curl/7.88")
                    .build()
                val resp = client.newCall(req).execute()
                resp.close()
                if (resp.code < 530) {
                    verified.add(result.ip)
                    Log.i(TAG, "Verified IP: ${result.ip} via $domain (${resp.code})")
                }
            } catch (e: Exception) {
                Log.d(TAG, "IP ${result.ip} failed fronting: ${e.message?.take(30)}")
            }
        }
        
        return if (verified.isNotEmpty()) verified else candidates.take(POOL_SIZE).map { it.ip }
    }

    private suspend fun applyIp(context: Context, ip: String) {
        if (!CdnVpnService.isRunning.get()) return
        currentIp = ip
        status = "Applying: $ip"
        Log.i(TAG, "Switching to IP: $ip")
        withContext(Dispatchers.Main) {
            android.widget.Toast.makeText(context, "Auto-IP: switching to $ip", android.widget.Toast.LENGTH_SHORT).show()
        }

        val prefs = context.getSharedPreferences("cdnhunter_vpn", Context.MODE_PRIVATE)
        val originalUri = prefs.getString("user_config", "") ?: ""
        if (originalUri.isBlank()) { Log.e(TAG, "No user_config"); return }

        val newUri = replaceIpInUri(originalUri, ip)
        Log.i(TAG, "New URI: $newUri")
        prefs.edit()
            .putString("user_config_active", newUri)
            .putString("user_config", newUri)
            .apply()

        withContext(Dispatchers.IO) {
            try {
                val config = VpnConfigBuilder.buildConfig(context)
                XrayBridge.stop()
                delay(500)
                XrayBridge.init(context.filesDir.absolutePath)
                XrayBridge.start(config, 0)
                status = "Using: $ip"
                Log.i(TAG, "Xray restarted with $ip")
            } catch (e: Exception) {
                Log.e(TAG, "Failed: ${e.message}")
                status = "Error: ${e.message?.take(30)}"
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
