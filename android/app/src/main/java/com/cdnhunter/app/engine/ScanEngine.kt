package com.cdnhunter.app.engine

import com.cdnhunter.app.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.*
import kotlin.math.min
import kotlin.random.Random

class ScanEngine {

    @Volatile private var stopRequested = false
    private var currentClient: OkHttpClient? = null

    var onProgress: ((scanned: Int, healthy: Int, total: Int) -> Unit)? = null
    var onLiveResult: ((ScanResult) -> Unit)? = null
    var onLog: ((String) -> Unit)? = null

    fun requestStop() {
        stopRequested = true
        currentClient?.dispatcher?.cancelAll()
    }

    suspend fun scan(config: ScanConfig): List<ScanResult> = withContext(Dispatchers.IO) {
        stopRequested = false
        val ips = expandIps(config)
        if (ips.isEmpty()) { onLog?.invoke("No IPs to scan"); return@withContext emptyList() }
        onLog?.invoke("Scanning ${ips.size} IPs (conc=${config.concurrency}, t=${config.timeout}s)")

        // Build client with config-based timeouts
        currentClient = buildClient(config)
        scanIps(ips, config)
    }

    fun expandIps(config: ScanConfig): List<String> = when (config.cdnProvider) {
        CdnProvider.MANUAL -> parseManualIps(config.manualIps)
        CdnProvider.CIDR -> expandCidrs(parseCidrs(config.manualCidr), config.maxIps)
        CdnProvider.SMART -> expandSmartScan(config.maxIps)
        CdnProvider.ALL -> expandAllCdns(config.maxIps)
        else -> {
            val key = config.cdnProvider.label
            val cidrs = CdnRanges.ranges[key] ?: return emptyList()
            expandCidrs(cidrs.shuffled().take(min(10, cidrs.size)), config.maxIps)
        }
    }

    private fun expandSmartScan(maxIps: Int): List<String> {
        val allCidrs = CdnRanges.ranges.values.flatMap { it.shuffled().take(5) }
        return expandCidrs(allCidrs.shuffled(), maxIps)
    }

    private fun expandAllCdns(maxIps: Int): List<String> {
        val perCdn = maxIps / CdnRanges.ranges.size
        return CdnRanges.ranges.values.flatMap { cidrs ->
            expandCidrs(cidrs.shuffled().take(5), perCdn)
        }.shuffled().take(maxIps)
    }

    /**
     * High-performance scan using chunked batches + semaphore.
     * UI updates are throttled to every 50 IPs to prevent lag.
     */
    private suspend fun scanIps(ips: List<String>, config: ScanConfig): List<ScanResult> {
        val healthy = mutableListOf<ScanResult>()
        val semaphore = Semaphore(config.concurrency)
        val total = ips.size
        val scannedCount = AtomicInteger(0)
        val healthyCount = AtomicInteger(0)
        val lock = Any()

        // Throttle UI updates: batch every N results
        val updateInterval = maxOf(1, total / 100) // ~100 UI updates total

        coroutineScope {
            ips.map { ip ->
                launch(Dispatchers.IO) {
                    if (stopRequested) return@launch
                    semaphore.acquire()
                    try {
                        if (stopRequested) return@launch
                        val result = checkIp(ip, config)
                        val sc = scannedCount.incrementAndGet()

                        if (result.ok) {
                            synchronized(lock) { healthy.add(result) }
                            healthyCount.incrementAndGet()
                            // Only emit live result for healthy IPs (less UI pressure)
                            onLiveResult?.invoke(result)
                        }

                        // Throttled progress update
                        if (sc % updateInterval == 0 || sc == total) {
                            onProgress?.invoke(sc, healthyCount.get(), total)
                        }
                    } finally {
                        semaphore.release()
                    }
                }
            }.joinAll()
        }

        // Final progress
        onProgress?.invoke(total, healthyCount.get(), total)
        return healthy.sortedBy { it.ms }
    }

    private fun checkIp(ip: String, config: ScanConfig): ScanResult {
        val client = currentClient ?: return ScanResult(ip = ip, ok = false)
        var lastCode = 0
        var bestMs = 9999

        repeat(config.retries) { attempt ->
            if (stopRequested) return ScanResult(ip = ip, ok = false, ms = bestMs, code = lastCode)
            val t0 = System.currentTimeMillis()
            try {
                val req = Request.Builder()
                    .url("https://$ip:443/")
                    .header("User-Agent", "curl/7.88")
                    .header("Connection", "close")
                    .apply { if (config.host.isNotBlank()) header("Host", config.host) }
                    .build()

                client.newCall(req).execute().use { response ->
                    val ms = (System.currentTimeMillis() - t0).toInt()
                    bestMs = min(bestMs, ms)
                    lastCode = response.code
                    if (response.code < 500) {
                        return ScanResult(ip = ip, ok = true, code = response.code, ms = ms)
                    }
                }
            } catch (e: Exception) {
                bestMs = min(bestMs, (System.currentTimeMillis() - t0).toInt())
            }
            if (attempt < config.retries - 1) Thread.sleep(20)
        }
        return ScanResult(ip = ip, ok = false, ms = bestMs, code = lastCode)
    }

    // ── IP expansion ────────────────────────────────────────────────────────

    fun expandCidrs(cidrs: List<String>, maxIps: Int): List<String> {
        val ips = mutableSetOf<String>()
        for (cidr in cidrs) {
            if (ips.size >= maxIps) break
            val parts = cidr.split("/")
            if (parts.size != 2) continue
            val baseIp = CdnRanges.ipToLong(parts[0]) ?: continue
            val prefix = parts[1].toIntOrNull() ?: continue
            if (prefix < 8 || prefix > 32) continue
            val size = 1L shl (32 - prefix)

            if (size <= 256) {
                for (i in 1 until size - 1) {
                    if (ips.size >= maxIps) break
                    ips.add(CdnRanges.longToIp(baseIp + i))
                }
            } else {
                val count = min(maxIps - ips.size, min(size.toInt() - 2, 300))
                repeat(count) {
                    ips.add(CdnRanges.longToIp(baseIp + Random.nextLong(1, size - 1)))
                }
            }
        }
        return ips.toList().shuffled()
    }

    fun parseManualIps(text: String): List<String> =
        text.split(Regex("[\\s,;\\n]+")).map { it.trim() }
            .filter { it.matches(Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) }.distinct()

    fun parseCidrs(text: String): List<String> =
        text.split(Regex("[\\s,;\\n]+")).map { it.trim() }
            .filter { it.matches(Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}/\\d{1,2}")) }.distinct()

    // ── OkHttp client ───────────────────────────────────────────────────────

    private fun buildClient(config: ScanConfig): OkHttpClient {
        val connectMs = (config.timeout * 650).toLong()  // 65% of timeout for connect
        val readMs = (config.timeout * 1000).toLong()

        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(c: Array<out X509Certificate>?, a: String?) {}
            override fun checkServerTrusted(c: Array<out X509Certificate>?, a: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val ssl = SSLContext.getInstance("TLS").apply { init(null, trustAll, SecureRandom()) }

        val dispatcher = Dispatcher().apply {
            maxRequests = config.concurrency + 50
            maxRequestsPerHost = config.concurrency
        }

        return OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trustAll[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(connectMs, TimeUnit.MILLISECONDS)
            .readTimeout(readMs, TimeUnit.MILLISECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(config.concurrency, 20, TimeUnit.SECONDS))
            .dispatcher(dispatcher)
            .retryOnConnectionFailure(false)
            .build()
    }
}
